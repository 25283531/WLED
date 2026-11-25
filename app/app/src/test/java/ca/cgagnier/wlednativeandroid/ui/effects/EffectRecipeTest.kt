package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 灯效 / 调色板 id 的反查与配方组装。
 *
 * 这组测试锁的是一个已经真实发生过的 bug：创作工坊把 Image 写死成旧效果表的 38、
 * 把 Scrolling Text 写死成旧效果表的 123，而固件调整效果槽位后这两个数字分别变成了
 * Aurora（极光）和 Drift Rose（玫瑰漂移）—— 上传的图案显示成极光、
 * 投送的字幕变成玫瑰漂移，不报错、极难联想到是 id 漂移。
 *
 * 效果表当前为 0..189，下面的断言值已按新表更新（Image=39、Scrolling Text=96、极光之舞=176）。
 *
 * 现在一律按固件英文名反查，测试就把「反查必须落到正确的槽位」钉死。
 */
class EffectRecipeTest {

    // ── 灯效 id 反查 ─────────────────────────────────────────────

    @Test
    fun byName_resolvesImageToItsRealSlotNotTheStaleOne() {
        val image = EffectCatalog.byName("Image")
        assertNotNull(image, "固件灯效目录里应有 Image")
        assertEquals(39, image!!.id)

        // 旧表的 38 曾经是 Image，槽位对齐后变成了 Aurora —— 两者必须分得开
        val aurora = EffectCatalog.byName("Aurora")
        assertNotNull(aurora)
        assertEquals(26, aurora!!.id)
        assertNotEquals(image.id, aurora.id)
    }

    @Test
    fun byName_resolvesScrollingTextToItsRealSlotNotTheStaleOne() {
        val scrolling = EffectCatalog.byName("Scrolling Text")
        assertNotNull(scrolling, "固件灯效目录里应有 Scrolling Text")
        assertEquals(96, scrolling!!.id)

        // 旧表的 123 曾经是 Scrolling Text，现在是 Drift Rose
        val driftRose = EffectCatalog.byName("Drift Rose")
        assertNotNull(driftRose)
        assertEquals(97, driftRose!!.id)
        assertNotEquals(scrolling.id, driftRose.id)
    }

    @Test
    fun byName_returnsNullForUnknownName() {
        assertNull(EffectCatalog.byName("No Such Effect 999"))
    }

    // ── 调色板 id 反查 ───────────────────────────────────────────

    @Test
    fun paletteByName_resolvesKnownPalettes() {
        assertEquals(0, PaletteCatalog.byName("Default")!!.id)
        assertEquals(6, PaletteCatalog.byName("Party")!!.id)
        assertEquals(9, PaletteCatalog.byName("Ocean")!!.id)
        assertEquals(35, PaletteCatalog.byName("Fire")!!.id)
        assertEquals(40, PaletteCatalog.byName("Magenta")!!.id)
        assertEquals(50, PaletteCatalog.byName("Aurora")!!.id)
    }

    @Test
    fun paletteByName_ignoresTheStarPrefix() {
        // 固件把 `*` 打在「只取当前颜色槽位」的特殊调色板前面，
        // `* Color 1` 就是界面上的 "Primary Color"。
        val canonical = PaletteCatalog.byName("Color 1")
        assertNotNull(canonical)
        assertEquals(2, canonical!!.id)
        assertEquals(canonical.id, PaletteCatalog.byName("* Color 1")!!.id)
        assertTrue(canonical.isStatic)
    }

    // ── 精选配方表 ───────────────────────────────────────────────

    @Test
    fun curatedEffects_allResolveToRealEffectAndPalette() {
        assertTrue(CuratedEffects.all.isNotEmpty(), "精选配方表不该是空的")
        CuratedEffects.all.forEach { curated ->
            assertNotNull(
                EffectCatalog.find(curated.effectId),
                "「${curated.title}」指向了不存在的灯效 id ${curated.effectId}",
            )
            assertNotNull(
                PaletteCatalog.find(curated.paletteId),
                "「${curated.title}」指向了不存在的调色板 id ${curated.paletteId}",
            )
            assertTrue(curated.speed in 0..255, "「${curated.title}」的速度越界")
            assertTrue(curated.intensity in 0..255, "「${curated.title}」的强度越界")
            assertTrue(curated.gradient.size >= 2, "「${curated.title}」的渐变至少要两个色")
        }
    }

    @Test
    fun curatedEffects_includeTheSignatureAuroraDanceWithItsFactoryParams() {
        val dance = EffectCatalog.byName("极光之舞")
        assertNotNull(dance, "固件里应有「极光之舞」")
        assertEquals(176, dance!!.id)

        val recipe = CuratedEffects.find(dance.id)
        assertNotNull(recipe, "招牌灯效应收录进精选配方表")
        assertEquals(115, recipe!!.speed)
        assertEquals(140, recipe!!.intensity)
    }

    // ── 配方组装优先级 ───────────────────────────────────────────

    @Test
    fun buildEffectSegment_appliesCuratedRecipeWhenNotFavorited() {
        val recipe = CuratedEffects.all.first()
        val segment = Segment(effect = 1, effectSpeed = 200, effectIntensity = 10, palette = 3)

        val result = buildEffectSegment(segment, recipe.effectId)

        assertEquals(recipe.effectId, result.effect)
        assertEquals(recipe.speed, result.effectSpeed)
        assertEquals(recipe.intensity, result.effectIntensity)
        assertEquals(recipe.paletteId, result.palette)
    }

    @Test
    fun buildEffectSegment_keepsDeviceValuesForUncuratedEffect() {
        val uncurated = EffectCatalog.all.first { CuratedEffects.find(it.id) == null }
        val segment = Segment(effect = 1, effectSpeed = 200, effectIntensity = 10, palette = 3)

        val result = buildEffectSegment(segment, uncurated.id)

        assertEquals(uncurated.id, result.effect)
        assertEquals(200, result.effectSpeed)
        assertEquals(10, result.effectIntensity)
        assertEquals(3, result.palette)
    }

    @Test
    fun buildEffectSegment_favoriteSnapshotOverridesCuratedRecipe() {
        val recipe = CuratedEffects.all.first()
        val favorite = FavoriteEffect(
            effectId = recipe.effectId,
            effectName = "手调过的一套",
            speed = 7,
            intensity = 8,
            palette = 9,
        )

        val result = buildEffectSegment(Segment(effectSpeed = 100), recipe.effectId, favorite)

        assertEquals(7, result.effectSpeed)
        assertEquals(8, result.effectIntensity)
        assertEquals(9, result.palette)
    }

    @Test
    fun buildEffectSegment_partialFavoriteOnlyOverridesWhatItStored() {
        val recipe = CuratedEffects.all.first()
        // 收藏时只记下了速度 —— 调色板和强度仍应走精选配方
        val favorite = FavoriteEffect(
            effectId = recipe.effectId,
            effectName = "只存了速度",
            speed = 7,
        )

        val result = buildEffectSegment(Segment(), recipe.effectId, favorite)

        assertEquals(7, result.effectSpeed)
        assertEquals(recipe.paletteId, result.palette)
        assertEquals(recipe.intensity, result.effectIntensity)
    }
}
