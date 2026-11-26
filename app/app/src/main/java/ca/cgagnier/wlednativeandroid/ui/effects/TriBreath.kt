package ca.cgagnier.wlednativeandroid.ui.effects

import androidx.annotation.StringRes
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import kotlin.math.abs

/**
 * 三频呼吸（固件灯效 `Tri-band Breath`）的三色配色模板。
 *
 * 只用于 App 侧：选中模板时把 3 个频段颜色写进设备 `col[0..2]`，
 * 不依赖固件调色板编号。
 */
data class TriColorPalette(
    @StringRes val nameRes: Int,
    val colors: List<Int>,
)

/**
 * 三频呼吸的频段配色约定。
 *
 * 低频 / 中频 / 高频分别对应灯柱自下而上的三段，颜色依次写入 `col[0..2]`；
 * 与固件 `mode_tribreath()` 的取色顺序保持一致。
 */
object TriBreath {
    const val EFFECT_NAME = "Tri-band Breath"
    const val BAND_COUNT = 3

    private const val MATCH_TOLERANCE = 3

    /** 固件未收到颜色时的回落色：暖橙 / 薄荷 / 亮蓝。 */
    val DEFAULT_COLORS = listOf(0xFF7020, 0x30FF90, 0x5080FF)

    /** 固件效果 id 由灯效目录推导，不在业务代码里写死。 */
    val effectId: Int? get() = EffectCatalog.byName(EFFECT_NAME)?.id

    val palettes: List<TriColorPalette> = listOf(
        TriColorPalette(
            R.string.isle_freq_palette_aurora,
            listOf(0x00F5FF, 0x1E78FF, 0xAF28FF),
        ),
        TriColorPalette(
            R.string.isle_freq_palette_lava,
            listOf(0xFF2D00, 0xFF7A00, 0xFFC300),
        ),
        TriColorPalette(
            R.string.isle_freq_palette_ocean,
            listOf(0x003C78, 0x0074B7, 0x9BE7FF),
        ),
        TriColorPalette(
            R.string.isle_freq_palette_neon,
            listOf(0xFF2E88, 0xFFD400, 0x00F5A0),
        ),
        TriColorPalette(
            R.string.isle_freq_palette_sunset,
            listOf(0x3A0CA3, 0xF72585, 0xFFD166),
        ),
        TriColorPalette(
            R.string.isle_freq_palette_forest,
            listOf(0x0B3D2E, 0x1F7A4D, 0xE2FFB3),
        ),
    )

    /** 当前 segment 的三个频段颜色，顺序为低 -> 中 -> 高；未设置时按默认色展示。 */
    fun currentColors(segment: Segment?): List<Int> {
        val slots = segment?.colors.orEmpty()
        return List(BAND_COUNT) { band ->
            val packed = slots.getOrNull(band)?.packRgb() ?: 0
            if (packed == 0) DEFAULT_COLORS[band] else packed
        }
    }

    /** 颜色接近某个模板时返回该模板，用于模板选择器的高亮。 */
    fun matchPalette(colors: List<Int>): TriColorPalette? = palettes.firstOrNull { palette ->
        palette.colors.indices.all { i ->
            channelNear(palette.colors[i], colors.getOrElse(i) { 0 }, 16) &&
                channelNear(palette.colors[i], colors.getOrElse(i) { 0 }, 8) &&
                channelNear(palette.colors[i], colors.getOrElse(i) { 0 }, 0)
        }
    }

    private fun channelNear(expected: Int, actual: Int, shift: Int): Boolean {
        val expectedChannel = (expected shr shift) and 0xFF
        val actualChannel = (actual shr shift) and 0xFF
        return abs(expectedChannel - actualChannel) <= MATCH_TOLERANCE
    }
}
