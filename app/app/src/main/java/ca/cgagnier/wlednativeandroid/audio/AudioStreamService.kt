package ca.cgagnier.wlednativeandroid.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AudioStreamService"
private val logger = Logger.withTag(TAG)

/** 服务对外暴露的运行状态。UI 与通知都读这一份，不各自维护副本。 */
sealed interface AudioStreamState {
    data object Idle : AudioStreamState

    data class Active(val source: CaptureSource, val deviceUnreachable: Boolean = false) : AudioStreamState

    /** 内录连续静音，等待用户确认（不允许静默切换）。 */
    data class AskingDowngrade(val source: CaptureSource) : AudioStreamState

    data class Failed(
        val failure: Failure,
        val source: CaptureSource? = null,
        val host: String? = null,
    ) : AudioStreamState {
        enum class Failure {
            /** 没有拿到投屏授权（Android 14 每次会话都要重新授权）。 */
            PERMISSION,

            /** AudioRecord/麦克风初始化失败。 */
            CAPTURE_START,

            /** 采集过程中读取失败（投影被系统或用户结束）。 */
            CAPTURE_LOST,
        }
    }
}

/**
 * APP 音频链路的前台服务。
 *
 * - A 内录（`AudioPlaybackCapture`）优先，连续静音后只发通知/弹框询问，不自动切；
 * - C 麦克风由用户二次确认后启动；
 * - `stopWithTask=false` + `onTaskRemoved` 不停止，只有通知里的「停止」或进程结束才终止。
 */
class AudioStreamService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            ACTION_START -> handleStart(intent)
            ACTION_START_MIC -> handleStartMicrophone(intent)
            ACTION_STOP -> stopEverything(userInitiated = true)
            ACTION_SWITCH_TO_MIC -> switchToMicrophone()
            ACTION_DISMISS_ASK -> dismissAsk()
            else -> logger.w { "unknown action: ${intent.action}" }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 划掉最近任务不停止：只有通知的「停止」或进程结束才终止
        logger.i { "task removed, keep capturing" }
    }

    override fun onDestroy() {
        session?.let { stopSession(it) }
        session = null
        if (_state.value !is AudioStreamState.Failed) {
            _state.value = AudioStreamState.Idle
        }
        super.onDestroy()
    }

    // region 启动与切换

    private fun handleStart(intent: Intent) {
        // startForegroundService 必须在 5 秒内进入前台，先起前台再做参数校验
        startForegroundWith(CaptureSource.PLAYBACK)
        val host = intent.getStringExtra(EXTRA_HOST)
        if (host.isNullOrBlank()) {
            logger.w { "start without host" }
            stopEverything(userInitiated = false)
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = resultData(intent)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || resultCode != Activity.RESULT_OK || resultData == null) {
            failAndStop(AudioStreamState.Failed.Failure.PERMISSION, CaptureSource.PLAYBACK, host)
            return
        }
        val projection = try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            manager.getMediaProjection(resultCode, resultData)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.e(e) { "getMediaProjection failed" }
            null
        }
        if (projection == null) {
            failAndStop(AudioStreamState.Failed.Failure.PERMISSION, CaptureSource.PLAYBACK, host)
            return
        }
        val source = PlaybackCaptureSource(projection, onProjectionStopped = ::onProjectionStopped)
        startSession(host, source)
    }

    /** Android 10 以下没有内录，直接授权麦克风使用；其余情况走确认后的降级路径。 */
    private fun handleStartMicrophone(intent: Intent) {
        startForegroundWith(CaptureSource.MICROPHONE)
        val host = intent.getStringExtra(EXTRA_HOST)
        if (host.isNullOrBlank()) {
            stopEverything(userInitiated = false)
            return
        }
        startSession(host, MicrophoneCaptureSource())
    }

    private fun startSession(host: String, source: AudioCaptureSource) {
        session?.let { stopSession(it) }
        session = null
        if (!source.start()) {
            logger.e { "capture source failed to start: ${source.source}" }
            source.stop()
            failAndStop(AudioStreamState.Failed.Failure.CAPTURE_START, source.source, host)
            return
        }
        val newSession = Session(host, source)
        newSession.silenceDetector.reset(SystemClock.elapsedRealtime())
        session = newSession
        newSession.thread = Thread({ captureLoop(newSession) }, "AudioSyncCapture").apply {
            isDaemon = true
            start()
        }
        _state.value = AudioStreamState.Active(source.source)
        updateOngoingNotification(source.source)
    }

    private fun switchToMicrophone() {
        val current = session ?: return
        logger.i { "switching capture to phone microphone" }
        // 确认后询问通知必须立刻消失，否则会一直挂着"是否正在播放"
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ASK)
        stopSession(current)
        session = null
        startForegroundWith(CaptureSource.MICROPHONE)
        startSession(current.host, MicrophoneCaptureSource())
    }

    private fun dismissAsk() {
        val current = session ?: return
        // 「否」只在本次会话生效：不降级，也不再自动询问
        current.asked = true
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ASK)
        if (current.source.source == CaptureSource.PLAYBACK) {
            _state.value = AudioStreamState.Active(CaptureSource.PLAYBACK)
            updateOngoingNotification(CaptureSource.PLAYBACK)
        }
    }

    private fun onProjectionStopped() {
        val current = session ?: return
        if (current.source.source != CaptureSource.PLAYBACK) return
        logger.w { "media projection stopped" }
        failAndStop(AudioStreamState.Failed.Failure.CAPTURE_LOST, current.source.source, current.host)
    }

    // endregion

    // region 采集循环

    private fun captureLoop(session: Session) {
        val analyzer = FftAnalyzer(session.source.sampleRate)
        val buffer = ShortArray(WINDOW_SIZE)
        var readFailures = 0
        var unreachableSinceMs = 0L
        var lastSendMs = 0L
        while (session.running) {
            val read = session.source.readMono(buffer)
            if (read <= 0) {
                if (++readFailures >= MAX_READ_FAILURES) {
                    mainHandler.post {
                        failAndStop(
                            AudioStreamState.Failed.Failure.CAPTURE_LOST,
                            session.source.source,
                            session.host,
                        )
                    }
                    return
                }
                // 读失败时短暂让出 CPU，避免连续空转把一次瞬时错误放大成"采集丢失"
                runCatching { Thread.sleep(READ_RETRY_MS) }
                continue
            }
            readFailures = 0
            val now = SystemClock.elapsedRealtime()
            val frame = analyzer.analyze(buffer, read, now)

            if (now - lastSendMs >= SEND_INTERVAL_MS) {
                lastSendMs = now
                if (!session.sender.send(frame)) {
                    if (unreachableSinceMs == 0L) unreachableSinceMs = now
                    if (now - unreachableSinceMs >= UNREACHABLE_MS) {
                        logger.w { "device unreachable for ${UNREACHABLE_MS}ms" }
                        unreachableSinceMs = now
                        mainHandler.post { markUnreachable(session.source.source) }
                    }
                } else if (unreachableSinceMs != 0L) {
                    unreachableSinceMs = 0L
                    mainHandler.post { markReachable(session.source.source) }
                }
            }

            if (session.source.source == CaptureSource.PLAYBACK &&
                !session.asked &&
                session.silenceDetector.onFrame(frame.sampleRaw, now)
            ) {
                session.asked = true
                mainHandler.post { onSilenceDetected() }
            }
        }
    }

    private fun onSilenceDetected() {
        val current = session ?: return
        if (current.source.source != CaptureSource.PLAYBACK) return
        logger.i { "playback capture silent for 5s, asking user" }
        _state.value = AudioStreamState.AskingDowngrade(CaptureSource.PLAYBACK)
        updateOngoingNotification(CaptureSource.PLAYBACK)
        // 后台不能弹对话框，用高优先级通知询问；点通知进入 APP 完成二次确认
        postAskNotification()
    }

    private fun markUnreachable(source: CaptureSource) {
        if (session?.source?.source != source) return
        if (_state.value is AudioStreamState.Active) {
            _state.value = AudioStreamState.Active(source, deviceUnreachable = true)
        }
    }

    private fun markReachable(source: CaptureSource) {
        if (session?.source?.source != source) return
        if (_state.value is AudioStreamState.Active) {
            _state.value = AudioStreamState.Active(source, deviceUnreachable = false)
        }
    }

    // endregion

    // region 生命周期收尾

    private fun stopSession(session: Session) {
        session.running = false
        runCatching { session.source.stop() }
        session.thread?.interrupt()
        session.sender.close()
    }

    private fun stopEverything(userInitiated: Boolean) {
        session?.let { stopSession(it) }
        session = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ASK)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ONGOING)
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        _state.value = AudioStreamState.Idle
        stopSelf()
        logger.i { "audio stream stopped (userInitiated=$userInitiated)" }
    }

    private fun failAndStop(
        failure: AudioStreamState.Failed.Failure,
        source: CaptureSource? = null,
        host: String? = null,
    ) {
        logger.e { "audio stream failed: $failure (source=$source)" }
        session?.let { stopSession(it) }
        session = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ASK)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ONGOING)
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        _state.value = AudioStreamState.Failed(failure, source, host)
        stopSelf()
    }

    // endregion

    // region 通知

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            getString(R.string.isle_audio_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.isle_audio_notification_channel)
            setShowBadge(false)
        }
        val alert = NotificationChannel(
            CHANNEL_ALERT,
            getString(R.string.isle_audio_ask_title),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.isle_audio_ask_body)
        }
        manager.createNotificationChannels(listOf(ongoing, alert))
    }

    private fun startForegroundWith(source: CaptureSource) {
        val notification = buildOngoingNotification(source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val type = if (source == CaptureSource.PLAYBACK) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ONGOING, notification, type)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ONGOING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ONGOING, notification)
        }
        foregroundStarted = true
    }

    private fun updateOngoingNotification(source: CaptureSource) {
        if (!foregroundStarted) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ONGOING, buildOngoingNotification(source))
    }

    private fun buildOngoingNotification(source: CaptureSource): Notification {
        val statusRes = when {
            source == CaptureSource.MICROPHONE -> R.string.isle_audio_status_mic
            _state.value is AudioStreamState.AskingDowngrade -> R.string.isle_audio_status_asking
            else -> R.string.isle_audio_status_playback
        }
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, AudioStreamService::class.java).apply { action = ACTION_STOP },
            pendingIntentFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setContentTitle(getString(R.string.isle_audio_notification_channel))
            .setContentText(getString(statusRes))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.isle_audio_stop), stopIntent)
            .build()
    }

    private fun buildAskNotification(): Notification {
        // 「正在播放」必须进 APP 做第二次确认：后台直接切麦克风在 Android 14 上会被拒绝
        val yesIntent = PendingIntent.getActivity(
            this,
            REQUEST_ASK_YES,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_SHOW_AUDIO_CONFIRM, true)
            },
            pendingIntentFlags(),
        )
        val noIntent = PendingIntent.getService(
            this,
            REQUEST_ASK_NO,
            Intent(this, AudioStreamService::class.java).apply { action = ACTION_DISMISS_ASK },
            pendingIntentFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setContentTitle(getString(R.string.isle_audio_ask_title))
            .setContentText(getString(R.string.isle_audio_ask_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_QUESTION)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.isle_audio_notification_yes), yesIntent)
            .addAction(0, getString(R.string.isle_audio_notification_no), noIntent)
            .build()
    }

    private fun postAskNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ASK, buildAskNotification())
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_SHOW_AUDIO_CONFIRM, true)
        },
        pendingIntentFlags(),
    )

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    @Suppress("DEPRECATION")
    private fun resultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

    // endregion

    private class Session(val host: String, val source: AudioCaptureSource) {
        val sender = WledAudioSyncSender(host)
        val silenceDetector = SilenceDetector()

        @Volatile
        var running = true

        @Volatile
        var asked = false

        var thread: Thread? = null
    }

    companion object {
        /** UI 读这一份状态；进程被杀后自然回到 [AudioStreamState.Idle]。 */
        private val _state = MutableStateFlow<AudioStreamState>(AudioStreamState.Idle)
        val state: StateFlow<AudioStreamState> = _state.asStateFlow()

        const val ACTION_START = "ca.cgagnier.wlednativeandroid.audio.START"
        const val ACTION_START_MIC = "ca.cgagnier.wlednativeandroid.audio.START_MIC"
        const val ACTION_STOP = "ca.cgagnier.wlednativeandroid.audio.STOP"
        const val ACTION_SWITCH_TO_MIC = "ca.cgagnier.wlednativeandroid.audio.SWITCH_TO_MIC"
        const val ACTION_DISMISS_ASK = "ca.cgagnier.wlednativeandroid.audio.DISMISS_ASK"

        const val EXTRA_HOST = "host"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SHOW_AUDIO_CONFIRM = "show_audio_confirm"

        private const val CHANNEL_ONGOING = "audio_sync"
        private const val CHANNEL_ALERT = "audio_sync_ask"
        private const val NOTIFICATION_ONGOING = 4101
        private const val NOTIFICATION_ASK = 4102
        private const val REQUEST_STOP = 1
        private const val REQUEST_ASK_YES = 2
        private const val REQUEST_ASK_NO = 3
        private const val REQUEST_OPEN_APP = 5
        private const val WINDOW_SIZE = 1024
        private const val SEND_INTERVAL_MS = 45L
        private const val UNREACHABLE_MS = 5_000L
        private const val MAX_READ_FAILURES = 3
        private const val READ_RETRY_MS = 20L

        /** 用户点「开始发送」后启动；结果码与数据来自 `createScreenCaptureIntent()`。 */
        fun start(context: Context, host: String, resultCode: Int, data: Intent) {
            val intent = Intent(context, AudioStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Android 10 以下的设备没有内录能力时，用麦克风启动。 */
        fun startWithMicrophone(context: Context, host: String) {
            val intent = Intent(context, AudioStreamService::class.java).apply {
                action = ACTION_START_MIC
                putExtra(EXTRA_HOST, host)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AudioStreamService::class.java).apply { action = ACTION_STOP },
            )
        }

        fun switchToMicrophone(context: Context) {
            context.startService(
                Intent(context, AudioStreamService::class.java).apply { action = ACTION_SWITCH_TO_MIC },
            )
        }

        fun dismissAsk(context: Context) {
            context.startService(
                Intent(context, AudioStreamService::class.java).apply { action = ACTION_DISMISS_ASK },
            )
        }
    }
}
