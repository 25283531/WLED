package ca.cgagnier.wlednativeandroid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlin.math.max

/** APP 侧的两种采集形态：A 内录其他 App 媒体，C 手机麦克风。 */
enum class CaptureSource {
    PLAYBACK,
    MICROPHONE,
}

/**
 * 单声道 PCM 采集源。实现必须让 [readMono] 阻塞到读满一帧或出错，
 * 调用方在独立线程里循环读取，不占用主线程。
 */
interface AudioCaptureSource {
    val source: CaptureSource
    val sampleRate: Int

    fun start(): Boolean

    /** @return 实际读到的样本数；<= 0 表示采集已失败 */
    fun readMono(out: ShortArray): Int

    fun stop()
}

/** 形态 C：手机麦克风，16 kHz / 单声道 / 16 bit。 */
class MicrophoneCaptureSource(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) : AudioCaptureSource {

    override val source: CaptureSource = CaptureSource.MICROPHONE
    override val sampleRate: Int = SAMPLE_RATE

    private var record: AudioRecord? = null

    override fun start(): Boolean {
        stop()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_MASK, ENCODING)
        if (minBuffer <= 0) return false
        val bufferSize = max(minBuffer, windowSize * BYTES_PER_SAMPLE)
        return try {
            val created = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                CHANNEL_MASK,
                ENCODING,
                bufferSize,
            )
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                created.release()
                return false
            }
            created.startRecording()
            if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                created.release()
                return false
            }
            record = created
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // 设备被其它 App 占用或系统拒绝时，AudioRecord 会直接抛异常
            false
        }
    }

    override fun readMono(out: ShortArray): Int =
        record?.read(out, 0, out.size) ?: ERROR_NOT_STARTED

    override fun stop() {
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val DEFAULT_WINDOW_SIZE = 1024
    }
}

/**
 * 形态 A：内录其他 App 的媒体声音（Android 10+）。
 *
 * `AudioPlaybackCaptureConfiguration` 只接受 `USAGE_MEDIA` / `USAGE_GAME` 等媒体用途，
 * 声明 `allowAudioPlaybackCapture=false` 的 App 不会报错，只会输出静音 ——
 * 这正是需要静音检测与用户确认的原因。
 */
@RequiresApi(Build.VERSION_CODES.Q)
class PlaybackCaptureSource(
    private val mediaProjection: MediaProjection,
    private val onProjectionStopped: () -> Unit,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) : AudioCaptureSource {

    override val source: CaptureSource = CaptureSource.PLAYBACK
    override val sampleRate: Int = SAMPLE_RATE

    private var record: AudioRecord? = null
    private var interleaved = ShortArray(windowSize * CHANNELS)
    private var callbackRegistered = false
    private var closing = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!closing) onProjectionStopped()
        }
    }

    override fun start(): Boolean {
        stop()
        val configuration = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(ENCODING)
            .setSampleRate(sampleRate)
            .setChannelMask(CHANNEL_MASK)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_MASK, ENCODING)
        if (minBuffer <= 0) return false
        val bufferSize = max(minBuffer, windowSize * CHANNELS * BYTES_PER_SAMPLE)
        return try {
            mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            callbackRegistered = true
            val created = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(configuration)
                .build()
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                created.release()
                releaseProjection()
                return false
            }
            created.startRecording()
            if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                created.release()
                releaseProjection()
                return false
            }
            record = created
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // 投影 token 失效或系统版本不支持时，构造函数会抛异常
            releaseProjection()
            false
        }
    }

    override fun readMono(out: ShortArray): Int {
        val recorder = record ?: return ERROR_NOT_STARTED
        val needed = out.size * CHANNELS
        if (interleaved.size < needed) interleaved = ShortArray(needed)
        val read = recorder.read(interleaved, 0, needed)
        if (read <= 0) return read
        val frames = read / CHANNELS
        for (frame in 0 until frames) {
            val left = interleaved[frame * CHANNELS]
            val right = if (CHANNELS > 1) interleaved[frame * CHANNELS + 1] else left
            out[frame] = ((left.toInt() + right.toInt()) / 2).toShort()
        }
        return frames
    }

    override fun stop() {
        val hadRecord = record != null
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
        // 没有真正开始过就不要碰 MediaProjection，否则 start() 里的清理会把授权直接作废
        if (hadRecord || callbackRegistered) releaseProjection()
    }

    private fun releaseProjection() {
        closing = true
        if (callbackRegistered) {
            runCatching { mediaProjection.unregisterCallback(projectionCallback) }
            callbackRegistered = false
        }
        runCatching { mediaProjection.stop() }
        closing = false
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val DEFAULT_WINDOW_SIZE = 1024
    }
}

private const val ERROR_NOT_STARTED = -1
