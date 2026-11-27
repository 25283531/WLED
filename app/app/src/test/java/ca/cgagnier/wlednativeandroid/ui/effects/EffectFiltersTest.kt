package ca.cgagnier.wlednativeandroid.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 灯效库筛选逻辑的测试。
 *
 * 用真实的 [EffectCatalog] 而不是造样本数据 —— 这样断言的是线上真实行为
 * （比如「字画」到底收了哪几个 id），样本数据会随目录更新而失效。
 */
class EffectFiltersTest {

    // 灯效库只展示可见项，Image / Scrolling Text 已改为创作页投送，不再出现在库里
    private val all = EffectCatalog.visible

    @Test
    fun `ALL 返回完整目录`() {
        assertEquals(all.size, filterEffects(all, EffectFilter.ALL, "").size)
        assertEquals(all.size, filterEffects(all, EffectFilter.ALL, "   ").size)
    }

    @Test
    fun `POPULAR 只返回常用灯效`() {
        val popular = filterEffects(all, EffectFilter.POPULAR, "")
        assertTrue(popular.isNotEmpty())
        assertTrue(popular.all { it.isPopular })
    }

    @Test
    fun `FAVORITE 只返回收藏中的灯效`() {
        val favorites = setOf(0, 1, all.last().id)
        val result = filterEffects(all, EffectFilter.FAVORITE, "", favorites)
        assertEquals(3, result.size)
        assertEquals(favorites, result.map { it.id }.toSet())
    }

    @Test
    fun `FAVORITE 收藏为空时返回空列表`() {
        assertTrue(filterEffects(all, EffectFilter.FAVORITE, "", emptySet()).isEmpty())
    }

    @Test
    fun `ONE_D 与 TWO_D 按固件效果类型划分且互斥`() {
        val oneD = filterEffects(all, EffectFilter.ONE_D, "")
        val twoD = filterEffects(all, EffectFilter.TWO_D, "")
        assertTrue(oneD.isNotEmpty())
        assertTrue(twoD.isNotEmpty())
        assertTrue(
            oneD.none { it.id in twoD.map { t -> t.id } },
            "1D 与 2D 不应有交集",
        )
        assertEquals(all.size, oneD.size + twoD.size)
    }

    @Test
    fun `律动模式筛选与目录分组一致`() {
        listOf(
            EffectFilter.AURORA to EffectMood.AURORA,
            EffectFilter.MUSIC to EffectMood.MUSIC,
        ).forEach { (filter, mood) ->
            val result = filterEffects(all, filter, "")
            assertTrue(result.isNotEmpty(), "$filter 不应为空")
            assertTrue(result.all { it.mood == mood }, "$filter 混入了其它模式的灯效")
            assertEquals(EffectCatalog.byMood(mood).size, result.size)
        }
    }

    @Test
    fun `数字雨归入幻彩极光且隐藏在库外`() {
        val matrix = EffectCatalog.byName("Matrix")
        assertEquals(EffectMood.AURORA, matrix?.mood, "数字雨应归入幻彩极光")
        assertTrue(filterEffects(all, EffectFilter.AURORA, "").any { it.name == "Matrix" })

        // 自定义图片/动态字幕仍可按名反查 id（创作页投送要用），但不再出现在灯效库
        listOf("Image", "Scrolling Text").forEach { name ->
            val effect = EffectCatalog.byName(name)
            assertTrue(effect != null && effect.hidden, "$name 应保留 id 映射并标记隐藏")
            assertTrue(all.none { it.name == name }, "$name 不应出现在灯效库可见列表中")
        }
    }

    @Test
    fun `睡眠夜灯不再是灯效分类`() {
        // 「睡眠夜灯」已改为设备页的一键暖光动作，不应再出现在筛选/分类里
        assertTrue(EffectFilter.entries.none { it.name == "SLEEP" })
        assertTrue(EffectMood.entries.none { it.name == "SLEEP" })
        assertTrue(EffectCatalog.byName("Solid") != null, "睡眠暖光依赖纯色灯效")
    }

    @Test
    fun `搜索同时匹配英文名与中文名`() {
        val byEnglish = filterEffects(all, EffectFilter.ALL, "Rainbow")
        val byChinese = filterEffects(all, EffectFilter.ALL, "极光")
        assertTrue(byEnglish.isNotEmpty())
        assertTrue(byChinese.isNotEmpty())
        assertTrue(
            byEnglish.any { it.name == "Rainbow" },
            "英文名 Rainbow 应命中对应灯效",
        )
        assertTrue(
            byChinese.any { it.name == "极光之舞" },
            "中文名「极光之舞」应命中对应灯效",
        )
    }

    @Test
    fun `搜索忽略大小写`() {
        val lower = filterEffects(all, EffectFilter.ALL, "rainbow").map { it.id }
        assertEquals(lower, filterEffects(all, EffectFilter.ALL, "RAINBOW").map { it.id })
        assertTrue(lower.isNotEmpty())
    }

    @Test
    fun `筛选与搜索叠加生效`() {
        val result = filterEffects(all, EffectFilter.AURORA, "数字雨")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.mood == EffectMood.AURORA })
        assertTrue(result.all { it.name.contains("数字雨") || it.nameZh.contains("数字雨") })
    }

    @Test
    fun `搜索无命中时返回空列表`() {
        assertTrue(filterEffects(all, EffectFilter.ALL, "不存在的灯效名字xyz").isEmpty())
    }

    @Test
    fun `每个灯效的 id 与固件槽位一致`() {
        // 目录里不允许出现重复 id，否则下发 seg.fx 会打到错误槽位
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "灯效目录出现重复 id")
        assertFalse(ids.contains(-1), "灯效 id 不能为负")
    }
}
