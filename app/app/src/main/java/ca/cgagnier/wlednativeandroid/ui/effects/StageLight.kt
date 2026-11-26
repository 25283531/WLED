package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/**
 * 舞台灯（固件灯效 `Stage Light`）的频段配色约定。
 *
 * 固件按 `custom1` 的 bit0..2 选择关联哪些频段（低/中/高），
 * `col[0..2]` 依次保存三个频段各自的爆闪颜色；颜色未设置时固件回落为白色。
 */
object StageLight {
    const val EFFECT_NAME = "Stage Light"
    const val BAND_COUNT = 3
    const val ALL_BANDS_MASK = 0x07

    /** 默认只关联低频。 */
    const val DEFAULT_MASK = 0x01

    /** 默认白色光。 */
    const val DEFAULT_COLOR = 0xFFFFFF

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

    /** 当前 segment 三个频段的颜色，顺序为低 -> 中 -> 高；未设置时按白色展示。 */
    fun currentColors(segment: Segment?): List<Int> {
        val slots = segment?.colors.orEmpty()
        return List(BAND_COUNT) { band ->
            val packed = slots.getOrNull(band)?.packRgb() ?: 0
            if (packed == 0) DEFAULT_COLOR else packed
        }
    }
}
