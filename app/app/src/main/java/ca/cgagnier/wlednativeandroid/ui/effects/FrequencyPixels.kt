package ca.cgagnier.wlednativeandroid.ui.effects

import androidx.annotation.StringRes
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import kotlin.math.abs

/**
 * 频谱像素（固件灯效 `Freqpixels`）的四色配色模板。
 *
 * 只用于 App 侧：选中模板时把 4 个颜色写进设备，不依赖固件的调色板编号。
 * 这样既能让用户从「四色模板」里快速选一套配色，也能逐个调整每个频段的颜色。
 */
data class FourColorPalette(
    @StringRes val nameRes: Int,
    val colors: List<Int>,
)

/**
 * 频谱像素的 4 频段配色约定。
 *
 * WLED 每个 segment 只有 3 个颜色槽（`col`），第 4 色用 21bit RGB777
 * 打包进 `c1/c2/c3`（`c3` 固件侧只有 5bit）。固件按同样的位序解包，
 * 因此这里必须和固件 `mode_freqpixels()` 的解包方式保持一致。
 */
object FrequencyPixels {
    const val EFFECT_NAME = "Freqpixels"
    const val BAND_COUNT = 4

    private const val SEVEN_BIT_MAX = 127
    private const val MATCH_TOLERANCE = 3

    /** 固件效果 id 由灯效目录推导，不在业务代码里写死。 */
    val effectId: Int? get() = EffectCatalog.byName(EFFECT_NAME)?.id

    val palettes: List<FourColorPalette> = listOf(
        FourColorPalette(
            R.string.isle_freq_palette_aurora,
            listOf(0x00F5FF, 0x1E78FF, 0xAF28FF, 0x3CFFB0),
        ),
        FourColorPalette(
            R.string.isle_freq_palette_lava,
            listOf(0xFF2D00, 0xFF7A00, 0xFFC300, 0xFFF2B0),
        ),
        FourColorPalette(
            R.string.isle_freq_palette_ocean,
            listOf(0x003C78, 0x0074B7, 0x00B4D8, 0x9BE7FF),
        ),
        FourColorPalette(
            R.string.isle_freq_palette_neon,
            listOf(0xFF2E88, 0xFFD400, 0x00F5A0, 0x8A5CFF),
        ),
        FourColorPalette(
            R.string.isle_freq_palette_sunset,
            listOf(0x3A0CA3, 0xF72585, 0xFF7B00, 0xFFD166),
        ),
        FourColorPalette(
            R.string.isle_freq_palette_forest,
            listOf(0x0B3D2E, 0x1F7A4D, 0x5FD068, 0xE2FFB3),
        ),
    )

    /** 当前 segment 的 4 个频段颜色，顺序为低 -> 高。 */
    fun currentColors(segment: Segment?): List<Int> {
        val slots = segment?.colors.orEmpty()
        return listOf(
            slots.getOrNull(0)?.packRgb() ?: 0,
            slots.getOrNull(1)?.packRgb() ?: 0,
            slots.getOrNull(2)?.packRgb() ?: 0,
            unpackBand4(segment?.custom1, segment?.custom2, segment?.custom3),
        )
    }

    /** 颜色接近某个模板时返回该模板，用于模板选择器的高亮。 */
    fun matchPalette(colors: List<Int>): FourColorPalette? = palettes.firstOrNull { palette ->
        palette.colors.indices.all { i ->
            val expected = palette.colors[i]
            val actual = colors.getOrElse(i) { 0 }
            channelNear(expected, actual, 16) &&
                channelNear(expected, actual, 8) &&
                channelNear(expected, actual, 0)
        }
    }

    /** 第 4 色打包成 `c1/c2/c3`，位序与固件 `mode_freqpixels()` 一致。 */
    fun packBand4(packedRgb: Int): Triple<Int, Int, Int> {
        val value = (toSevenBit((packedRgb shr 16) and 0xFF) shl 14) or
            (toSevenBit((packedRgb shr 8) and 0xFF) shl 7) or
            toSevenBit(packedRgb and 0xFF)
        return Triple(value and 0xFF, (value shr 8) and 0xFF, (value shr 16) and 0x1F)
    }

    /** 从 `c1/c2/c3` 还原第 4 色。 */
    fun unpackBand4(custom1: Int?, custom2: Int?, custom3: Int?): Int {
        val value = (custom1 ?: 0) or
            ((custom2 ?: 0) shl 8) or
            (((custom3 ?: 0) and 0x1F) shl 16)
        val red = fromSevenBit((value shr 14) and 0x7F)
        val green = fromSevenBit((value shr 7) and 0x7F)
        val blue = fromSevenBit(value and 0x7F)
        return packRgb(red, green, blue)
    }

    private fun toSevenBit(channel: Int): Int =
        (channel.coerceIn(0, 255) * SEVEN_BIT_MAX + 127) / 255

    private fun fromSevenBit(channel: Int): Int = (channel shl 1) or (channel shr 6)

    private fun channelNear(expected: Int, actual: Int, shift: Int): Boolean {
        val expectedChannel = (expected shr shift) and 0xFF
        val actualChannel = (actual shr shift) and 0xFF
        return abs(expectedChannel - actualChannel) <= MATCH_TOLERANCE
    }
}
