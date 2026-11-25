package ca.cgagnier.wlednativeandroid.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
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
 * Hann 窗 + radix-2 FFT，输出 16 段 GEQ。
 *
 * 管线刻意对齐固件 `audio_reactive.cpp` 的 `postProcessFFTResults`，让手机内录和灯本地麦
 * 产生**同分布**的 fftResult，效果（按本地分布调参）在两种音源下表现一致：
 *
 *  1. 滑动窗累积 → FFT 每帧都有完整窗口（不会因半满缓冲冻结柱子）。
 *  2. 轻量 AGC → 把采集电平拉到目标量程，补偿手机内录偏低 / 音量键变化。
 *  3. 固件同款 bin 映射 + 阻尼（bin 宽度 44100/1024 ≈ 22050/512 = 43Hz，直接对齐）。
 *  4. 粉噪归一化 `fftResultPink`（高频最高拉 9.55 倍）。
 *  5. sqrt 感知缩放（对应固件 `FFTScalingMode = 3`）。
 *  6. 上升快 / 回落慢平滑 + 限幅（对应固件 limiter 默认开启）。
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

    /** 最近 windowSize 个单声道样本（已归一化到 [-1, 1]），新样本追加到末尾。 */
    private val window = FloatArray(windowSize)
    private val hann = FloatArray(windowSize) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / (windowSize - 1))).toFloat()
    }
    private val real = FloatArray(windowSize)
    private val imaginary = FloatArray(windowSize)
    private val magnitudes = FloatArray(windowSize / 2)

    private val geq = ByteArray(BAND_COUNT)
    /** 平滑后的段值（粉噪后、缩放前），对应固件 `fftAvg`。 */
    private val fftAvg = FloatArray(BAND_COUNT)

    private var smoothedLevel = 0f
    private var lastPeakMs = -PEAK_MIN_INTERVAL_MS

    // region AGC 状态
    private var agcGain = 1f
    /** 跟踪各段「幅度 × 粉噪」的峰值（AGC 的目标量）。 */
    private var smoothedPinkPeak = 0f
    // endregion

    private var debugFrame = 0

    fun analyze(samples: ShortArray, length: Int, nowMs: Long): AudioFrame {
        val n = min(length, windowSize)
        if (n > 0) {
            // 滑动窗：丢掉最旧的 n 个，把新的 n 个（归一化）追加到末尾
            if (n < windowSize) {
                window.copyInto(window, destinationOffset = 0, startIndex = n, endIndex = windowSize)
            }
            for (i in 0 until n) {
                window[windowSize - n + i] = samples[i] / PCM_SCALE
            }
        }

        // 电平（RMS，未加 AGC）—— 噪声门用
        val rawRms = computeRms(window)
        val rawPeak = computePeak(window)

        for (index in 0 until windowSize) {
            real[index] = window[index] * hann[index]
            imaginary[index] = 0f
        }
        fft(real, imaginary)

        val maxBin = windowSize / 2
        for (bin in 1 until maxBin) {
            magnitudes[bin] = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * TWO_OVER_WINDOW
        }

        // ---- 各段原始平均幅度（对齐固件 fftAddAvg：取区间平均，再乘阻尼）----
        val bandAvg = FloatArray(BAND_COUNT)
        var loudestBin = 1
        var loudestMag = 0f
        var pinkPeak = 0f
        for (band in 0 until BAND_COUNT) {
            val spec = BANDS[band]
            val from = spec.from.coerceIn(1, maxBin - 1)
            val to = spec.to.coerceIn(from, maxBin - 1)
            var sum = 0f
            var count = 0
            for (bin in from..to) {
                val v = magnitudes[bin]
                sum += v
                count++
                if (v > loudestMag) {
                    loudestMag = v
                    loudestBin = bin
                }
            }
            val avg = if (count > 0) sum / count else 0f
            bandAvg[band] = avg * spec.damping
            val pink = bandAvg[band] * PINK[band]
            if (pink > pinkPeak) pinkPeak = pink
        }

        // ---- AGC：把「幅度 × 粉噪」的峰值拉到 TARGET_PINK_PEAK ----
        // 噪声门：信号太弱时不抬增益，避免把静噪放大成底噪
        val gateOpen = rawPeak > NOISE_PEAK
        smoothedPinkPeak += (pinkPeak - smoothedPinkPeak) *
            if (pinkPeak > smoothedPinkPeak) PEAK_RISE else PEAK_FALL
        if (gateOpen && smoothedPinkPeak > 1e-4f) {
            val desired = (TARGET_PINK_PEAK / smoothedPinkPeak).coerceIn(MIN_GAIN, MAX_GAIN)
            val adapt = if (desired < agcGain) AGC_ATTACK else AGC_RELEASE
            agcGain += (desired - agcGain) * adapt
            agcGain = agcGain.coerceIn(MIN_GAIN, MAX_GAIN)
        }
        val gain = agcGain

        // ---- 段值：AGC → 粉噪 → 平滑（上升快、回落慢）→ sqrt 感知缩放 ----
        var geqMax = 0f
        var geqSum = 0f
        for (band in 0 until BAND_COUNT) {
            val withPink = bandAvg[band] * gain * PINK[band]
            val prev = fftAvg[band]
            val smoothed = if (withPink > prev) {
                withPink * BAND_RISE_NEW + prev * BAND_RISE_OLD
            } else {
                withPink * BAND_FALL_NEW + prev * BAND_FALL_OLD
            }
            fftAvg[band] = smoothed.coerceIn(0f, 1023f)
            // sqrt 感知缩放，校准到归一化幅度量程（sqrt(peak) * OUT_SCALE → ~TARGET_BYTE）
            val cr = smoothed.coerceAtLeast(0f)
            val v = if (cr > SQRT_FLOOR) sqrt(cr) else 0f
            val out = (v * OUT_SCALE).coerceIn(0f, MAX_BYTE.toFloat())
            geq[band] = out.toInt().toByte()
            if (out > geqMax) geqMax = out
            geqSum += out
        }

        // ---- 电平 sampleRaw / sampleSmth：AGC 后 RMS，dBFS 映射（-60dB 起步）----
        val agcRms = (rawRms * gain).coerceAtLeast(SILENCE_FLOOR)
        val dbfs = 20f * log10(agcRms)
        val level = ((dbfs - LEVEL_FLOOR_DB) / -LEVEL_FLOOR_DB * MAX_BYTE).coerceIn(0f, MAX_BYTE.toFloat())
        val isPeak = detectPeak(level, nowMs)
        smoothedLevel += (level - smoothedLevel) * if (level > smoothedLevel) ATTACK else RELEASE

        if (DEBUG) {
            debugFrame++
            if (debugFrame % DEBUG_INTERVAL == 0) {
                val sb = StringBuilder()
                for (b in 0 until BAND_COUNT) { sb.append(geq[b].toInt().and(0xFF)); if (b < 15) sb.append(',') }
                Log.d(
                    TAG,
                    "gain=%.2f pinkPeak=%.3f rawPeak=%.4f level=%d max=%d geq=[%s]"
                        .format(gain, smoothedPinkPeak, rawPeak, level.toInt(), geqMax.toInt(), sb),
                )
            }
        }

        val majorPeak = (loudestBin.toFloat() * sampleRate / windowSize).coerceIn(1f, MAX_MAJOR_PEAK_HZ)
        return AudioFrame(
            sampleRaw = level,
            sampleSmth = smoothedLevel,
            peak = isPeak,
            geq = geq.copyOf(),
            magnitude = (loudestMag * gain).coerceAtLeast(0f) * MAGNITUDE_SCALE,
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

    private fun computeRms(buf: FloatArray): Float {
        var sum = 0.0
        for (i in buf.indices) {
            val v = buf[i]
            sum += (v * v).toDouble()
        }
        return sqrt(sum / buf.size).toFloat().coerceAtLeast(0f)
    }

    private fun computePeak(buf: FloatArray): Float {
        var max = 0f
        for (i in buf.indices) {
            val v = if (buf[i] < 0f) -buf[i] else buf[i]
            if (v > max) max = v
        }
        return max
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
                    val oddImag = real[oddIndex] * sinValue + imaginary[oddIndex] * cosValue
                    real[oddIndex] = real[evenIndex] - oddReal
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImag
                    real[evenIndex] += oddReal
                    imaginary[evenIndex] += oddImag
                }
            }
            size = size shl 1
        }
    }

    private data class BandSpec(val from: Int, val to: Int, val damping: Float)

    private companion object {
        const val TAG = "FftAnalyzer"
        const val DEBUG = true
        const val DEBUG_INTERVAL = 20

        const val DEFAULT_WINDOW_SIZE = 1024
        const val BAND_COUNT = 16
        const val MAX_BYTE = 255
        const val PCM_SCALE = 32768f

        // 归一化：FFT 幅度 × 2/N，满幅正弦 → 约 1.0
        const val TWO_OVER_WINDOW = 2f / DEFAULT_WINDOW_SIZE

        // 电平映射
        const val LEVEL_FLOOR_DB = -60f
        const val ATTACK = 0.6f
        const val RELEASE = 0.22f

        // 瞬态检测
        const val PEAK_MIN_LEVEL = 40f
        const val PEAK_MARGIN = 18f
        const val PEAK_MIN_INTERVAL_MS = 90L

        // 段平滑：上升快 / 回落慢（对齐固件 postProcessFFTResults 默认 decayTime）
        const val BAND_RISE_NEW = 0.75f
        const val BAND_RISE_OLD = 0.25f
        const val BAND_FALL_NEW = 0.22f
        const val BAND_FALL_OLD = 0.78f

        // AGC
        const val TARGET_PINK_PEAK = 0.72f   // 粉噪后峰值目标 → sqrt*OUT_SCALE ≈ 205
        const val OUT_SCALE = 242f
        const val SQRT_FLOOR = 0.0001f
        const val NOISE_PEAK = 0.004f        // 约 -48dBFS，低于此不抬增益
        const val PEAK_RISE = 0.6f
        const val PEAK_FALL = 0.18f
        const val AGC_ATTACK = 0.35f         // 过响时快速降增益，防削波
        const val AGC_RELEASE = 0.06f        // 过轻时缓慢抬增益，防抽吸
        const val MIN_GAIN = 0.5f
        const val MAX_GAIN = 14f

        const val MAGNITUDE_SCALE = 6_000f
        const val MAX_MAJOR_PEAK_HZ = 11_025f
        const val SILENCE_FLOOR = 1e-6f

        /**
         * 固件同款 bin 映射（非带通路径，对应 ES7210 I2S）：bin 宽度 43.07Hz，
         * [from, to] 闭区间取平均，再乘 damping。与 `audio_reactive.cpp` L536-553 一致。
         */
        val BANDS = arrayOf(
            BandSpec(1, 2, 1.00f),    // 43 - 86   sub-bass
            BandSpec(2, 3, 1.00f),    // 86 - 129  bass
            BandSpec(3, 5, 1.00f),    // 129 - 216 bass
            BandSpec(5, 7, 1.00f),    // 216 - 301 bass + mid
            BandSpec(7, 10, 1.00f),   // 301 - 430 mid
            BandSpec(10, 13, 1.00f),  // 430 - 560 mid
            BandSpec(13, 19, 1.00f),  // 560 - 818 mid
            BandSpec(19, 26, 1.00f),  // 818 - 1120 mid (1kHz 居中)
            BandSpec(26, 33, 1.00f),  // 1120 - 1421 mid
            BandSpec(33, 44, 1.00f),  // 1421 - 1895 mid
            BandSpec(44, 56, 1.00f),  // 1895 - 2412 mid + high-mid
            BandSpec(56, 70, 1.00f),  // 2412 - 3015 high-mid
            BandSpec(70, 86, 1.00f),  // 3015 - 3704 high-mid
            BandSpec(86, 104, 1.00f), // 3704 - 4479 high-mid
            BandSpec(104, 165, 0.88f),// 4479 - 7106 high-mid + high (轻微阻尼)
            BandSpec(165, 215, 0.70f),// 7106 - 9259 high (阻尼，避开混叠段)
        )

        /** 粉噪归一化表，逐字对齐固件 `fftResultPink`。 */
        val PINK = floatArrayOf(
            1.70f, 1.71f, 1.73f, 1.78f, 1.68f, 1.56f, 1.55f, 1.63f,
            1.79f, 1.62f, 1.80f, 2.06f, 2.47f, 3.35f, 6.83f, 9.55f,
        )
    }
}
