// 由 tools/gen_effect_catalog.py 自动生成，请勿手工编辑。
// 数据源: firmware/effects_enabled.json（idx 即 WLED 运行时 fx 值）
package ca.cgagnier.wlednativeandroid.ui.effects

/** 灯效分类。1D 与 2D 由固件效果类型决定，「字画」是能直接显示用户内容的效果子集。 */
enum class EffectCategory { ONE_D, TWO_D, ART }

/**
 * 律动模式：App 侧的产品分类，对应设备页的律动胶囊与灯效库的 Tab。
 *
 * 固件没有这个概念，分类由 [EffectCatalog] 的 id 白名单决定。
 */
enum class EffectMood { AURORA, MUSIC, TEXT }

/**
 * 一个固件灯效。
 *
 * @property id 运行时 fx 值，可直接写入 `seg.fx`。
 * @property name 固件提供的英文名称（`/json/effects` 同一份数据，此处固化以便离线展示）。
 * @property nameZh 中文显示名；未收录时与 [name] 相同。
 * @property category 见 [EffectCategory]。
 * @property mood 律动胶囊的分组，见 [EffectMood]。
 * @property isPopular 是否列入「常用」。
 * @property preview 预览 GIF 的资源名（assets/effect_previews/<preview>.gif），即效果 id。
 */
data class EffectInfo(
    val id: Int,
    val name: String,
    val nameZh: String,
    val category: EffectCategory,
    val mood: EffectMood,
    val isPopular: Boolean,
    val preview: String?,
) {
    val hasPreview: Boolean get() = preview != null

    /** 有中文名时优先显示中文，英文名作为副标题。 */
    val hasChineseName: Boolean get() = nameZh != name
}

object EffectCatalog {
    val all: List<EffectInfo> = listOf(
        EffectInfo(0, "Solid", "纯色", EffectCategory.ONE_D, EffectMood.AURORA, false, "0"),
        EffectInfo(1, "Blink", "明灭", EffectCategory.ONE_D, EffectMood.AURORA, true, "1"),
        EffectInfo(2, "Breathe", "呼吸", EffectCategory.ONE_D, EffectMood.AURORA, true, "2"),
        EffectInfo(3, "Colorloop", "循环彩", EffectCategory.ONE_D, EffectMood.AURORA, true, "3"),
        EffectInfo(4, "Rainbow", "七彩流光", EffectCategory.ONE_D, EffectMood.AURORA, true, "4"),
        EffectInfo(5, "Fade", "渐隐", EffectCategory.ONE_D, EffectMood.AURORA, false, "5"),
        EffectInfo(6, "Theater", "剧院追光", EffectCategory.ONE_D, EffectMood.AURORA, false, "6"),
        EffectInfo(7, "Running", "奔走", EffectCategory.ONE_D, EffectMood.AURORA, false, "7"),
        EffectInfo(8, "Dissolve", "溶解", EffectCategory.ONE_D, EffectMood.AURORA, false, "8"),
        EffectInfo(9, "Strobe Mega", "疾速频闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "9"),
        EffectInfo(10, "Chase", "追逐", EffectCategory.ONE_D, EffectMood.AURORA, true, "10"),
        EffectInfo(11, "Chase Flash Rnd", "乱序追闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "11"),
        EffectInfo(12, "Colorful", "缤纷", EffectCategory.ONE_D, EffectMood.AURORA, false, "12"),
        EffectInfo(13, "Fireworks", "焰火", EffectCategory.ONE_D, EffectMood.AURORA, true, "13"),
        EffectInfo(14, "Fire Flicker", "烛火摇曳", EffectCategory.ONE_D, EffectMood.AURORA, false, "14"),
        EffectInfo(15, "Gradient", "渐变", EffectCategory.ONE_D, EffectMood.AURORA, false, "15"),
        EffectInfo(16, "Rolling Balls", "滚球", EffectCategory.ONE_D, EffectMood.AURORA, false, "16"),
        EffectInfo(17, "Fairy", "仙子尘", EffectCategory.ONE_D, EffectMood.AURORA, false, "17"),
        EffectInfo(18, "Image", "自定义图片", EffectCategory.ART, EffectMood.TEXT, true, "18"),
        EffectInfo(19, "Chase 3", "三重追逐", EffectCategory.ONE_D, EffectMood.AURORA, false, "19"),
        EffectInfo(20, "Tri Fade", "三色渐隐", EffectCategory.ONE_D, EffectMood.AURORA, false, "20"),
        EffectInfo(21, "Juggle", "抛球", EffectCategory.ONE_D, EffectMood.AURORA, false, "21"),
        EffectInfo(22, "Palette", "调色板", EffectCategory.ONE_D, EffectMood.AURORA, false, "22"),
        EffectInfo(23, "Colorwaves", "色浪", EffectCategory.ONE_D, EffectMood.AURORA, false, "23"),
        EffectInfo(24, "Fill Noise", "噪声填充", EffectCategory.ONE_D, EffectMood.AURORA, false, "24"),
        EffectInfo(25, "Noise 4", "噪声 IV", EffectCategory.ONE_D, EffectMood.AURORA, false, "25"),
        EffectInfo(26, "Colortwinkles", "彩屑闪烁", EffectCategory.ONE_D, EffectMood.AURORA, false, "26"),
        EffectInfo(27, "Lake", "湖光", EffectCategory.ONE_D, EffectMood.AURORA, false, "27"),
        EffectInfo(28, "Meteor", "流星", EffectCategory.ONE_D, EffectMood.AURORA, true, "28"),
        EffectInfo(29, "Copy Segment", "复制段落", EffectCategory.ONE_D, EffectMood.AURORA, false, "29"),
        EffectInfo(30, "Railway", "铁道", EffectCategory.ONE_D, EffectMood.AURORA, false, "30"),
        EffectInfo(31, "Ripple", "涟漪", EffectCategory.ONE_D, EffectMood.AURORA, true, "31"),
        EffectInfo(32, "Twinklefox", "萤火", EffectCategory.ONE_D, EffectMood.AURORA, false, "32"),
        EffectInfo(33, "Glitter", "碎钻", EffectCategory.ONE_D, EffectMood.AURORA, false, "33"),
        EffectInfo(34, "Fireworks Starburst", "星爆焰火", EffectCategory.ONE_D, EffectMood.AURORA, false, "34"),
        EffectInfo(35, "Bouncing Balls", "弹跳球", EffectCategory.ONE_D, EffectMood.AURORA, true, "35"),
        EffectInfo(36, "Popcorn", "爆米花", EffectCategory.ONE_D, EffectMood.AURORA, false, "36"),
        EffectInfo(37, "Drip", "滴落", EffectCategory.ONE_D, EffectMood.AURORA, false, "37"),
        EffectInfo(38, "Plasma", "等离子", EffectCategory.ONE_D, EffectMood.AURORA, true, "38"),
        EffectInfo(39, "Sunrise", "日出", EffectCategory.ONE_D, EffectMood.AURORA, false, "39"),
        EffectInfo(40, "Phased", "相位", EffectCategory.ONE_D, EffectMood.AURORA, false, "40"),
        EffectInfo(41, "Sine", "正弦波", EffectCategory.ONE_D, EffectMood.AURORA, false, "41"),
        EffectInfo(42, "Flow", "流动", EffectCategory.ONE_D, EffectMood.AURORA, false, "42"),
        EffectInfo(43, "Chunchun", "啾啾", EffectCategory.ONE_D, EffectMood.AURORA, false, "43"),
        EffectInfo(44, "Dancing Shadows", "舞影", EffectCategory.ONE_D, EffectMood.AURORA, false, "44"),
        EffectInfo(45, "Washing Machine", "洗衣机", EffectCategory.ONE_D, EffectMood.AURORA, false, "45"),
        EffectInfo(46, "Blends", "混色", EffectCategory.ONE_D, EffectMood.AURORA, false, "46"),
        EffectInfo(47, "Crazy Bees", "疯狂蜜蜂", EffectCategory.TWO_D, EffectMood.AURORA, false, "47"),
        EffectInfo(48, "Ghost Rider", "恶灵骑士", EffectCategory.TWO_D, EffectMood.AURORA, false, "48"),
        EffectInfo(49, "Blobs", "团块", EffectCategory.TWO_D, EffectMood.AURORA, false, "49"),
        EffectInfo(50, "Scrolling Text", "动态字幕", EffectCategory.ART, EffectMood.TEXT, true, "50"),
        EffectInfo(51, "Distortion Waves", "扭曲波", EffectCategory.TWO_D, EffectMood.AURORA, false, "51"),
        EffectInfo(52, "Soap", "皂泡", EffectCategory.TWO_D, EffectMood.AURORA, false, "52"),
        EffectInfo(53, "Octopus", "章鱼", EffectCategory.TWO_D, EffectMood.AURORA, false, "53"),
        EffectInfo(54, "Waving Cell", "波动细胞", EffectCategory.TWO_D, EffectMood.AURORA, false, "54"),
        EffectInfo(55, "Pixels", "像素", EffectCategory.ONE_D, EffectMood.AURORA, false, "55"),
        EffectInfo(56, "Pixelwave", "像素波", EffectCategory.ONE_D, EffectMood.AURORA, false, "56"),
        EffectInfo(57, "Juggles", "流光飞舞", EffectCategory.ONE_D, EffectMood.AURORA, false, "57"),
        EffectInfo(58, "Matripix", "矩阵像素", EffectCategory.ONE_D, EffectMood.AURORA, false, "58"),
        EffectInfo(59, "Gravimeter", "重力表", EffectCategory.ONE_D, EffectMood.AURORA, false, "59"),
        EffectInfo(60, "Plasmoid", "等离子团", EffectCategory.ONE_D, EffectMood.AURORA, false, "60"),
        EffectInfo(61, "Puddles", "水洼", EffectCategory.ONE_D, EffectMood.AURORA, false, "61"),
        EffectInfo(62, "Midnoise", "中段噪声", EffectCategory.ONE_D, EffectMood.AURORA, false, "62"),
        EffectInfo(63, "Waterfall", "瀑布", EffectCategory.ONE_D, EffectMood.AURORA, false, "63"),
        EffectInfo(64, "Freqpixels", "频谱像素", EffectCategory.ONE_D, EffectMood.MUSIC, false, "64"),
        EffectInfo(65, "Noisefire", "噪声之火", EffectCategory.ONE_D, EffectMood.AURORA, false, "65"),
        EffectInfo(66, "Noisemove", "噪声游移", EffectCategory.ONE_D, EffectMood.AURORA, false, "66"),
        EffectInfo(67, "Noise2D", "二维噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "67"),
        EffectInfo(68, "PacMan", "吃豆人", EffectCategory.ONE_D, EffectMood.AURORA, true, "68"),
        EffectInfo(69, "DNA", "基因链", EffectCategory.TWO_D, EffectMood.AURORA, false, "69"),
        EffectInfo(70, "Matrix", "数字雨", EffectCategory.TWO_D, EffectMood.TEXT, true, "70"),
        EffectInfo(71, "Metaballs", "融球", EffectCategory.TWO_D, EffectMood.AURORA, false, "71"),
        EffectInfo(72, "Gravcenter", "重力中心", EffectCategory.ONE_D, EffectMood.AURORA, false, "72"),
        EffectInfo(73, "Funky Plank", "律动板", EffectCategory.TWO_D, EffectMood.AURORA, false, "73"),
        EffectInfo(74, "Pulser", "脉冲", EffectCategory.TWO_D, EffectMood.AURORA, false, "74"),
        EffectInfo(75, "Blurz", "模糊", EffectCategory.ONE_D, EffectMood.AURORA, false, "75"),
        EffectInfo(76, "Drift", "漂移", EffectCategory.TWO_D, EffectMood.AURORA, false, "76"),
        EffectInfo(77, "Waverly", "波涌", EffectCategory.TWO_D, EffectMood.AURORA, false, "77"),
        EffectInfo(78, "Sun Radiation", "日冕", EffectCategory.TWO_D, EffectMood.AURORA, false, "78"),
        EffectInfo(79, "Colored Bursts", "彩爆", EffectCategory.TWO_D, EffectMood.AURORA, false, "79"),
        EffectInfo(80, "Julia", "朱莉娅集", EffectCategory.TWO_D, EffectMood.AURORA, false, "80"),
        EffectInfo(81, "Game Of Life", "生命游戏", EffectCategory.TWO_D, EffectMood.AURORA, false, "81"),
        EffectInfo(82, "Tartan", "格纹", EffectCategory.TWO_D, EffectMood.AURORA, false, "82"),
        EffectInfo(83, "Polar Lights", "极地之光", EffectCategory.TWO_D, EffectMood.AURORA, false, "83"),
        EffectInfo(84, "Swirl", "漩涡", EffectCategory.TWO_D, EffectMood.AURORA, false, "84"),
        EffectInfo(85, "Lissajous", "李萨如", EffectCategory.TWO_D, EffectMood.AURORA, false, "85"),
        EffectInfo(86, "Frizzles", "蜷曲", EffectCategory.TWO_D, EffectMood.AURORA, false, "86"),
        EffectInfo(87, "Flow Stripe", "流光条纹", EffectCategory.ONE_D, EffectMood.AURORA, false, "87"),
        EffectInfo(88, "Hiphotic", "催眠", EffectCategory.TWO_D, EffectMood.AURORA, false, "88"),
        EffectInfo(89, "Sindots", "正弦点", EffectCategory.TWO_D, EffectMood.AURORA, false, "89"),
        EffectInfo(90, "DNA Spiral", "基因螺旋", EffectCategory.TWO_D, EffectMood.AURORA, false, "90"),
        EffectInfo(91, "Black Hole", "黑洞", EffectCategory.TWO_D, EffectMood.AURORA, false, "91"),
        EffectInfo(92, "Wavesins", "波正弦", EffectCategory.ONE_D, EffectMood.AURORA, false, "92"),
        EffectInfo(93, "Rocktaves", "摇滚波", EffectCategory.ONE_D, EffectMood.AURORA, false, "93"),
        EffectInfo(94, "PS Vortex", "涡旋", EffectCategory.TWO_D, EffectMood.AURORA, false, "94"),
        EffectInfo(95, "PS Fuzzy Noise", "朦胧噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "95"),
        EffectInfo(96, "PS Ballpit", "球池", EffectCategory.TWO_D, EffectMood.AURORA, false, "96"),
        EffectInfo(97, "PS Box", "盒子", EffectCategory.TWO_D, EffectMood.AURORA, false, "97"),
        EffectInfo(98, "PS Attractor", "吸引子", EffectCategory.TWO_D, EffectMood.AURORA, false, "98"),
        EffectInfo(99, "PS Spray", "喷溅", EffectCategory.TWO_D, EffectMood.AURORA, false, "99"),
        EffectInfo(100, "PS Ghost Rider", "粒子恶灵", EffectCategory.TWO_D, EffectMood.AURORA, false, "100"),
        EffectInfo(101, "PS Blobs", "粒子团块", EffectCategory.TWO_D, EffectMood.AURORA, false, "101"),
        EffectInfo(102, "PS Dancing Shadows", "粒子舞影", EffectCategory.TWO_D, EffectMood.AURORA, false, "102"),
        EffectInfo(103, "PS Chase", "粒子追逐", EffectCategory.TWO_D, EffectMood.AURORA, false, "103"),
        EffectInfo(104, "PS GEQ 1D", "线型频谱", EffectCategory.TWO_D, EffectMood.MUSIC, false, "104"),
        EffectInfo(105, "PS Springy", "弹簧", EffectCategory.TWO_D, EffectMood.AURORA, false, "105"),
        EffectInfo(106, "PS Galaxy", "星海", EffectCategory.TWO_D, EffectMood.AURORA, false, "106"),
        EffectInfo(107, "Color Clouds", "彩云", EffectCategory.ONE_D, EffectMood.AURORA, false, "107"),
        EffectInfo(108, "极光之舞", "极光之舞", EffectCategory.TWO_D, EffectMood.AURORA, true, "108"),
        EffectInfo(109, "Lumi Forest", "林间萤光", EffectCategory.TWO_D, EffectMood.AURORA, false, "109"),
        EffectInfo(110, "Lumi Caustics", "深海波光", EffectCategory.TWO_D, EffectMood.AURORA, false, "110"),
        EffectInfo(111, "Stage Light", "舞台灯", EffectCategory.ONE_D, EffectMood.MUSIC, false, "111"),
        EffectInfo(112, "Tri-band Breath", "三频呼吸", EffectCategory.ONE_D, EffectMood.MUSIC, false, "112"),
        EffectInfo(113, "Audio Spiral", "音频螺旋", EffectCategory.ONE_D, EffectMood.MUSIC, false, "113"),
        EffectInfo(114, "Sonic Pulse", "声波脉冲", EffectCategory.ONE_D, EffectMood.MUSIC, false, "114"),
        EffectInfo(115, "Bass Lava", "低频熔岩", EffectCategory.ONE_D, EffectMood.MUSIC, false, "115"),
        EffectInfo(116, "Spectrum Lamp", "频谱灯", EffectCategory.ONE_D, EffectMood.MUSIC, false, "116"),
        EffectInfo(117, "Audio Solution", "音律溶液", EffectCategory.ONE_D, EffectMood.MUSIC, false, "117"),
    )

    private val byId: Map<Int, EffectInfo> = all.associateBy { it.id }

    fun find(id: Int?): EffectInfo? = id?.let { byId[it] }

    fun nameOf(id: Int?): String? = find(id)?.name

    /** 按固件英文名反查灯效。业务代码不写死 id —— 固件插入新效果后 id 会整体前移。 */
    fun byName(name: String): EffectInfo? = all.firstOrNull { it.name == name.trim() }

    val popular: List<EffectInfo> = all.filter { it.isPopular }

    fun byCategory(category: EffectCategory): List<EffectInfo> = all.filter { it.category == category }

    fun byMood(mood: EffectMood): List<EffectInfo> = all.filter { it.mood == mood }
}
