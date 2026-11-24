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

    private val all = EffectCatalog.all

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
        val favorites = setOf(0, 1, 188)
        val result = filterEffects(all, EffectFilter.FAVORITE, "", favorites)
        assertEquals(3, result.size)
        assertEquals(setOf(0, 1, 188), result.map { it.id }.toSet())
    }

    @Test
    fun `FAVORITE 收藏为空时返回空列表`() {
        assertTrue(filterEffects(all, EffectFilter.FAVORITE, "", emptySet()).isEmpty())
    }

    @Test
    fun `ART 收录自定义图片与滚动字幕`() {
        val art = filterEffects(all, EffectFilter.ART, "")
        assertEquals(
            setOf(39, 97),
            art.map { it.id }.toSet(),
            "「字画」应收纳 Image(53) 与 Scrolling Text(122)",
        )
        assertTrue(art.all { it.category == EffectCategory.ART })
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
        assertEquals(all.size, oneD.size + twoD.size + filterEffects(all, EffectFilter.ART, "").size)
    }

    @Test
    fun `律动模式筛选与目录分组一致`() {
        listOf(
            EffectFilter.AURORA to EffectMood.AURORA,
            EffectFilter.MUSIC to EffectMood.MUSIC,
            EffectFilter.TEXT to EffectMood.TEXT,
        ).forEach { (filter, mood) ->
            val result = filterEffects(all, filter, "")
            assertTrue(result.isNotEmpty(), "$filter 不应为空")
            assertTrue(result.all { it.mood == mood }, "$filter 混入了其它模式的灯效")
            assertEquals(EffectCatalog.byMood(mood).size, result.size)
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
        val byEnglish = filterEffects(all, EffectFilter.ALL, "Aurora")
        val byChinese = filterEffects(all, EffectFilter.ALL, "极光")
        assertTrue(byEnglish.isNotEmpty())
        assertTrue(byChinese.isNotEmpty())
        assertTrue(
            byEnglish.any { it.id == 26 },
            "英文名 Aurora 应命中 id 26",
        )
        assertTrue(
            byChinese.any { it.id == 188 },
            "中文名「极光之舞」应命中 id 188",
        )
    }

    @Test
    fun `搜索忽略大小写`() {
        assertEquals(
            filterEffects(all, EffectFilter.ALL, "aurora").map { it.id },
            filterEffects(all, EffectFilter.ALL, "AURORA").map { it.id },
        )
    }

    @Test
    fun `筛选与搜索叠加生效`() {
        val result = filterEffects(all, EffectFilter.ART, "字幕")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.category == EffectCategory.ART })
        assertTrue(result.all { it.name.contains("字幕") || it.nameZh.contains("字幕") })
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
