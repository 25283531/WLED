package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/** 灯效库的筛选项。 */
enum class EffectFilter {
    ALL,
    POPULAR,
    ONE_D,
    TWO_D,
    ART,
}

/**
 * 把固件灯效目录与筛选条件组合起来。
 *
 * 抽成纯函数，方便单测，也让界面只关心「给我当前该显示哪些灯效」。
 */
fun filterEffects(
    effects: List<EffectInfo>,
    filter: EffectFilter,
    query: String,
): List<EffectInfo> {
    val trimmed = query.trim()
    val byFilter = when (filter) {
        EffectFilter.ALL -> effects
        EffectFilter.POPULAR -> effects.filter { it.isPopular }
        EffectFilter.ONE_D -> effects.filter { it.category == EffectCategory.ONE_D }
        EffectFilter.TWO_D -> effects.filter { it.category == EffectCategory.TWO_D }
        EffectFilter.ART -> effects.filter { it.category == EffectCategory.ART }
    }
    if (trimmed.isEmpty()) return byFilter
    return byFilter.filter { it.name.contains(trimmed, ignoreCase = true) }
}

/** RGB 分量列表（WLED `col` 的一项）压缩成 `0xRRGGBB`。 */
fun List<Int>.packRgb(): Int? {
    if (size < RGB_COMPONENT_COUNT) return null
    return packRgb(this[0], this[1], this[2])
}

/** 三个分量压缩成 `0xRRGGBB`。 */
fun packRgb(red: Int, green: Int, blue: Int): Int =
    (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

/** `0xRRGGBB` 拆回 WLED `col` 需要的 `[r, g, b]`。 */
fun Int.unpackRgb(): List<Int> =
    listOf((this shr 16) and 0xFF, (this shr 8) and 0xFF, this and 0xFF)

/** 该段落当前是否正在使用 [effectId]。 */
fun Segment.isUsingEffect(effectId: Int): Boolean = effect == effectId

private const val RGB_COMPONENT_COUNT = 3
