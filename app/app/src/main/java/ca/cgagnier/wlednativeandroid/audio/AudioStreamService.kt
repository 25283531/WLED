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

    data class Active(
        val transport: AudioTransportType,
        val deviceUnreachable: Boolean = false,
    ) : AudioStreamState

    data class Failed(
        val failure: Failure,
        val transport: AudioTransportType? = null,
        val host: String? = null,
    ) : AudioStreamState {
        enum class Failure {
            /** 没有拿到投屏授权（Android 14 每次会话都要重新授权）。 */
            PERMISSION,

            /** AudioRecord 初始化失败。 */
            CAPTURE_START,

            /** 采集过程中读取失败（投影被系统或用户结束）。 */
            CAPTURE_LOST,

            /** 蓝牙不可用：权限被拒、没有 BLE 硬件或适配器未开启。 */
            BLE_UNAVAILABLE,
        }
    }
}

/**
 * 手机内录 → FFT → 发送到灯带的前台服务。
 *
 * 采集只有内录一种形态；发送交给 [AudioTransport]：
 * - 蓝牙：BLE GATT 写入 44 字节包；
 * - 直通：UDP 组播/单播，与旧版行为一致。
 *
 * `stopWithTask=false` + `onTaskRemoved` 不停止，只有通知里的「停止」或进程结束才终止。
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
            ACTION_STOP -> stopEverything(userInitiated = true)
            else -> logger.w { "unknown action: ${intent.action}" }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 划掉最近任务不停止：只有通知的「停止」或进程结束才终止
        logger.i { "task removed, keep sending" }
    }

    override fun onDestroy() {
        session?.let { stopSession(it) }
        session = null
        if (_state.value !is AudioStreamState.Failed) {
            _state.value = AudioStreamState.Idle
        }
        super.onDestroy()
    }

    // region 启动

    private fun handleStart(intent: Intent) {
        val transportType = transportFrom(intent.getStringExtra(EXTRA_TRANSPORT))
        // startForegroundService 必须在 5 秒内进入前台，先起前台再做参数校验
        if (!startForegroundWith(transportType)) {
            val failure = if (transportType == AudioTransportType.BLE) {
                AudioStreamState.Failed.Failure.BLE_UNAVAILABLE
            } else {
                AudioStreamState.Failed.Failure.PERMISSION
            }
            _state.value = AudioStreamState.Failed(failure, transportType)
            stopSelf()
            return
        }
        val host = intent.getStringExtra(EXTRA_HOST)
        if (host.isNullOrBlank()) {
            logger.w { "start without host" }
            stopEverything(userInitiated = false)
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = resultData(intent)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || resultCode != Activity.RESULT_OK || resultData == null) {
            failAndStop(AudioStreamState.Failed.Failure.PERMISSION, transportType, host)
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
            failAndStop(AudioStreamState.Failed.Failure.PERMISSION, transportType, host)
            return
        }
        val transport = createTransport(transportType, host)
        if (!transport.start()) {
            transport.stop()
            val failure = if (transportType == AudioTransportType.BLE) {
                AudioStreamState.Failed.Failure.BLE_UNAVAILABLE
            } else {
                AudioStreamState.Failed.Failure.CAPTURE_START
            }
            logger.e { "transport failed to start: $transportType" }
            projection.stop()
            failAndStop(failure, transportType, host)
            return
        }
        val source = PlaybackCaptureSource(projection, onProjectionStopped = ::onProjectionStopped)
        startSession(host, transport, source)
    }

    private fun createTransport(type: AudioTransportType, host: String): AudioTransport = when (type) {
        AudioTransportType.BLE -> BleAudioSyncSender(applicationContext)
        AudioTransportType.UDP -> UdpAudioTransport(host)
    }

    private fun startSession(host: String, transport: AudioTransport, source: AudioCaptureSource) {
        session?.let { stopSession(it) }
        session = null
        if (!source.start()) {
            logger.e { "capture source failed to start" }
            source.stop()
            transport.stop()
            failAndStop(AudioStreamState.Failed.Failure.CAPTURE_START, transport.type, host)
            return
        }
        val newSession = Session(host, transport, source)
        session = newSession
        newSession.thread = Thread({ captureLoop(newSession) }, "AudioSyncCapture").apply {
            isDaemon = true
            start()
        }
        _state.value = AudioStreamState.Active(transport.type)
        updateOngoingNotification(transport.type)
    }

    private fun onProjectionStopped() {
        val current = session ?: return
        logger.w { "media projection stopped" }
        failAndStop(AudioStreamState.Failed.Failure.CAPTURE_LOST, current.transport.type, current.host)
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
                            session.transport.type,
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
                if (!session.transport.send(frame)) {
                    if (unreachableSinceMs == 0L) unreachableSinceMs = now
                    if (now - unreachableSinceMs >= UNREACHABLE_MS) {
                        logger.w { "device unreachable for ${UNREACHABLE_MS}ms" }
                        unreachableSinceMs = now
                        mainHandler.post { markUnreachable(session.transport.type) }
                    }
                } else if (unreachableSinceMs != 0L) {
                    unreachableSinceMs = 0L
                    mainHandler.post { markReachable(session.transport.type) }
                }
            }
        }
    }

    private fun markUnreachable(transport: AudioTransportType) {
        if (session?.transport?.type != transport) return
        if (_state.value is AudioStreamState.Active) {
            _state.value = AudioStreamState.Active(transport, deviceUnreachable = true)
        }
    }

    private fun markReachable(transport: AudioTransportType) {
        if (session?.transport?.type != transport) return
        if (_state.value is AudioStreamState.Active) {
            _state.value = AudioStreamState.Active(transport, deviceUnreachable = false)
        }
    }

    // endregion

    // region 生命周期收尾

    private fun stopSession(session: Session) {
        session.running = false
        runCatching { session.source.stop() }
        session.thread?.interrupt()
        session.transport.stop()
    }

    private fun stopEverything(userInitiated: Boolean) {
        session?.let { stopSession(it) }
        session = null
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
        transport: AudioTransportType? = null,
        host: String? = null,
    ) {
        logger.e { "audio stream failed: $failure (transport=$transport)" }
        session?.let { stopSession(it) }
        session = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ONGOING)
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        _state.value = AudioStreamState.Failed(failure, transport, host)
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
        manager.createNotificationChannel(ongoing)
    }

    private fun startForegroundWith(transport: AudioTransportType): Boolean {
        val notification = buildOngoingNotification(transport)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (transport == AudioTransportType.BLE) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                startForeground(NOTIFICATION_ONGOING, notification, type)
            } else {
                startForeground(NOTIFICATION_ONGOING, notification)
            }
            foregroundStarted = true
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // 缺少 mediaProjection / 蓝牙运行时权限时系统会直接拒绝前台类型
            logger.e(e) { "startForeground rejected for $transport" }
            false
        }
    }

    private fun updateOngoingNotification(transport: AudioTransportType) {
        if (!foregroundStarted) return
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ONGOING,
            buildOngoingNotification(transport),
        )
    }

    private fun buildOngoingNotification(transport: AudioTransportType): Notification {
        val statusRes = when (transport) {
            AudioTransportType.BLE -> R.string.isle_audio_status_ble
            AudioTransportType.UDP -> R.string.isle_audio_status_passthrough
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

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
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

    private class Session(
        val host: String,
        val transport: AudioTransport,
        val source: AudioCaptureSource,
    ) {
        @Volatile
        var running = true

        var thread: Thread? = null
    }

    companion object {
        /** UI 读这一份状态；进程被杀后自然回到 [AudioStreamState.Idle]。 */
        private val _state = MutableStateFlow<AudioStreamState>(AudioStreamState.Idle)
        val state: StateFlow<AudioStreamState> = _state.asStateFlow()

        const val ACTION_START = "ca.cgagnier.wlednativeandroid.audio.START"
        const val ACTION_STOP = "ca.cgagnier.wlednativeandroid.audio.STOP"

        const val EXTRA_HOST = "host"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_TRANSPORT = "transport"

        private const val CHANNEL_ONGOING = "audio_sync"
        private const val NOTIFICATION_ONGOING = 4101
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN_APP = 5
        private const val WINDOW_SIZE = 1024
        private const val SEND_INTERVAL_MS = 45L
        private const val UNREACHABLE_MS = 5_000L
        private const val MAX_READ_FAILURES = 3
        private const val READ_RETRY_MS = 20L

        /** 用户点「开始发送」后启动；结果码与数据来自 `createScreenCaptureIntent()`。 */
        fun start(
            context: Context,
            host: String,
            resultCode: Int,
            data: Intent,
            transport: AudioTransportType,
        ) {
            val intent = Intent(context, AudioStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_TRANSPORT, transport.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AudioStreamService::class.java).apply { action = ACTION_STOP },
            )
        }

        private fun transportFrom(value: String?): AudioTransportType =
            AudioTransportType.entries.firstOrNull { it.name == value } ?: AudioTransportType.UDP
    }
}
