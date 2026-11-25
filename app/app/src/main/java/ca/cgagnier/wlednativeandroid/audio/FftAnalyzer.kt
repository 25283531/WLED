package ca.cgagnier.wlednativeandroid.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 一帧分析结果，字段与 WLED Audio Sync v2 的 44 字节包一一对应。 */
data class AudioFrame(
    /** 瞬时电平 0-255（`sampleRaw`，与固件本地拾音同一量纲）。 */
    val sampleRaw: Float,
    /** 平滑电平 0-255（`sampleSmth`）。 */
    val sampleSmth: Float,
    /** 本帧是否检测到瞬态（`samplePeak`）。 */
    val peak: Boolean,
    /** 16 段 GEQ，0-255。 */
    val geq: ByteArray,
    /** 频谱峰值幅度（`FFT_Magnitude`）。 */
    val magnitude: Float,
    /** 能量最大频点，Hz（`FFT_MajorPeak`）。 */
    val majorPeak: Float,
)

/**
 * Hann 窗 + radix-2 FFT。
 *
 * 输出口径按固件 `NUM_GEQ_CHANNELS = 16`：40 Hz 到 16 kHz 对数分箱（不超过 Nyquist），
 * 每段取该频段内最大幅度。
 *
 * 采样率由调用方传入，因此不写死窗口内包含多少频率点。
 */
class FftAnalyzer(
    private val sampleRate: Int,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {

    init {
        require(windowSize > 1 && windowSize and (windowSize - 1) == 0) {
            "windowSize 必须是 2 的幂"
        }
    }

    private val window = FloatArray(windowSize) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / (windowSize - 1))).toFloat()
    }
    private val real = FloatArray(windowSize)
    private val imaginary = FloatArray(windowSize)
    private val magnitudes = FloatArray(windowSize / 2)
    private val bandEdges = buildBandEdges(sampleRate, windowSize)
    private val twoOverWindow = 2f / windowSize
    private val geq = ByteArray(BAND_COUNT)
    private val smoothedBands = FloatArray(BAND_COUNT)

    private var smoothedLevel = 0f
    private var lastPeakMs = -PEAK_MIN_INTERVAL_MS

    /**
     * 分析一帧单声道 PCM。
     *
     * @param samples 采样缓冲，长度至少 [length]
     * @param length 有效样本数，不足一个窗时返回上一帧的近似结果
     * @param nowMs 当前时间，用于瞬态的最小间隔
     */
    fun analyze(samples: ShortArray, length: Int, nowMs: Long): AudioFrame {
        val available = min(length, windowSize)
        val level = level255(samples, available)
        val isPeak = detectPeak(level, nowMs)
        smoothedLevel += (level - smoothedLevel) * if (level > smoothedLevel) ATTACK else RELEASE

        if (available < windowSize / 2) {
            return AudioFrame(level, smoothedLevel, isPeak, geq.copyOf(), 0f, 0f)
        }

        for (index in 0 until windowSize) {
            val sample = if (index < available) samples[index] / PCM_SCALE else 0f
            real[index] = sample * window[index]
            imaginary[index] = 0f
        }
        fft(real, imaginary)

        val maxBin = windowSize / 2
        for (bin in 1 until maxBin) {
            magnitudes[bin] = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * twoOverWindow
        }

        var loudestBin = 1
        for (band in 0 until BAND_COUNT) {
            var bandMax = 0f
            var peakBin = bandEdges[band]
            for (bin in bandEdges[band] until bandEdges[band + 1]) {
                val value = magnitudes[bin]
                if (value > bandMax) {
                    bandMax = value
                    peakBin = bin
                }
            }
            val target = amplitudeToByte(bandMax)
            // 上升快、回落慢：柱子看起来是"跳"起来的，而不是随机闪烁
            val previous = smoothedBands[band]
            smoothedBands[band] = if (target > previous) {
                previous + (target - previous) * BAND_ATTACK
            } else {
                previous + (target - previous) * BAND_RELEASE
            }
            geq[band] = smoothedBands[band].toInt().coerceIn(0, MAX_BYTE).toByte()
            if (bandMax > magnitudes[loudestBin]) loudestBin = peakBin
        }

        val majorPeak = (loudestBin.toFloat() * sampleRate / windowSize).coerceIn(1f, MAX_MAJOR_PEAK_HZ)
        return AudioFrame(
            sampleRaw = level,
            sampleSmth = smoothedLevel,
            peak = isPeak,
            geq = geq.copyOf(),
            magnitude = magnitudes[loudestBin] * MAGNITUDE_SCALE,
            majorPeak = majorPeak,
        )
    }

    private fun detectPeak(level: Float, nowMs: Long): Boolean {
        if (level < PEAK_MIN_LEVEL) return false
        if (level - smoothedLevel < PEAK_MARGIN) return false
        if (nowMs - lastPeakMs < PEAK_MIN_INTERVAL_MS) return false
        lastPeakMs = nowMs
        return true
    }

    private fun level255(samples: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (index in 0 until length) {
            val value = samples[index] / PCM_SCALE
            sum += (value * value).toDouble()
        }
        val rms = sqrt(sum / length).toFloat().coerceAtLeast(SILENCE_FLOOR)
        val dbfs = 20f * log10(rms)
        return ((dbfs - FLOOR_DB) / -FLOOR_DB * FULL_SCALE).coerceIn(0f, FULL_SCALE)
    }

    private fun amplitudeToByte(amplitude: Float): Float {
        if (amplitude <= SILENCE_FLOOR) return 0f
        val dbfs = 20f * log10(amplitude)
        return ((dbfs - FLOOR_DB) / -FLOOR_DB * MAX_BYTE).coerceIn(0f, MAX_BYTE.toFloat())
    }

    private fun fft(real: FloatArray, imaginary: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempReal = real[i]
                real[i] = real[j]
                real[j] = tempReal
                val tempImaginary = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = tempImaginary
            }
        }

        var size = 2
        while (size <= n) {
            val half = size shr 1
            val step = (-2.0 * PI / size).toFloat()
            for (start in 0 until n step size) {
                for (offset in 0 until half) {
                    val angle = step * offset
                    val cosValue = cos(angle)
                    val sinValue = kotlin.math.sin(angle)
                    val evenIndex = start + offset
                    val oddIndex = evenIndex + half
                    val oddReal = real[oddIndex] * cosValue - imaginary[oddIndex] * sinValue
                    val oddImaginary = real[oddIndex] * sinValue + imaginary[oddIndex] * cosValue
                    real[oddIndex] = real[evenIndex] - oddReal
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary
                    real[evenIndex] += oddReal
                    imaginary[evenIndex] += oddImaginary
                }
            }
            size = size shl 1
        }
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 1024
        const val BAND_COUNT = 16
        const val MAX_BYTE = 255
        const val PCM_SCALE = 32768f
        const val FULL_SCALE = 255f
        const val FLOOR_DB = -70f
        const val SILENCE_FLOOR = 1e-6f
        const val ATTACK = 0.6f
        const val RELEASE = 0.22f
        const val BAND_ATTACK = 0.75f
        const val BAND_RELEASE = 0.28f
        const val PEAK_MIN_LEVEL = 46f
        const val PEAK_MARGIN = 25f
        const val PEAK_MIN_INTERVAL_MS = 90L
        const val MAGNITUDE_SCALE = 4_000f
        const val MAX_MAJOR_PEAK_HZ = 11_025f
        const val MIN_BAND_HZ = 40.0
        const val MAX_BAND_HZ = 16_000.0

        /** 16 段对数分箱，返回 17 个边界 bin（升序、至少 1 bin 宽）。 */
        fun buildBandEdges(sampleRate: Int, windowSize: Int): IntArray {
            val nyquist = sampleRate / 2f
            val upper = min(MAX_BAND_HZ, nyquist.toDouble())
            val edges = IntArray(BAND_COUNT + 1)
            val maxBin = windowSize / 2 - 1
            val ratio = upper / MIN_BAND_HZ
            for (band in 0..BAND_COUNT) {
                val frequency = MIN_BAND_HZ * Math.pow(ratio, band.toDouble() / BAND_COUNT)
                val bin = (frequency * windowSize / sampleRate).toInt().coerceIn(1, maxBin)
                edges[band] = if (band == 0) bin else max(edges[band - 1] + 1, bin).coerceAtMost(maxBin)
            }
            // 频段过密时（如 16 kHz 采样）末尾几段可能撞到 Nyquist，保证循环非空
            for (band in BAND_COUNT - 1 downTo 0) {
                if (edges[band + 1] <= edges[band]) edges[band] = max(1, edges[band + 1] - 1)
            }
            return edges
        }
    }
}
