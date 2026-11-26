package ca.cgagnier.wlednativeandroid.ui.effects

/**
 * 一条精选灯效配方。
 *
 * 这是「用户点了会得到什么」的完整答案：不只是一个灯效 id，还包括该灯效
 * 跑起来最好看的调色板、速度、强度。灯效库里点一下就按这套配方下发，
 * 用户不用先学会「先切灯效、再调调色板、再调速度」这一串操作。
 *
 * @property effectId 固件灯效 id（运行时 `seg.fx` 值）。
 * @property paletteId 固件调色板 id（运行时 `seg.pal` 值）。
 * @property speed 推荐速度，0..255。
 * @property intensity 推荐强度，0..255。
 * @property title 产品名（中文），比固件技术名更好懂。
 * @property tagline 英文副标题。
 * @property gradient 卡片预览渐变色，`0xRRGGBB`，取自灯效实际用色。
 */
data class CuratedEffect(
    val effectId: Int,
    val paletteId: Int,
    val speed: Int,
    val intensity: Int,
    val title: String,
    val tagline: String,
    val gradient: List<Int>,
)

/**
 * 精选灯效配方表。
 *
 * 只收录**少数几个**灯效，而不是给全部 117 个灯效都编一套参数：
 * 参数编多了没有依据，反倒会误导（用户点「壁炉篝火」时不该担心它跑成什么颜色）。
 * 收录标准是 —— 效果本身有代表性、参数调出来确实好看、覆盖四个 mood。
 *
 * ## 为什么按名字引用而不是写 id
 *
 * 固件的灯效槽位和调色板数组都会随版本调整（加一个灯效，后面所有 id 整体前移）。
 * 写死的 id 会**静默**指向另一个效果 —— 不报错，只是画面悄悄变了，
 * 排查起来极其困难。这里一律用固件的英文名在运行时反查，槽位变了名字不变，行为自动跟随。
 *
 * 反查失败（固件改名或删了该效果）时该条会被跳过并打日志，不会让整个 App 崩掉。
 */
object CuratedEffects {

    /**
     * 配方声明。用固件的**英文名**指定灯效与调色板，运行时反查 id。
     */
    private data class Spec(
        val effectName: String,
        val paletteName: String,
        val speed: Int,
        val intensity: Int,
        val title: String,
        val tagline: String,
        val gradient: List<Int>,
    )

    private val specs = listOf(
        // ── 幻彩极光 ──────────────────────────────────────────────
        Spec(
            effectName = "Polar Lights",
            paletteName = "Aurora",
            speed = 95,
            intensity = 150,
            title = "极地漫游",
            tagline = "Arctic Tour",
            gradient = listOf(0x1DE9B6, 0x651FFF),
        ),
        Spec(
            effectName = "Twinklefox",
            paletteName = "Random Cycle",
            speed = 70,
            intensity = 100,
            title = "星河微光",
            tagline = "Starlight",
            gradient = listOf(0x311B92, 0x80DEEA),
        ),

        // 光屿的招牌灯效：固件自带的「极光之舞」（按英文名反查，槽位变化自动跟随）。
        // 调色板给 Aurora 只是因为它自带完整渐变、不依赖调色板，留一个不冲突的默认值。
        Spec(
            effectName = "极光之舞",
            paletteName = "Aurora",
            speed = 115,
            intensity = 140,
            title = "极光之舞",
            tagline = "Aurora Dance",
            gradient = listOf(0x00F5FF, 0xAF28FF),
        ),

        // ── 动态字幕 ──────────────────────────────────────────────
        Spec(
            effectName = "Scrolling Text",
            paletteName = "Default",
            speed = 120,
            intensity = 128,
            title = "极客弹幕",
            tagline = "Scrolling Text",
            gradient = listOf(0x00E676, 0x00B0FF),
        ),

        // ── 温馨慢节奏 ────────────────────────────────────────────
        Spec(
            effectName = "Breathe",
            paletteName = "Color 1",
            speed = 45,
            intensity = 60,
            title = "暖阳呼吸",
            tagline = "Warm Breath",
            gradient = listOf(0xFF8A65, 0x4E342E),
        ),
    )

    /** 解析后的配方表。id 反查失败的条目会被剔除。 */
    val all: List<CuratedEffect> = specs.mapNotNull { it.resolve() }

    private val byEffectId: Map<Int, CuratedEffect> = all.associateBy { it.effectId }

    /** 按灯效 id 找配方；没有收录则返回 null（调用方按「沿用设备当前值」处理）。 */
    fun find(effectId: Int): CuratedEffect? = byEffectId[effectId]

    private fun Spec.resolve(): CuratedEffect? {
        val effect = EffectCatalog.byName(effectName)
        if (effect == null) {
            // 用 println 而不是 android.util.Log：这个 object 的初始化会发生在
            // JVM 单元测试里，android.util.Log 在未 mock 的测试环境中会直接抛异常。
            println("[CuratedEffects] 固件灯效目录里找不到「$effectName」，已跳过该精选配方")
            return null
        }
        val palette = PaletteCatalog.byName(paletteName)
        if (palette == null) {
            println("[CuratedEffects] 固件调色板目录里找不到「$paletteName」，已跳过「$title」")
            return null
        }
        return CuratedEffect(
            effectId = effect.id,
            paletteId = palette.id,
            speed = speed.coerceIn(0, 255),
            intensity = intensity.coerceIn(0, 255),
            title = title,
            tagline = tagline,
            gradient = gradient,
        )
    }
}
