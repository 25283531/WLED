package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/**
 * 声波脉冲（固件灯效 `Sonic Pulse`）的频段配色约定。
 *
 * 固件按 `custom1` 的 bit0..2 选择关联哪些频段（低/中/高），每检测到一个频段波峰
 * 就从灯柱中心向上下两侧各发一条声波；`col[0..2]` 依次保存三个频段各自的颜色。
 * 默认只关联低频，配色为蓝 + 青。
 */
object SonicPulse {
    const val EFFECT_NAME = "Sonic Pulse"
    const val BAND_COUNT = 3
    const val ALL_BANDS_MASK = 0x07

    /** 默认只关联低频。 */
    const val DEFAULT_MASK = 0x01

    /** 固件未收到颜色时的回落色：蓝 / 青 / 青白，与固件 `kDefaultCol` 一致。 */
    val DEFAULT_COLORS = listOf(0x2060FF, 0x00E5FF, 0x66FFFF)

    /** 固件效果 id 由灯效目录推导，不在业务代码里写死。 */
    val effectId: Int? get() = EffectCatalog.byName(EFFECT_NAME)?.id

    /** 当前 segment 关联的频段掩码；非法值回落到默认低频。 */
    fun currentMask(segment: Segment?): Int {
        val mask = (segment?.custom1 ?: 0) and ALL_BANDS_MASK
        return if (mask != 0) mask else DEFAULT_MASK
    }

    fun isLinked(mask: Int, band: Int): Boolean = (mask and (1 shl band)) != 0

    /** 勾选/取消某个频段，至少保留一个频段。 */
    fun toggleBand(mask: Int, band: Int): Int {
        val next = (mask xor (1 shl band)) and ALL_BANDS_MASK
        return if (next == 0) mask else next
    }

    /** 当前 segment 三个频段的颜色，顺序为低 -> 中 -> 高；未设置时按默认色展示。 */
    fun currentColors(segment: Segment?): List<Int> {
        val slots = segment?.colors.orEmpty()
        return List(BAND_COUNT) { band ->
            val packed = slots.getOrNull(band)?.packRgb() ?: 0
            if (packed == 0) DEFAULT_COLORS[band] else packed
        }
    }
}
