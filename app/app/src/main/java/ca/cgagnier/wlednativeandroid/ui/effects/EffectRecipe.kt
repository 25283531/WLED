package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.restoreFrom
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment

/**
 * 组装「点一下灯效」要下发到设备的段落。
 *
 * 参数优先级，从高到低：
 *
 * 1. **收藏快照** —— 用户自己调过并存下来的那套参数，最权威。
 * 2. **精选配方** —— 该灯效在 [CuratedEffects] 里有推荐参数时套用。
 * 3. **设备当前值** —— 以上都没有时原样保留，不动用户已经调好的参数。
 *
 * 顺序不能反：先套精选、再让收藏快照覆盖。这样「收藏过但当时没记下速度」的灯效，
 * 既保住了收藏时的观感（颜色/调色板），又不会把精选的速度顶掉。
 *
 * 抽成函数是为了让首页快捷卡片和灯效库点卡片走同一套逻辑 —— 两处以前各写各的，
 * 很容易出现「首页能恢复参数、灯效库不能」这种不一致。
 */
fun buildEffectSegment(
    segment: Segment,
    effectId: Int,
    favorite: FavoriteEffect? = null,
): Segment {
    var result = segment.copy(effect = effectId)
    // 舞台灯的频段掩码与配色存放在 c1 / col[0..2]，其它效果也会占用这些字段；
    // 从其它效果切到舞台灯时恢复默认观感（只关联低频、白色光），
    // 同一效果内的调整保持不变，收藏快照仍会覆盖颜色。
    val stageLightId = StageLight.effectId
    if (stageLightId != null && effectId == stageLightId && segment.effect != stageLightId) {
        result = result.copy(
            custom1 = StageLight.DEFAULT_MASK,
            custom2 = 0,
            custom3 = 0,
            colors = List(StageLight.BAND_COUNT) { listOf(255, 255, 255) },
        )
    }
    CuratedEffects.find(effectId)?.let { curated ->
        result = result.copy(
            effectSpeed = curated.speed,
            effectIntensity = curated.intensity,
            palette = curated.paletteId,
        )
    }
    return result.restoreFrom(favorite)
}
