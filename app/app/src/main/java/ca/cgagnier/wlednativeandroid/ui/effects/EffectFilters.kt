package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/**
 * 灯效库的筛选项。
 *
 * 扁平的一维筛选：任选其一即生效，不做多维交叉。
 * 之所以不做「模式 × 维度」的交叉，是因为两者会大量落空
 * （例如「幻彩极光」里没有「字画」，交叉后是空列表），
 * 与其在界面上解释这个规则，不如让用户一次选一个意图。
 *
 * [ONE_D] / [TWO_D] 按固件效果类型区分 1D 与 2D；[ART] 是能直接显示用户内容的字画子集。
 */
enum class EffectFilter {
    ALL,
    POPULAR,
    FAVORITE,
    ART,
    ONE_D,
    TWO_D,
    AURORA,
    MUSIC,
    TEXT,
    SLEEP,
}

/**
 * 把固件灯效目录与筛选条件组合起来。
 *
 * 抽成纯函数，方便单测，也让界面只关心「给我当前该显示哪些灯效」。
 *
 * @param favoriteIds 已收藏的灯效 id，用于 [EffectFilter.FAVORITE]。
 */
fun filterEffects(
    effects: List<EffectInfo>,
    filter: EffectFilter,
    query: String,
    favoriteIds: Set<Int> = emptySet(),
): List<EffectInfo> {
    val trimmed = query.trim()
    val byFilter = when (filter) {
        EffectFilter.ALL -> effects
        EffectFilter.POPULAR -> effects.filter { it.isPopular }
        EffectFilter.FAVORITE -> effects.filter { it.id in favoriteIds }
        EffectFilter.ART -> effects.filter { it.category == EffectCategory.ART }
        EffectFilter.ONE_D -> effects.filter { it.category == EffectCategory.ONE_D }
        EffectFilter.TWO_D -> effects.filter { it.category == EffectCategory.TWO_D }
        EffectFilter.AURORA -> effects.filter { it.mood == EffectMood.AURORA }
        EffectFilter.MUSIC -> effects.filter { it.mood == EffectMood.MUSIC }
        EffectFilter.TEXT -> effects.filter { it.mood == EffectMood.TEXT }
        EffectFilter.SLEEP -> effects.filter { it.mood == EffectMood.SLEEP }
    }
    if (trimmed.isEmpty()) return byFilter
    return byFilter.filter {
        it.name.contains(trimmed, ignoreCase = true) ||
            it.nameZh.contains(trimmed, ignoreCase = true)
    }
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
