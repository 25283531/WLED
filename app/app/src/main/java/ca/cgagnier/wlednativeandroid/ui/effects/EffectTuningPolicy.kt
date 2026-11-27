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
 * - `Fireworks` 只用强度控制喷发频率，颜色由调色板决定，仅 2-5 号颜色槽调色板读取颜色槽；
 * - `Colorful` 固定 4 段颜色，强度改为连续的柔和度；单色调色板用移动的亮度坡度取代表现；
 * - `Fairy` 固定白底绿点，强度是绿点数量、速度是闪烁速度，调色板与颜色槽都不参与；
 * - `Colorwaves` 在默认（0 号）与单色调色板下只会输出单色亮度波，隐藏这两类模板；
 * - `Fireworks Starburst` 的星点由 `color_wheel()` 取色、背景取第二颜色槽，主色槽不起作用；
 * - `Copy Segment` 的色相偏移按 0..255 全量映射，滑杆有效区间仅前 10%，需要缩小通道映射；
 *   它完全不读取速度，速度滑杆直接隐藏；
 * - `Rolling Balls`（逐光者）的强度是球数，`Railway`（平行线）的强度是往返平滑度；
 * - `Sunrise`（光影之舞）的速度是日出/日落时长（分钟），强度是光晕宽度；
 * - `Scrolling Text` 的速度是滚动速度、强度是纵向位置，选中后引导到创作页做图文叠加。
 */
object EffectTuningPolicy {
    /** 固件「单色」动态调色板 id（`* Color 1`）。 */
    const val SOLID_PALETTE_ID = 2

    /** 固件「默认」调色板 id（`Default`，取当前颜色槽 1，Colorwaves 下不随模板变化）。 */
    const val DEFAULT_PALETTE_ID = 0

    /** 强度滑杆的语义因灯效而异，UI 据此换用更贴切的文案。 */
    enum class IntensityLabel {
        DEFAULT, WIDTH, STROBE_COUNT, FIREWORK_RATE, SOFTNESS, DOT_COUNT, TEXT_Y_OFFSET,
        SMOOTHNESS, FRAGMENTS, HUE_SHIFT, BALL_COUNT,
    }

    /** 速度滑杆的语义因灯效而异（闪烁节奏 / 滚动速度 / 日出时长 / 出现概率）。 */
    enum class SpeedLabel { DEFAULT, BLINK_RATE, SCROLL_SPEED, DURATION_MIN, SPAWN_CHANCE }

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
    /** 缤纷：强度是连续柔和度。 */
    private val colorfulId = EffectCatalog.byName("Colorful")?.id
    /** 绿野迷踪：固定白底绿点，强度=绿点数量，速度=闪烁速度。 */
    private val fairyId = EffectCatalog.byName("Fairy")?.id
    /** 色浪：默认与单色调色板只输出单色亮度波。 */
    private val colorwavesId = EffectCatalog.byName("Colorwaves")?.id
    /** 星爆焰火：主色槽不参与，星点颜色由调色板决定。 */
    private val starburstId = EffectCatalog.byName("Fireworks Starburst")?.id
    /** 动态字幕：强度是纵向位置。 */
    private val scrollTextId = EffectCatalog.byName("Scrolling Text")?.id
    /** 复制段落：色相偏移通道有效区间只有前 10%。 */
    private val copySegmentId = EffectCatalog.byName("Copy Segment")?.id
    /** 逐光者（原滚球）：强度是球的数量。 */
    private val rollingBallsId = EffectCatalog.byName("Rolling Balls")?.id
    /** 平行线（原铁道）：强度是往返过渡的平滑度。 */
    private val railwayId = EffectCatalog.byName("Railway")?.id
    /** 光影之舞（原日出）：速度是日出/日落时长（分钟）。 */
    private val sunriseId = EffectCatalog.byName("Sunrise")?.id
    private val rainbowPaletteId = PaletteCatalog.byName("Rainbow")?.id ?: 11

    fun isSolid(effectId: Int?): Boolean = effectId != null && effectId == solidId

    fun isBlink(effectId: Int?): Boolean = effectId != null && effectId == blinkId

    fun isColorloop(effectId: Int?): Boolean = effectId != null && effectId == colorloopId

    /** 纯色不读取速度；音律溶液只读音频；焰火只用强度控制喷发频率；复制段落完全不读取速度。 */
    fun hidesSpeed(effectId: Int?): Boolean =
        isSolid(effectId) ||
            (effectId != null && effectId == audioSolutionId) ||
            (effectId != null && effectId == fireworksId) ||
            (effectId != null && effectId == copySegmentId)

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
        isSolid(effectId) ||
            (effectId != null && effectId == chaseFlashRndId) ||
            (effectId != null && effectId == fairyId)

    /**
     * 绿野迷踪固定白色底 + 绿色光点；星爆焰火的星点颜色由调色板决定、背景取第二颜色槽，
     * 主色槽（色块行修改的就是它）不参与运算，隐藏以免误导。
     */
    fun hidesColorRow(effectId: Int?): Boolean =
        effectId != null && (effectId == fairyId || effectId == starburstId)

    /**
     * 焰火等效果的颜色完全由调色板决定；只有固件 2-5 号「由颜色槽拼出」的
     * 动态调色板才会读取颜色槽，其余模板（含默认 Rainbow）下改颜色不会有任何效果，
     * UI 据此隐藏色块行。
     */
    fun colorsOnlyViaPalette(effectId: Int?): Boolean =
        effectId != null &&
            (effectId == fireworksId || effectId == sunriseId || effectId == colorwavesId)

    /** 强度滑杆文案。 */
    fun intensityLabel(effectId: Int?): IntensityLabel = when {
        effectId == null -> IntensityLabel.DEFAULT
        effectId == chaseId -> IntensityLabel.WIDTH
        effectId == strobeMegaId -> IntensityLabel.STROBE_COUNT
        effectId == fireworksId -> IntensityLabel.FIREWORK_RATE
        effectId == colorfulId -> IntensityLabel.SOFTNESS
        effectId == fairyId -> IntensityLabel.DOT_COUNT
        effectId == scrollTextId -> IntensityLabel.TEXT_Y_OFFSET
        effectId == copySegmentId -> IntensityLabel.HUE_SHIFT
        effectId == colorwavesId -> IntensityLabel.HUE_SHIFT
        effectId == railwayId -> IntensityLabel.SMOOTHNESS
        effectId == starburstId -> IntensityLabel.FRAGMENTS
        effectId == rollingBallsId -> IntensityLabel.BALL_COUNT
        effectId == sunriseId -> IntensityLabel.WIDTH
        else -> IntensityLabel.DEFAULT
    }

    /** 速度滑杆文案。 */
    fun speedLabel(effectId: Int?): SpeedLabel = when {
        effectId == null -> SpeedLabel.DEFAULT
        effectId == fairyId -> SpeedLabel.BLINK_RATE
        effectId == scrollTextId -> SpeedLabel.SCROLL_SPEED
        effectId == sunriseId -> SpeedLabel.DURATION_MIN
        effectId == starburstId -> SpeedLabel.SPAWN_CHANCE
        else -> SpeedLabel.DEFAULT
    }

    /**
     * 循环彩选「单色」会变成常亮；色浪在「默认」与「单色」下只输出单色亮度波，
     * 都屏蔽对应模板。
     */
    fun hidesSolidPalette(effectId: Int?): Boolean =
        isColorloop(effectId) || (effectId != null && effectId == colorwavesId)

    /**
     * 色浪的「默认」调色板只取颜色槽 1，不随模板变化，隐藏；
     * 返回空集合表示不做额外过滤。
     */
    fun hiddenPaletteIds(effectId: Int?): Set<Int> =
        if (effectId != null && effectId == colorwavesId) setOf(DEFAULT_PALETTE_ID) else emptySet()

    /**
     * 滑杆百分比 → 通道值的缩放系数。
     *
     * 复制段落的色相偏移把滑杆 0..100% 直接映射到 0..255，实际 3% 时颜色已经偏移过度，
     * 因此把通道压缩到原来的 10%（原先 10% 的位置成为现在的 100%）。
     */
    fun sliderScale(effectId: Int?): Float =
        if (effectId != null && effectId == copySegmentId) 0.1f else 1f

    fun isScrollingText(effectId: Int?): Boolean =
        effectId != null && effectId == scrollTextId

    /** 从通道值还原滑杆百分比（考虑缩放），用于滑杆初始化。 */
    fun channelToSlider(effectId: Int?, channel: Int): Float {
        val scale = sliderScale(effectId)
        val raw = if (scale <= 0f) 0 else (channel / scale).toInt().coerceIn(0, 255)
        return raw / 255f
    }

    /** 滑杆百分比 → 实际下发的通道值（考虑缩放）。 */
    fun sliderToChannel(effectId: Int?, slider: Float): Int {
        val scale = sliderScale(effectId)
        return (slider.coerceIn(0f, 1f) * 255f * scale).toInt().coerceIn(0, 255)
    }

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
        // 色浪在默认/单色调色板下只输出单色亮度波，自动切到彩虹以恢复多彩色浪。
        if (effectId == colorwavesId &&
            (result.palette == SOLID_PALETTE_ID || result.palette == DEFAULT_PALETTE_ID)
        ) {
            result = result.copy(palette = rainbowPaletteId)
        }
        return result
    }
}
