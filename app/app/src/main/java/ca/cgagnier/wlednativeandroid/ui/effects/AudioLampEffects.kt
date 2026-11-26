package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/**
 * 音频螺旋（固件灯效 `Audio Spiral`）的双主链配色约定。
 *
 * 两条主链之间夹一条音频响应填充带：`col[0]` 为 A 链、`col[1]` 为 B 链，
 * 与固件 `mode_audiospiral()` 的取色顺序一致。速度滑杆控制螺旋上升转速，
 * 强度滑杆控制填充带的亮度增益。
 */
object AudioSpiral {
    const val EFFECT_NAME = "Audio Spiral"
    const val CHAIN_COUNT = 2

    /** 固件未收到颜色时的回落色：暖橙 / 亮蓝。 */
    val DEFAULT_COLORS = listOf(0xF56E0F, 0x1491FF)

    /** 固件效果 id 由灯效目录推导，不在业务代码里写死。 */
    val effectId: Int? get() = EffectCatalog.byName(EFFECT_NAME)?.id

    /** 当前 segment 两条主链的颜色，顺序为 A -> B；未设置时按默认色展示。 */
    fun currentColors(segment: Segment?): List<Int> {
        val slots = segment?.colors.orEmpty()
        return List(CHAIN_COUNT) { chain ->
            val packed = slots.getOrNull(chain)?.packRgb() ?: 0
            if (packed == 0) DEFAULT_COLORS[chain] else packed
        }
    }
}

/**
 * 低频熔岩（固件灯效 `Bass Lava`）。
 *
 * 内部使用固定的熔岩色带，没有可换的颜色；设备页只暴露速度（漂移快慢）
 * 与强度（低频泵浦的膨胀、提亮幅度）两个滑杆。
 */
object BassLava {
    const val EFFECT_NAME = "Bass Lava"

    val effectId: Int? get() = EffectCatalog.byName(EFFECT_NAME)?.id
}

/**
 * 频谱灯（固件灯效 `Spectrum Lamp`）的三频配色约定。
 *
 * 低频 / 中频 / 高频分别铺在外缘 / 中部 / 中心，颜色依次写入 `col[0..2]`；
 * 列宽由固件按百分比镜像分配，不依赖具体灯珠数量。
 */
object SpectrumLamp {
    const val EFFECT_NAME = "Spectrum Lamp"
    const val BAND_COUNT = 3

    /** 固件未收到颜色时的回落色：暖橙 / 薄荷 / 亮蓝。 */
    val DEFAULT_COLORS = listOf(0xFF4020, 0x20FF80, 0x4060FF)

    /** 三色模板沿用三频呼吸的模板表，避免维护第二套配色。 */
    val palettes: List<TriColorPalette> get() = TriBreath.palettes

    val effectId: Int? get() = EffectCatalog.byName(EFFECT_NAME)?.id

    /** 当前 segment 三个频段的颜色，顺序为低 -> 中 -> 高；未设置时按默认色展示。 */
    fun currentColors(segment: Segment?): List<Int> {
        val slots = segment?.colors.orEmpty()
        return List(BAND_COUNT) { band ->
            val packed = slots.getOrNull(band)?.packRgb() ?: 0
            if (packed == 0) DEFAULT_COLORS[band] else packed
        }
    }

    /** 颜色与某个模板完全一致时返回该模板，用于模板选择器的高亮。 */
    fun matchPalette(colors: List<Int>): TriColorPalette? =
        palettes.firstOrNull { it.colors == colors }
}
