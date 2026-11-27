package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/**
 * 部分内置灯效对参数的读取方式特殊，设备页据此隐藏无效项、并在切效果时修正参数。
 *
 * 固件里的这些效果由上游 WLED 提供，参数语义固定：
 * - `Solid` 只读取当前颜色槽（`* Color 1`），速度/强度/调色板都不参与运算；
 * - `Blink` 的强度是**点亮时长占空比**（数值越大，一次明灭中亮着的时间越长），
 *   熄灭阶段是纯黑，所以强度是一个真实可调、效果直观的参数；
 * - `Colorloop` 通过 `color_wheel()` 取色，调色板为「单色」时整条灯变成该颜色的常亮，
 *   只有多色调色板才会循环；
 * - `Strobe Mega` 的强度是每次循环的频闪次数，单色调色板下固件以黑色作为熄灭相；
 * - `Chase` 的强度是追逐段宽度，单色调色板下固件以黑色铺底、用主色追逐；
 * - `Chase Flash Rnd` 的滚动像素由 `color_wheel()` 生成，调色板与强度都不参与运算；
 * - `Fireworks` 只用强度控制喷发频率，颜色由调色板决定，仅 2-5 号颜色槽调色板读取颜色槽。
 */
object EffectTuningPolicy {
    /** 固件「单色」动态调色板 id（`* Color 1`）。 */
    const val SOLID_PALETTE_ID = 2

    /** 强度滑杆的语义因灯效而异，UI 据此换用更贴切的文案。 */
    enum class IntensityLabel { DEFAULT, WIDTH, STROBE_COUNT, FIREWORK_RATE }

    private val solidId = EffectCatalog.byName("Solid")?.id
    private val blinkId = EffectCatalog.byName("Blink")?.id
    private val colorloopId = EffectCatalog.byName("Colorloop")?.id
    /** 音律溶液：完全由音频驱动，速度/强度不参与运算，只保留颜色。 */
    private val audioSolutionId = EffectCatalog.byName("Audio Solution")?.id
    /** 追逐：强度是追逐段的宽度（固件 `Width`）。 */
    private val chaseId = EffectCatalog.byName("Chase")?.id
    /** 疾速频闪：强度是每次循环的频闪次数（固件 `count`）。 */
    private val strobeMegaId = EffectCatalog.byName("Strobe Mega")?.id
    /** 乱序追闪：不读取调色板，也不读取强度。 */
    private val chaseFlashRndId = EffectCatalog.byName("Chase Flash Rnd")?.id
    /** 焰火：不读取速度；颜色只由调色板决定，仅 2-5 号颜色槽调色板读取颜色槽。 */
    private val fireworksId = EffectCatalog.byName("Fireworks")?.id
    private val rainbowPaletteId = PaletteCatalog.byName("Rainbow")?.id ?: 11

    fun isSolid(effectId: Int?): Boolean = effectId != null && effectId == solidId

    fun isBlink(effectId: Int?): Boolean = effectId != null && effectId == blinkId

    fun isColorloop(effectId: Int?): Boolean = effectId != null && effectId == colorloopId

    /** 纯色不读取速度；音律溶液只读音频；焰火只用强度控制喷发频率。 */
    fun hidesSpeed(effectId: Int?): Boolean =
        isSolid(effectId) ||
            (effectId != null && effectId == audioSolutionId) ||
            (effectId != null && effectId == fireworksId)

    /** 纯色不读取强度；明灭的强度是点亮时长，保留可调；音律溶液只读音频；乱序追闪不使用强度。 */
    fun hidesIntensity(effectId: Int?): Boolean =
        isSolid(effectId) ||
            (effectId != null && effectId == audioSolutionId) ||
            (effectId != null && effectId == chaseFlashRndId)

    /**
     * 纯色不需要调色板，颜色由上方色板决定。
     *
     * 乱序追闪的滚动像素用 `color_wheel()` 取色，调色板完全不参与运算，
     * 留着一个点了没反应的调色板只会误导，因此整个选择器都隐藏。
     */
    fun hidesPalette(effectId: Int?): Boolean =
        isSolid(effectId) || (effectId != null && effectId == chaseFlashRndId)

    /**
     * 焰火等效果的颜色完全由调色板决定；只有固件 2-5 号「由颜色槽拼出」的
     * 动态调色板才会读取颜色槽，其余模板（含默认 Rainbow）下改颜色不会有任何效果，
     * UI 据此隐藏色块行。
     */
    fun colorsOnlyViaPalette(effectId: Int?): Boolean =
        effectId != null && effectId == fireworksId

    /** 强度滑杆文案。 */
    fun intensityLabel(effectId: Int?): IntensityLabel = when {
        effectId == null -> IntensityLabel.DEFAULT
        effectId == chaseId -> IntensityLabel.WIDTH
        effectId == strobeMegaId -> IntensityLabel.STROBE_COUNT
        effectId == fireworksId -> IntensityLabel.FIREWORK_RATE
        else -> IntensityLabel.DEFAULT
    }

    /** 循环彩选「单色」会变成常亮，屏蔽该模板。 */
    fun hidesSolidPalette(effectId: Int?): Boolean = isColorloop(effectId)

    /**
     * 切到灯效后修正无效参数，避免「点了没反应」：
     * - 循环彩若当前是单色模板，自动切到彩虹，恢复循环变色。
     *
     * 纯色不受影响：固件 `mode_static()` 直接取 `col[0]`，与调色板无关。
     * 明灭不受影响：强度由用户自行调节点亮时长。
     * 在收藏快照恢复之后调用，确保这些约束不会被历史收藏破坏。
     */
    fun adjust(segment: Segment, effectId: Int): Segment {
        var result = segment
        if (effectId == colorloopId && result.palette == SOLID_PALETTE_ID) {
            result = result.copy(palette = rainbowPaletteId)
        }
        return result
    }
}
