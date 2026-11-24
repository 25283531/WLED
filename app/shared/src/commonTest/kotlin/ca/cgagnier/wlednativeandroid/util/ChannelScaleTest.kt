package ca.cgagnier.wlednativeandroid.util

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelScaleTest {

    // --- normalizedToChannel tests ---

    @Test
    fun normalizedToChannel_withZero_returnsZero() {
        assertEquals(0, normalizedToChannel(0f))
    }

    @Test
    fun normalizedToChannel_withOne_returns255() {
        assertEquals(255, normalizedToChannel(1f))
    }

    @Test
    fun normalizedToChannel_withHalf_returns128() {
        // 0.5 × 255 = 127.5 → 128（四舍五入）
        assertEquals(128, normalizedToChannel(0.5f))
    }

    @Test
    fun normalizedToChannel_clampsNegativeToZero() {
        assertEquals(0, normalizedToChannel(-0.5f))
    }

    @Test
    fun normalizedToChannel_clampsAboveOneTo255() {
        assertEquals(255, normalizedToChannel(1.5f))
    }

    // --- channelToNormalized tests ---

    @Test
    fun channelToNormalized_withZero_returnsZero() {
        assertEquals(0f, channelToNormalized(0), 0.0001f)
    }

    @Test
    fun channelToNormalized_with255_returnsOne() {
        assertEquals(1f, channelToNormalized(255), 0.0001f)
    }

    @Test
    fun channelToNormalized_with128_returnsApproximatelyHalf() {
        assertEquals(0.502f, channelToNormalized(128), 0.001f)
    }

    @Test
    fun channelToNormalized_clampsOutOfRange() {
        assertEquals(0f, channelToNormalized(-10), 0.0001f)
        assertEquals(1f, channelToNormalized(300), 0.0001f)
    }

    // --- channelToPercent tests ---

    @Test
    fun channelToPercent_withZero_returnsZero() {
        assertEquals(0, channelToPercent(0))
    }

    @Test
    fun channelToPercent_with255_returns100() {
        assertEquals(100, channelToPercent(255))
    }

    @Test
    fun channelToPercent_with128_returns50() {
        // 128 × 100 / 255 ≈ 50.2 → 50
        assertEquals(50, channelToPercent(128))
    }

    @Test
    fun channelToPercent_withFive_returnsTwo() {
        // 5 × 100 / 255 ≈ 1.96 → 2
        assertEquals(2, channelToPercent(5))
    }

    @Test
    fun channelToPercent_clampsOutOfRange() {
        assertEquals(0, channelToPercent(-10))
        assertEquals(100, channelToPercent(300))
    }

    // --- percentToChannel tests ---

    @Test
    fun percentToChannel_withZeroPercent_returnsZero() {
        assertEquals(0, percentToChannel(0f))
    }

    @Test
    fun percentToChannel_with100Percent_returns255() {
        assertEquals(255, percentToChannel(100f))
    }

    @Test
    fun percentToChannel_with50Percent_returns128() {
        // 50/100 × 255 = 127.5 → 128
        assertEquals(128, percentToChannel(50f))
    }

    @Test
    fun percentToChannel_withOnePercent_returnsThree() {
        // 1/100 × 255 = 2.55 → 3
        assertEquals(3, percentToChannel(1f))
    }

    @Test
    fun percentToChannel_clampsOutOfRange() {
        assertEquals(0, percentToChannel(-10f))
        assertEquals(255, percentToChannel(150f))
    }

    // --- normalizedToPercent tests ---

    @Test
    fun normalizedToPercent_withZero_returnsZero() {
        assertEquals(0, normalizedToPercent(0f))
    }

    @Test
    fun normalizedToPercent_withOne_returns100() {
        assertEquals(100, normalizedToPercent(1f))
    }

    @Test
    fun normalizedToPercent_withHalf_returns50() {
        assertEquals(50, normalizedToPercent(0.5f))
    }

    @Test
    fun normalizedToPercent_clampsOutOfRange() {
        assertEquals(0, normalizedToPercent(-0.2f))
        assertEquals(100, normalizedToPercent(1.5f))
    }

    // --- 回归测试：滑块必须覆盖 0..255 全量程 ---

    @Test
    fun normalizedToChannel_sliderPositionsMapToExpectedChannels() {
        // 这几个位置是当初真机上实测过的 tap 坐标换算出来的比例，
        // 旧实现下它们分别落到 0/2/5/5/5/7，肉眼完全看不出差别。
        val samples = listOf(0f, 0.25f, 0.5f, 0.673f, 0.769f, 1f)
        assertEquals(listOf(0, 64, 128, 172, 196, 255), samples.map { normalizedToChannel(it) })
    }

    @Test
    fun normalizedToChannel_isMonotonicAcrossWholeRange() {
        var previous = -1
        for (percent in 0..100) {
            val channel = normalizedToChannel(percent / 100f)
            assertTrue(channel >= previous, "percent=$percent channel=$channel previous=$previous")
            previous = channel
        }
        assertEquals(0, normalizedToChannel(0f))
        assertEquals(CHANNEL_MAX, normalizedToChannel(1f))
    }

    @Test
    fun normalizedToChannel_coversFarMoreRangeThanLegacyDoubleConversion() {
        // 旧实现把「比例 → 通道」换算做了两遍（×255/100 各一次），
        // 拖到头也只能输出 7 —— 全量程被压掉 97%，这就是「滑块拖不动」的根因。
        // 这里的 255/100 用整数字面量，忠实还原旧代码的 Int 整除语义。
        fun legacyDoubleConversion(normalized: Float): Int {
            val first = (normalized * 255 / 100).roundToInt()
            return first * 255 / 100
        }

        val legacyMax = (0..100).map { legacyDoubleConversion(it / 100f) }.max()
        val currentMax = (0..100).map { normalizedToChannel(it / 100f) }.max()

        assertEquals(7, legacyMax)
        assertEquals(CHANNEL_MAX, currentMax)
    }

    // --- 往返一致性 ---

    @Test
    fun roundtrip_channelThroughNormalized_isLossless() {
        for (channel in 0..CHANNEL_MAX) {
            assertEquals(channel, normalizedToChannel(channelToNormalized(channel)))
        }
    }
}
