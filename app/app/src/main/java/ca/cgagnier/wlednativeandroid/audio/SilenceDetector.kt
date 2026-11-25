package ca.cgagnier.wlednativeandroid.audio

/**
 * A → C 降级判定：内录已稳定运行一段时间后，连续静音超过阈值才认为"可疑"。
 *
 * 只做静音判定，不做任何自动切换 —— 命中后必须由用户确认，
 * 因为「用户主动暂停」「音量极低」「蓝牙输出」都会表现成静音。
 */
class SilenceDetector(
    private val minRunMs: Long = MIN_RUN_MS,
    private val silentMs: Long = SILENT_MS,
    private val thresholdDbfs: Float = THRESHOLD_DBFS,
) {

    private var startedAtMs = 0L
    private var silentSinceMs: Long? = null

    fun reset(nowMs: Long) {
        startedAtMs = nowMs
        silentSinceMs = null
    }

    /**
     * @param level 该帧的 0-255 电平（与固件 `sampleRaw` 同量纲）
     * @return true 表示"连续静音可疑"，调用方应询问用户而不是直接切换
     */
    fun onFrame(level: Float, nowMs: Long): Boolean {
        val dbfs = if (level <= 0f) {
            MIN_DB
        } else {
            (level / FULL_SCALE) * -SILENCE_FLOOR_DB + SILENCE_FLOOR_DB
        }
        if (dbfs > thresholdDbfs) {
            silentSinceMs = null
            return false
        }
        if (silentSinceMs == null) silentSinceMs = nowMs
        val silent = silentSinceMs ?: return false
        val runningLongEnough = nowMs - startedAtMs >= minRunMs
        return runningLongEnough && nowMs - silent >= silentMs
    }

    private companion object {
        const val MIN_RUN_MS = 10_000L
        const val SILENT_MS = 5_000L
        const val THRESHOLD_DBFS = -60f
        const val MIN_DB = -100f
        const val FULL_SCALE = 255f
        const val SILENCE_FLOOR_DB = -70f
    }
}
