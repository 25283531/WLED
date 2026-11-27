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
 *   只有多色调色板才会循环。
 */
object EffectTuningPolicy {
    /** 固件「单色」动态调色板 id（`* Color 1`）。 */
    const val SOLID_PALETTE_ID = 2

    private val solidId = EffectCatalog.byName("Solid")?.id
    private val blinkId = EffectCatalog.byName("Blink")?.id
    private val colorloopId = EffectCatalog.byName("Colorloop")?.id
    /** 音律溶液：完全由音频驱动，速度/强度不参与运算，只保留颜色。 */
    private val audioSolutionId = EffectCatalog.byName("Audio Solution")?.id
    private val rainbowPaletteId = PaletteCatalog.byName("Rainbow")?.id ?: 11

    fun isSolid(effectId: Int?): Boolean = effectId != null && effectId == solidId

    fun isBlink(effectId: Int?): Boolean = effectId != null && effectId == blinkId

    fun isColorloop(effectId: Int?): Boolean = effectId != null && effectId == colorloopId

    /** 纯色不读取速度；音律溶液只读音频。 */
    fun hidesSpeed(effectId: Int?): Boolean =
        isSolid(effectId) || (effectId != null && effectId == audioSolutionId)

    /** 纯色不读取强度；明灭的强度是点亮时长，保留可调；音律溶液只读音频。 */
    fun hidesIntensity(effectId: Int?): Boolean =
        isSolid(effectId) || (effectId != null && effectId == audioSolutionId)

    /** 纯色不需要调色板，颜色由上方色板决定。 */
    fun hidesPalette(effectId: Int?): Boolean = isSolid(effectId)

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
