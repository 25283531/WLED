// 由 tools/gen_effect_catalog.py 自动生成，请勿手工编辑。
// 数据源: firmware/effects_enabled.json（idx 即 WLED 运行时 fx 值）
package ca.cgagnier.wlednativeandroid.ui.effects

/** 灯效分类。1D 与 2D 由固件效果类型决定，「字画」是能直接显示用户内容的效果子集。 */
enum class EffectCategory { ONE_D, TWO_D, ART }

/**
 * 律动模式：App 侧的产品分类，对应首页的四个胶囊与灯效库的四个 Tab。
 *
 * 固件没有这个概念，分类由 [EffectCatalog] 的 id 白名单决定。
 */
enum class EffectMood { AURORA, MUSIC, TEXT, SLEEP }

/**
 * 一个固件灯效。
 *
 * @property id 运行时 fx 值，可直接写入 `seg.fx`。
 * @property name 固件提供的英文名称（`/json/effects` 同一份数据，此处固化以便离线展示）。
 * @property nameZh 中文显示名；未收录时与 [name] 相同。
 * @property category 见 [EffectCategory]。
 * @property mood 首页四胶囊的分组，见 [EffectMood]。
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
        EffectInfo(0, "Copy Segment", "复制段落", EffectCategory.ONE_D, EffectMood.AURORA, false, "0"),
        EffectInfo(1, "Blink", "明灭", EffectCategory.ONE_D, EffectMood.AURORA, true, "1"),
        EffectInfo(2, "Breathe", "呼吸", EffectCategory.ONE_D, EffectMood.SLEEP, true, "2"),
        EffectInfo(3, "Wipe", "擦拭", EffectCategory.ONE_D, EffectMood.AURORA, true, "3"),
        EffectInfo(4, "Random Colors", "随机彩", EffectCategory.ONE_D, EffectMood.AURORA, false, "4"),
        EffectInfo(5, "Sweep", "扫光", EffectCategory.ONE_D, EffectMood.AURORA, false, "5"),
        EffectInfo(6, "Dynamic", "动感", EffectCategory.ONE_D, EffectMood.AURORA, false, "6"),
        EffectInfo(7, "Colorloop", "循环彩", EffectCategory.ONE_D, EffectMood.AURORA, true, "7"),
        EffectInfo(8, "Rainbow", "七彩流光", EffectCategory.ONE_D, EffectMood.AURORA, true, "8"),
        EffectInfo(9, "Scan", "扫描", EffectCategory.ONE_D, EffectMood.AURORA, false, "9"),
        EffectInfo(10, "Fade", "渐隐", EffectCategory.ONE_D, EffectMood.SLEEP, false, "10"),
        EffectInfo(11, "Theater", "剧院追光", EffectCategory.ONE_D, EffectMood.AURORA, false, "11"),
        EffectInfo(12, "Running", "奔走", EffectCategory.ONE_D, EffectMood.AURORA, false, "12"),
        EffectInfo(13, "Twinkle", "星闪", EffectCategory.ONE_D, EffectMood.SLEEP, true, "13"),
        EffectInfo(14, "Dissolve", "溶解", EffectCategory.ONE_D, EffectMood.AURORA, false, "14"),
        EffectInfo(15, "Sparkle Dark", "暗夜星屑", EffectCategory.ONE_D, EffectMood.SLEEP, false, "15"),
        EffectInfo(16, "Strobe", "频闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "16"),
        EffectInfo(17, "Strobe Mega", "疾速频闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "17"),
        EffectInfo(18, "Android", "安卓", EffectCategory.ONE_D, EffectMood.AURORA, false, "18"),
        EffectInfo(19, "Chase", "追逐", EffectCategory.ONE_D, EffectMood.AURORA, true, "19"),
        EffectInfo(20, "Chase Flash", "追逐闪白", EffectCategory.ONE_D, EffectMood.AURORA, false, "20"),
        EffectInfo(21, "Chase Flash Rnd", "乱序追闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "21"),
        EffectInfo(22, "Rainbow Runner", "彩虹奔流", EffectCategory.ONE_D, EffectMood.AURORA, false, "22"),
        EffectInfo(23, "Colorful", "缤纷", EffectCategory.ONE_D, EffectMood.AURORA, false, "23"),
        EffectInfo(24, "Traffic Light", "红绿灯", EffectCategory.ONE_D, EffectMood.AURORA, false, "24"),
        EffectInfo(25, "Aurora", "极光", EffectCategory.ONE_D, EffectMood.AURORA, true, "25"),
        EffectInfo(26, "Color Clouds", "彩云", EffectCategory.ONE_D, EffectMood.AURORA, false, "26"),
        EffectInfo(27, "Stream", "溪流", EffectCategory.ONE_D, EffectMood.AURORA, false, "27"),
        EffectInfo(28, "Scanner", "巡航", EffectCategory.ONE_D, EffectMood.AURORA, true, "28"),
        EffectInfo(29, "Rain", "落雨", EffectCategory.ONE_D, EffectMood.AURORA, false, "29"),
        EffectInfo(30, "Colorwaves", "色浪", EffectCategory.ONE_D, EffectMood.AURORA, false, "30"),
        EffectInfo(31, "Fireworks", "焰火", EffectCategory.ONE_D, EffectMood.AURORA, true, "31"),
        EffectInfo(32, "Tetrix", "俄罗斯方块", EffectCategory.ONE_D, EffectMood.AURORA, false, "32"),
        EffectInfo(33, "Fire Flicker", "烛火摇曳", EffectCategory.ONE_D, EffectMood.AURORA, false, "33"),
        EffectInfo(34, "Gradient", "渐变", EffectCategory.ONE_D, EffectMood.AURORA, false, "34"),
        EffectInfo(35, "Fairy", "仙子尘", EffectCategory.ONE_D, EffectMood.AURORA, false, "35"),
        EffectInfo(36, "Two Dots", "双点", EffectCategory.ONE_D, EffectMood.AURORA, false, "36"),
        EffectInfo(37, "Fairytwinkle", "仙尘闪烁", EffectCategory.ONE_D, EffectMood.AURORA, false, "37"),
        EffectInfo(38, "Image", "自定义图片", EffectCategory.ART, EffectMood.TEXT, true, "38"),
        EffectInfo(39, "Chase 3", "三重追逐", EffectCategory.ONE_D, EffectMood.AURORA, false, "39"),
        EffectInfo(40, "Tri Wipe", "三色擦拭", EffectCategory.ONE_D, EffectMood.AURORA, false, "40"),
        EffectInfo(41, "Tri Fade", "三色渐隐", EffectCategory.ONE_D, EffectMood.AURORA, false, "41"),
        EffectInfo(42, "Lightning", "闪电", EffectCategory.ONE_D, EffectMood.AURORA, false, "42"),
        EffectInfo(43, "ICU", "监护仪", EffectCategory.ONE_D, EffectMood.AURORA, false, "43"),
        EffectInfo(44, "Stream 2", "溪流 II", EffectCategory.ONE_D, EffectMood.AURORA, false, "44"),
        EffectInfo(45, "Oscillate", "振荡", EffectCategory.ONE_D, EffectMood.AURORA, false, "45"),
        EffectInfo(46, "Juggle", "抛球", EffectCategory.ONE_D, EffectMood.AURORA, false, "46"),
        EffectInfo(47, "Palette", "调色板", EffectCategory.ONE_D, EffectMood.AURORA, false, "47"),
        EffectInfo(48, "Bpm", "节拍", EffectCategory.ONE_D, EffectMood.MUSIC, false, "48"),
        EffectInfo(49, "Fill Noise", "噪声填充", EffectCategory.ONE_D, EffectMood.AURORA, false, "49"),
        EffectInfo(50, "Noise 1", "噪声 I", EffectCategory.ONE_D, EffectMood.AURORA, false, "50"),
        EffectInfo(51, "Noise 4", "噪声 IV", EffectCategory.ONE_D, EffectMood.AURORA, false, "51"),
        EffectInfo(52, "Colortwinkles", "彩屑闪烁", EffectCategory.ONE_D, EffectMood.SLEEP, false, "52"),
        EffectInfo(53, "Lake", "湖光", EffectCategory.ONE_D, EffectMood.SLEEP, false, "53"),
        EffectInfo(54, "Meteor", "流星", EffectCategory.ONE_D, EffectMood.AURORA, true, "54"),
        EffectInfo(55, "Railway", "铁道", EffectCategory.ONE_D, EffectMood.AURORA, false, "55"),
        EffectInfo(56, "Ripple", "涟漪", EffectCategory.ONE_D, EffectMood.AURORA, true, "56"),
        EffectInfo(57, "Twinklefox", "萤火", EffectCategory.ONE_D, EffectMood.SLEEP, false, "57"),
        EffectInfo(58, "Halloween Eyes", "万圣之眼", EffectCategory.ONE_D, EffectMood.AURORA, false, "58"),
        EffectInfo(59, "Solid Pattern", "纯色图案", EffectCategory.ONE_D, EffectMood.AURORA, true, "59"),
        EffectInfo(60, "Solid Pattern Tri", "三色图案", EffectCategory.ONE_D, EffectMood.AURORA, false, "60"),
        EffectInfo(61, "Spots", "光斑", EffectCategory.ONE_D, EffectMood.AURORA, false, "61"),
        EffectInfo(62, "Lighthouse", "灯塔", EffectCategory.ONE_D, EffectMood.AURORA, true, "62"),
        EffectInfo(63, "Fire 2012", "壁炉篝火", EffectCategory.ONE_D, EffectMood.AURORA, true, "63"),
        EffectInfo(64, "Fireworks 1D", "线型焰火", EffectCategory.ONE_D, EffectMood.AURORA, false, "64"),
        EffectInfo(65, "Sparkle", "闪耀", EffectCategory.ONE_D, EffectMood.AURORA, false, "65"),
        EffectInfo(66, "Glitter", "碎钻", EffectCategory.ONE_D, EffectMood.AURORA, false, "66"),
        EffectInfo(67, "Solid Glitter", "纯色碎钻", EffectCategory.ONE_D, EffectMood.AURORA, true, "67"),
        EffectInfo(68, "Multi Comet", "多重彗尾", EffectCategory.ONE_D, EffectMood.AURORA, true, "68"),
        EffectInfo(69, "Rolling Balls", "滚球", EffectCategory.ONE_D, EffectMood.AURORA, false, "69"),
        EffectInfo(70, "Fireworks Starburst", "星爆焰火", EffectCategory.ONE_D, EffectMood.AURORA, false, "70"),
        EffectInfo(71, "Dancing Shadows", "舞影", EffectCategory.ONE_D, EffectMood.AURORA, false, "71"),
        EffectInfo(72, "Candle", "烛光", EffectCategory.ONE_D, EffectMood.SLEEP, true, "72"),
        EffectInfo(73, "Bouncing Balls", "弹跳球", EffectCategory.ONE_D, EffectMood.AURORA, true, "73"),
        EffectInfo(74, "Popcorn", "爆米花", EffectCategory.ONE_D, EffectMood.AURORA, false, "74"),
        EffectInfo(75, "Drip", "滴落", EffectCategory.ONE_D, EffectMood.AURORA, false, "75"),
        EffectInfo(76, "Sinelon", "正弦彗星", EffectCategory.ONE_D, EffectMood.AURORA, true, "76"),
        EffectInfo(77, "Plasma", "等离子", EffectCategory.ONE_D, EffectMood.AURORA, true, "77"),
        EffectInfo(78, "Percent", "百分比", EffectCategory.ONE_D, EffectMood.AURORA, false, "78"),
        EffectInfo(79, "Heartbeat", "心跳", EffectCategory.ONE_D, EffectMood.AURORA, false, "79"),
        EffectInfo(80, "Pacifica", "深海潮汐", EffectCategory.ONE_D, EffectMood.SLEEP, false, "80"),
        EffectInfo(81, "Candle Multi", "多簇烛光", EffectCategory.ONE_D, EffectMood.SLEEP, false, "81"),
        EffectInfo(82, "Sunrise", "日出", EffectCategory.ONE_D, EffectMood.SLEEP, false, "82"),
        EffectInfo(83, "Phased", "相位", EffectCategory.ONE_D, EffectMood.AURORA, false, "83"),
        EffectInfo(84, "Twinkleup", "上升星点", EffectCategory.ONE_D, EffectMood.SLEEP, false, "84"),
        EffectInfo(85, "Noise Pal", "噪声调色", EffectCategory.ONE_D, EffectMood.AURORA, false, "85"),
        EffectInfo(86, "Sine", "正弦波", EffectCategory.ONE_D, EffectMood.AURORA, false, "86"),
        EffectInfo(87, "Flow", "流动", EffectCategory.ONE_D, EffectMood.AURORA, false, "87"),
        EffectInfo(88, "Chunchun", "啾啾", EffectCategory.ONE_D, EffectMood.AURORA, false, "88"),
        EffectInfo(89, "Washing Machine", "洗衣机", EffectCategory.ONE_D, EffectMood.AURORA, false, "89"),
        EffectInfo(90, "Blends", "混色", EffectCategory.ONE_D, EffectMood.AURORA, false, "90"),
        EffectInfo(91, "TV Simulator", "电视雪花", EffectCategory.ONE_D, EffectMood.AURORA, false, "91"),
        EffectInfo(92, "PacMan", "吃豆人", EffectCategory.ONE_D, EffectMood.AURORA, true, "92"),
        EffectInfo(93, "Slow Transition", "缓慢流转", EffectCategory.ONE_D, EffectMood.SLEEP, false, "93"),
        EffectInfo(94, "Pixels", "像素", EffectCategory.ONE_D, EffectMood.AURORA, false, "94"),
        EffectInfo(95, "Pixelwave", "像素波", EffectCategory.ONE_D, EffectMood.AURORA, false, "95"),
        EffectInfo(96, "Juggles", "流光飞舞", EffectCategory.ONE_D, EffectMood.AURORA, false, "96"),
        EffectInfo(97, "Matripix", "矩阵像素", EffectCategory.ONE_D, EffectMood.AURORA, false, "97"),
        EffectInfo(98, "Gravimeter", "重力表", EffectCategory.ONE_D, EffectMood.AURORA, false, "98"),
        EffectInfo(99, "Plasmoid", "等离子团", EffectCategory.ONE_D, EffectMood.AURORA, false, "99"),
        EffectInfo(100, "Puddles", "水洼", EffectCategory.ONE_D, EffectMood.AURORA, false, "100"),
        EffectInfo(101, "Midnoise", "中段噪声", EffectCategory.ONE_D, EffectMood.AURORA, false, "101"),
        EffectInfo(102, "Noisemeter", "噪声电平", EffectCategory.ONE_D, EffectMood.MUSIC, false, "102"),
        EffectInfo(103, "Freqwave", "频谱波", EffectCategory.ONE_D, EffectMood.MUSIC, false, "103"),
        EffectInfo(104, "Waterfall", "瀑布", EffectCategory.ONE_D, EffectMood.AURORA, false, "104"),
        EffectInfo(105, "Freqpixels", "频谱像素", EffectCategory.ONE_D, EffectMood.MUSIC, false, "105"),
        EffectInfo(106, "Noisefire", "噪声之火", EffectCategory.ONE_D, EffectMood.AURORA, false, "106"),
        EffectInfo(107, "Noisemove", "噪声游移", EffectCategory.ONE_D, EffectMood.AURORA, false, "107"),
        EffectInfo(108, "Perlin Move", "柏林游移", EffectCategory.ONE_D, EffectMood.AURORA, false, "108"),
        EffectInfo(109, "Ripple Peak", "峰值涟漪", EffectCategory.ONE_D, EffectMood.MUSIC, false, "109"),
        EffectInfo(110, "Freqmap", "频谱映射", EffectCategory.ONE_D, EffectMood.MUSIC, false, "110"),
        EffectInfo(111, "Gravcenter", "重力中心", EffectCategory.ONE_D, EffectMood.AURORA, false, "111"),
        EffectInfo(112, "DJ Light", "打碟灯", EffectCategory.ONE_D, EffectMood.MUSIC, false, "112"),
        EffectInfo(113, "Blurz", "模糊", EffectCategory.ONE_D, EffectMood.AURORA, false, "113"),
        EffectInfo(114, "Flow Stripe", "流光条纹", EffectCategory.ONE_D, EffectMood.AURORA, false, "114"),
        EffectInfo(115, "Wavesins", "波正弦", EffectCategory.ONE_D, EffectMood.AURORA, false, "115"),
        EffectInfo(116, "Rocktaves", "摇滚波", EffectCategory.ONE_D, EffectMood.AURORA, false, "116"),
        EffectInfo(117, "Shimmer", "微光", EffectCategory.ONE_D, EffectMood.AURORA, false, "117"),
        EffectInfo(118, "Rotozoomer", "旋转缩放", EffectCategory.TWO_D, EffectMood.AURORA, true, "118"),
        EffectInfo(119, "Spaceships", "飞船", EffectCategory.TWO_D, EffectMood.AURORA, false, "119"),
        EffectInfo(120, "Crazy Bees", "疯狂蜜蜂", EffectCategory.TWO_D, EffectMood.AURORA, false, "120"),
        EffectInfo(121, "Ghost Rider", "恶灵骑士", EffectCategory.TWO_D, EffectMood.AURORA, false, "121"),
        EffectInfo(122, "Blobs", "团块", EffectCategory.TWO_D, EffectMood.AURORA, false, "122"),
        EffectInfo(123, "Scrolling Text", "动态字幕", EffectCategory.ART, EffectMood.TEXT, true, "123"),
        EffectInfo(124, "Drift Rose", "玫瑰漂移", EffectCategory.TWO_D, EffectMood.AURORA, false, "124"),
        EffectInfo(125, "Distortion Waves", "扭曲波", EffectCategory.TWO_D, EffectMood.AURORA, false, "125"),
        EffectInfo(126, "GEQ", "音频律动", EffectCategory.TWO_D, EffectMood.MUSIC, true, "126"),
        EffectInfo(127, "Analog Clock 2D", "指针时钟", EffectCategory.TWO_D, EffectMood.TEXT, false, "127"),
        EffectInfo(128, "Noise2D", "二维噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "128"),
        EffectInfo(129, "Firenoise", "火焰噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "129"),
        EffectInfo(130, "Squared Swirl", "方块漩涡", EffectCategory.TWO_D, EffectMood.AURORA, false, "130"),
        EffectInfo(131, "DNA", "基因链", EffectCategory.TWO_D, EffectMood.AURORA, false, "131"),
        EffectInfo(132, "Matrix", "数字雨", EffectCategory.TWO_D, EffectMood.TEXT, true, "132"),
        EffectInfo(133, "Metaballs", "融球", EffectCategory.TWO_D, EffectMood.AURORA, false, "133"),
        EffectInfo(134, "Funky Plank", "律动板", EffectCategory.TWO_D, EffectMood.AURORA, false, "134"),
        EffectInfo(135, "Pulser", "脉冲", EffectCategory.TWO_D, EffectMood.AURORA, false, "135"),
        EffectInfo(136, "Drift", "漂移", EffectCategory.TWO_D, EffectMood.AURORA, false, "136"),
        EffectInfo(137, "Waverly", "波涌", EffectCategory.TWO_D, EffectMood.AURORA, false, "137"),
        EffectInfo(138, "Sun Radiation", "日冕", EffectCategory.TWO_D, EffectMood.AURORA, false, "138"),
        EffectInfo(139, "Colored Bursts", "彩爆", EffectCategory.TWO_D, EffectMood.AURORA, false, "139"),
        EffectInfo(140, "Julia", "朱莉娅集", EffectCategory.TWO_D, EffectMood.AURORA, false, "140"),
        EffectInfo(141, "Game Of Life", "生命游戏", EffectCategory.TWO_D, EffectMood.AURORA, false, "141"),
        EffectInfo(142, "Tartan", "格纹", EffectCategory.TWO_D, EffectMood.AURORA, false, "142"),
        EffectInfo(143, "Polar Lights", "极地之光", EffectCategory.TWO_D, EffectMood.AURORA, false, "143"),
        EffectInfo(144, "Swirl", "漩涡", EffectCategory.TWO_D, EffectMood.AURORA, false, "144"),
        EffectInfo(145, "Lissajous", "李萨如", EffectCategory.TWO_D, EffectMood.AURORA, false, "145"),
        EffectInfo(146, "Frizzles", "蜷曲", EffectCategory.TWO_D, EffectMood.AURORA, false, "146"),
        EffectInfo(147, "Plasma Ball", "等离子球", EffectCategory.TWO_D, EffectMood.AURORA, false, "147"),
        EffectInfo(148, "Hiphotic", "催眠", EffectCategory.TWO_D, EffectMood.AURORA, false, "148"),
        EffectInfo(149, "Sindots", "正弦点", EffectCategory.TWO_D, EffectMood.AURORA, false, "149"),
        EffectInfo(150, "DNA Spiral", "基因螺旋", EffectCategory.TWO_D, EffectMood.AURORA, false, "150"),
        EffectInfo(151, "Black Hole", "黑洞", EffectCategory.TWO_D, EffectMood.AURORA, false, "151"),
        EffectInfo(152, "Soap", "皂泡", EffectCategory.TWO_D, EffectMood.AURORA, false, "152"),
        EffectInfo(153, "Octopus", "章鱼", EffectCategory.TWO_D, EffectMood.AURORA, false, "153"),
        EffectInfo(154, "Waving Cell", "波动细胞", EffectCategory.TWO_D, EffectMood.AURORA, false, "154"),
        EffectInfo(155, "Akemi", "阿凯米律动", EffectCategory.TWO_D, EffectMood.MUSIC, false, "155"),
        EffectInfo(156, "PS Volcano", "火山", EffectCategory.TWO_D, EffectMood.AURORA, false, "156"),
        EffectInfo(157, "PS Fire", "火焰", EffectCategory.TWO_D, EffectMood.AURORA, false, "157"),
        EffectInfo(158, "PS Fireworks", "粒子焰火", EffectCategory.TWO_D, EffectMood.AURORA, false, "158"),
        EffectInfo(159, "PS Vortex", "涡旋", EffectCategory.TWO_D, EffectMood.AURORA, false, "159"),
        EffectInfo(160, "PS Fuzzy Noise", "朦胧噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "160"),
        EffectInfo(161, "PS Ballpit", "球池", EffectCategory.TWO_D, EffectMood.AURORA, false, "161"),
        EffectInfo(162, "PS Box", "盒子", EffectCategory.TWO_D, EffectMood.AURORA, false, "162"),
        EffectInfo(163, "PS Attractor", "吸引子", EffectCategory.TWO_D, EffectMood.AURORA, false, "163"),
        EffectInfo(164, "PS Impact", "冲击", EffectCategory.TWO_D, EffectMood.AURORA, false, "164"),
        EffectInfo(165, "PS Waterfall", "粒子瀑布", EffectCategory.TWO_D, EffectMood.AURORA, false, "165"),
        EffectInfo(166, "PS Spray", "喷溅", EffectCategory.TWO_D, EffectMood.AURORA, false, "166"),
        EffectInfo(167, "PS GEQ 2D", "二维频谱", EffectCategory.TWO_D, EffectMood.MUSIC, false, "167"),
        EffectInfo(168, "PS GEQ Nova", "频谱新星", EffectCategory.TWO_D, EffectMood.MUSIC, false, "168"),
        EffectInfo(169, "PS Ghost Rider", "粒子恶灵", EffectCategory.TWO_D, EffectMood.AURORA, false, "169"),
        EffectInfo(170, "PS Blobs", "粒子团块", EffectCategory.TWO_D, EffectMood.AURORA, false, "170"),
        EffectInfo(171, "PS Galaxy", "星海", EffectCategory.TWO_D, EffectMood.AURORA, false, "171"),
        EffectInfo(172, "PS DripDrop", "滴答", EffectCategory.TWO_D, EffectMood.AURORA, false, "172"),
        EffectInfo(173, "PS Pinball", "弹珠", EffectCategory.TWO_D, EffectMood.AURORA, false, "173"),
        EffectInfo(174, "PS Dancing Shadows", "粒子舞影", EffectCategory.TWO_D, EffectMood.AURORA, false, "174"),
        EffectInfo(175, "PS Fireworks 1D", "线型粒子焰火", EffectCategory.TWO_D, EffectMood.AURORA, false, "175"),
        EffectInfo(176, "PS Sparkler", "手持烟花", EffectCategory.TWO_D, EffectMood.AURORA, false, "176"),
        EffectInfo(177, "PS Hourglass", "沙漏", EffectCategory.TWO_D, EffectMood.AURORA, false, "177"),
        EffectInfo(178, "PS Spray 1D", "线型喷溅", EffectCategory.TWO_D, EffectMood.AURORA, false, "178"),
        EffectInfo(179, "PS 1D Balance", "线型平衡", EffectCategory.TWO_D, EffectMood.AURORA, false, "179"),
        EffectInfo(180, "PS Chase", "粒子追逐", EffectCategory.TWO_D, EffectMood.AURORA, false, "180"),
        EffectInfo(181, "PS Starburst", "星芒", EffectCategory.TWO_D, EffectMood.AURORA, false, "181"),
        EffectInfo(182, "PS GEQ 1D", "线型频谱", EffectCategory.TWO_D, EffectMood.MUSIC, false, "182"),
        EffectInfo(183, "PS Fire 1D", "线型火焰", EffectCategory.TWO_D, EffectMood.AURORA, false, "183"),
        EffectInfo(184, "PS Sonic Stream", "声波激流", EffectCategory.TWO_D, EffectMood.MUSIC, false, "184"),
        EffectInfo(185, "PS Sonic Boom", "声爆", EffectCategory.TWO_D, EffectMood.MUSIC, false, "185"),
        EffectInfo(186, "PS Springy", "弹簧", EffectCategory.TWO_D, EffectMood.AURORA, false, "186"),
    )

    private val byId: Map<Int, EffectInfo> = all.associateBy { it.id }

    fun find(id: Int?): EffectInfo? = id?.let { byId[it] }

    fun nameOf(id: Int?): String? = find(id)?.name

    val popular: List<EffectInfo> = all.filter { it.isPopular }

    fun byCategory(category: EffectCategory): List<EffectInfo> = all.filter { it.category == category }

    fun byMood(mood: EffectMood): List<EffectInfo> = all.filter { it.mood == mood }
}
