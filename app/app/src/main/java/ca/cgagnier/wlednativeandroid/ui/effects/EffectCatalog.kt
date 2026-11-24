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
        EffectInfo(3, "Wipe", "擦拭", EffectCategory.ONE_D, EffectMood.AURORA, true, "3"),
        EffectInfo(4, "Random Colors", "随机彩", EffectCategory.ONE_D, EffectMood.AURORA, false, "4"),
        EffectInfo(5, "Sweep", "扫光", EffectCategory.ONE_D, EffectMood.AURORA, false, "5"),
        EffectInfo(6, "Dynamic", "动感", EffectCategory.ONE_D, EffectMood.AURORA, false, "6"),
        EffectInfo(7, "Colorloop", "循环彩", EffectCategory.ONE_D, EffectMood.AURORA, true, "7"),
        EffectInfo(8, "Rainbow", "七彩流光", EffectCategory.ONE_D, EffectMood.AURORA, true, "8"),
        EffectInfo(9, "Scan", "扫描", EffectCategory.ONE_D, EffectMood.AURORA, false, "9"),
        EffectInfo(10, "Fade", "渐隐", EffectCategory.ONE_D, EffectMood.AURORA, false, "10"),
        EffectInfo(11, "Theater", "剧院追光", EffectCategory.ONE_D, EffectMood.AURORA, false, "11"),
        EffectInfo(12, "Running", "奔走", EffectCategory.ONE_D, EffectMood.AURORA, false, "12"),
        EffectInfo(13, "Twinkle", "星闪", EffectCategory.ONE_D, EffectMood.AURORA, true, "13"),
        EffectInfo(14, "Dissolve", "溶解", EffectCategory.ONE_D, EffectMood.AURORA, false, "14"),
        EffectInfo(15, "Sparkle", "闪耀", EffectCategory.ONE_D, EffectMood.AURORA, false, "15"),
        EffectInfo(16, "Sparkle Dark", "暗夜星屑", EffectCategory.ONE_D, EffectMood.AURORA, false, "16"),
        EffectInfo(17, "Strobe", "频闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "17"),
        EffectInfo(18, "Strobe Mega", "疾速频闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "18"),
        EffectInfo(19, "Android", "安卓", EffectCategory.ONE_D, EffectMood.AURORA, false, "19"),
        EffectInfo(20, "Chase", "追逐", EffectCategory.ONE_D, EffectMood.AURORA, true, "20"),
        EffectInfo(21, "Chase Flash", "追逐闪白", EffectCategory.ONE_D, EffectMood.AURORA, false, "21"),
        EffectInfo(22, "Chase Flash Rnd", "乱序追闪", EffectCategory.ONE_D, EffectMood.AURORA, false, "22"),
        EffectInfo(23, "Rainbow Runner", "彩虹奔流", EffectCategory.ONE_D, EffectMood.AURORA, false, "23"),
        EffectInfo(24, "Colorful", "缤纷", EffectCategory.ONE_D, EffectMood.AURORA, false, "24"),
        EffectInfo(25, "Traffic Light", "红绿灯", EffectCategory.ONE_D, EffectMood.AURORA, false, "25"),
        EffectInfo(26, "Aurora", "极光", EffectCategory.ONE_D, EffectMood.AURORA, true, "26"),
        EffectInfo(27, "Stream", "溪流", EffectCategory.ONE_D, EffectMood.AURORA, false, "27"),
        EffectInfo(28, "Scanner", "巡航", EffectCategory.ONE_D, EffectMood.AURORA, true, "28"),
        EffectInfo(29, "Lighthouse", "灯塔", EffectCategory.ONE_D, EffectMood.AURORA, true, "29"),
        EffectInfo(30, "Fireworks", "焰火", EffectCategory.ONE_D, EffectMood.AURORA, true, "30"),
        EffectInfo(31, "Rain", "落雨", EffectCategory.ONE_D, EffectMood.AURORA, false, "31"),
        EffectInfo(32, "Tetrix", "俄罗斯方块", EffectCategory.ONE_D, EffectMood.AURORA, false, "32"),
        EffectInfo(33, "Fire Flicker", "烛火摇曳", EffectCategory.ONE_D, EffectMood.AURORA, false, "33"),
        EffectInfo(34, "Gradient", "渐变", EffectCategory.ONE_D, EffectMood.AURORA, false, "34"),
        EffectInfo(35, "Rolling Balls", "滚球", EffectCategory.ONE_D, EffectMood.AURORA, false, "35"),
        EffectInfo(36, "Fairy", "仙子尘", EffectCategory.ONE_D, EffectMood.AURORA, false, "36"),
        EffectInfo(37, "Two Dots", "双点", EffectCategory.ONE_D, EffectMood.AURORA, false, "37"),
        EffectInfo(38, "Fairytwinkle", "仙尘闪烁", EffectCategory.ONE_D, EffectMood.AURORA, false, "38"),
        EffectInfo(39, "Image", "自定义图片", EffectCategory.ART, EffectMood.TEXT, true, "39"),
        EffectInfo(40, "Chase 3", "三重追逐", EffectCategory.ONE_D, EffectMood.AURORA, false, "40"),
        EffectInfo(41, "Tri Wipe", "三色擦拭", EffectCategory.ONE_D, EffectMood.AURORA, false, "41"),
        EffectInfo(42, "Tri Fade", "三色渐隐", EffectCategory.ONE_D, EffectMood.AURORA, false, "42"),
        EffectInfo(43, "Lightning", "闪电", EffectCategory.ONE_D, EffectMood.AURORA, false, "43"),
        EffectInfo(44, "ICU", "监护仪", EffectCategory.ONE_D, EffectMood.AURORA, false, "44"),
        EffectInfo(45, "Multi Comet", "多重彗尾", EffectCategory.ONE_D, EffectMood.AURORA, true, "45"),
        EffectInfo(46, "Stream 2", "溪流 II", EffectCategory.ONE_D, EffectMood.AURORA, false, "46"),
        EffectInfo(47, "Oscillate", "振荡", EffectCategory.ONE_D, EffectMood.AURORA, false, "47"),
        EffectInfo(48, "Juggle", "抛球", EffectCategory.ONE_D, EffectMood.AURORA, false, "48"),
        EffectInfo(49, "Palette", "调色板", EffectCategory.ONE_D, EffectMood.AURORA, false, "49"),
        EffectInfo(50, "Fire 2012", "壁炉篝火", EffectCategory.ONE_D, EffectMood.AURORA, true, "50"),
        EffectInfo(51, "Colorwaves", "色浪", EffectCategory.ONE_D, EffectMood.AURORA, false, "51"),
        EffectInfo(52, "Bpm", "节拍", EffectCategory.ONE_D, EffectMood.MUSIC, false, "52"),
        EffectInfo(53, "Fill Noise", "噪声填充", EffectCategory.ONE_D, EffectMood.AURORA, false, "53"),
        EffectInfo(54, "Noise 1", "噪声 I", EffectCategory.ONE_D, EffectMood.AURORA, false, "54"),
        EffectInfo(55, "Noise 4", "噪声 IV", EffectCategory.ONE_D, EffectMood.AURORA, false, "55"),
        EffectInfo(56, "Colortwinkles", "彩屑闪烁", EffectCategory.ONE_D, EffectMood.AURORA, false, "56"),
        EffectInfo(57, "Lake", "湖光", EffectCategory.ONE_D, EffectMood.AURORA, false, "57"),
        EffectInfo(58, "Meteor", "流星", EffectCategory.ONE_D, EffectMood.AURORA, true, "58"),
        EffectInfo(59, "Copy Segment", "复制段落", EffectCategory.ONE_D, EffectMood.AURORA, false, "59"),
        EffectInfo(60, "Railway", "铁道", EffectCategory.ONE_D, EffectMood.AURORA, false, "60"),
        EffectInfo(61, "Ripple", "涟漪", EffectCategory.ONE_D, EffectMood.AURORA, true, "61"),
        EffectInfo(62, "Twinklefox", "萤火", EffectCategory.ONE_D, EffectMood.AURORA, false, "62"),
        EffectInfo(63, "Halloween Eyes", "万圣之眼", EffectCategory.ONE_D, EffectMood.AURORA, false, "63"),
        EffectInfo(64, "Solid Pattern", "纯色图案", EffectCategory.ONE_D, EffectMood.AURORA, true, "64"),
        EffectInfo(65, "Solid Pattern Tri", "三色图案", EffectCategory.ONE_D, EffectMood.AURORA, false, "65"),
        EffectInfo(66, "Spots", "光斑", EffectCategory.ONE_D, EffectMood.AURORA, false, "66"),
        EffectInfo(67, "Glitter", "碎钻", EffectCategory.ONE_D, EffectMood.AURORA, false, "67"),
        EffectInfo(68, "Candle", "烛光", EffectCategory.ONE_D, EffectMood.AURORA, true, "68"),
        EffectInfo(69, "Fireworks Starburst", "星爆焰火", EffectCategory.ONE_D, EffectMood.AURORA, false, "69"),
        EffectInfo(70, "Fireworks 1D", "线型焰火", EffectCategory.ONE_D, EffectMood.AURORA, false, "70"),
        EffectInfo(71, "Bouncing Balls", "弹跳球", EffectCategory.ONE_D, EffectMood.AURORA, true, "71"),
        EffectInfo(72, "Sinelon", "正弦彗星", EffectCategory.ONE_D, EffectMood.AURORA, true, "72"),
        EffectInfo(73, "Popcorn", "爆米花", EffectCategory.ONE_D, EffectMood.AURORA, false, "73"),
        EffectInfo(74, "Drip", "滴落", EffectCategory.ONE_D, EffectMood.AURORA, false, "74"),
        EffectInfo(75, "Plasma", "等离子", EffectCategory.ONE_D, EffectMood.AURORA, true, "75"),
        EffectInfo(76, "Percent", "百分比", EffectCategory.ONE_D, EffectMood.AURORA, false, "76"),
        EffectInfo(77, "Heartbeat", "心跳", EffectCategory.ONE_D, EffectMood.AURORA, false, "77"),
        EffectInfo(78, "Pacifica", "深海潮汐", EffectCategory.ONE_D, EffectMood.AURORA, false, "78"),
        EffectInfo(79, "Candle Multi", "多簇烛光", EffectCategory.ONE_D, EffectMood.AURORA, false, "79"),
        EffectInfo(80, "Solid Glitter", "纯色碎钻", EffectCategory.ONE_D, EffectMood.AURORA, true, "80"),
        EffectInfo(81, "Sunrise", "日出", EffectCategory.ONE_D, EffectMood.AURORA, false, "81"),
        EffectInfo(82, "Phased", "相位", EffectCategory.ONE_D, EffectMood.AURORA, false, "82"),
        EffectInfo(83, "Twinkleup", "上升星点", EffectCategory.ONE_D, EffectMood.AURORA, false, "83"),
        EffectInfo(84, "Noise Pal", "噪声调色", EffectCategory.ONE_D, EffectMood.AURORA, false, "84"),
        EffectInfo(85, "Sine", "正弦波", EffectCategory.ONE_D, EffectMood.AURORA, false, "85"),
        EffectInfo(86, "Flow", "流动", EffectCategory.ONE_D, EffectMood.AURORA, false, "86"),
        EffectInfo(87, "Chunchun", "啾啾", EffectCategory.ONE_D, EffectMood.AURORA, false, "87"),
        EffectInfo(88, "Dancing Shadows", "舞影", EffectCategory.ONE_D, EffectMood.AURORA, false, "88"),
        EffectInfo(89, "Washing Machine", "洗衣机", EffectCategory.ONE_D, EffectMood.AURORA, false, "89"),
        EffectInfo(90, "Rotozoomer", "旋转缩放", EffectCategory.TWO_D, EffectMood.AURORA, true, "90"),
        EffectInfo(91, "Blends", "混色", EffectCategory.ONE_D, EffectMood.AURORA, false, "91"),
        EffectInfo(92, "TV Simulator", "电视雪花", EffectCategory.ONE_D, EffectMood.AURORA, false, "92"),
        EffectInfo(93, "Spaceships", "飞船", EffectCategory.TWO_D, EffectMood.AURORA, false, "93"),
        EffectInfo(94, "Crazy Bees", "疯狂蜜蜂", EffectCategory.TWO_D, EffectMood.AURORA, false, "94"),
        EffectInfo(95, "Ghost Rider", "恶灵骑士", EffectCategory.TWO_D, EffectMood.AURORA, false, "95"),
        EffectInfo(96, "Blobs", "团块", EffectCategory.TWO_D, EffectMood.AURORA, false, "96"),
        EffectInfo(97, "Scrolling Text", "动态字幕", EffectCategory.ART, EffectMood.TEXT, true, "97"),
        EffectInfo(98, "Drift Rose", "玫瑰漂移", EffectCategory.TWO_D, EffectMood.AURORA, false, "98"),
        EffectInfo(99, "Distortion Waves", "扭曲波", EffectCategory.TWO_D, EffectMood.AURORA, false, "99"),
        EffectInfo(100, "Soap", "皂泡", EffectCategory.TWO_D, EffectMood.AURORA, false, "100"),
        EffectInfo(101, "Octopus", "章鱼", EffectCategory.TWO_D, EffectMood.AURORA, false, "101"),
        EffectInfo(102, "Waving Cell", "波动细胞", EffectCategory.TWO_D, EffectMood.AURORA, false, "102"),
        EffectInfo(103, "Pixels", "像素", EffectCategory.ONE_D, EffectMood.AURORA, false, "103"),
        EffectInfo(104, "Pixelwave", "像素波", EffectCategory.ONE_D, EffectMood.AURORA, false, "104"),
        EffectInfo(105, "Juggles", "流光飞舞", EffectCategory.ONE_D, EffectMood.AURORA, false, "105"),
        EffectInfo(106, "Matripix", "矩阵像素", EffectCategory.ONE_D, EffectMood.AURORA, false, "106"),
        EffectInfo(107, "Gravimeter", "重力表", EffectCategory.ONE_D, EffectMood.AURORA, false, "107"),
        EffectInfo(108, "Plasmoid", "等离子团", EffectCategory.ONE_D, EffectMood.AURORA, false, "108"),
        EffectInfo(109, "Puddles", "水洼", EffectCategory.ONE_D, EffectMood.AURORA, false, "109"),
        EffectInfo(110, "Midnoise", "中段噪声", EffectCategory.ONE_D, EffectMood.AURORA, false, "110"),
        EffectInfo(111, "Noisemeter", "噪声电平", EffectCategory.ONE_D, EffectMood.MUSIC, false, "111"),
        EffectInfo(112, "Freqwave", "频谱波", EffectCategory.ONE_D, EffectMood.MUSIC, false, "112"),
        EffectInfo(113, "GEQ", "音频律动", EffectCategory.TWO_D, EffectMood.MUSIC, true, "113"),
        EffectInfo(114, "Waterfall", "瀑布", EffectCategory.ONE_D, EffectMood.AURORA, false, "114"),
        EffectInfo(115, "Freqpixels", "频谱像素", EffectCategory.ONE_D, EffectMood.MUSIC, false, "115"),
        EffectInfo(116, "Noisefire", "噪声之火", EffectCategory.ONE_D, EffectMood.AURORA, false, "116"),
        EffectInfo(117, "Noisemove", "噪声游移", EffectCategory.ONE_D, EffectMood.AURORA, false, "117"),
        EffectInfo(118, "Noise2D", "二维噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "118"),
        EffectInfo(119, "Perlin Move", "柏林游移", EffectCategory.ONE_D, EffectMood.AURORA, false, "119"),
        EffectInfo(120, "Ripple Peak", "峰值涟漪", EffectCategory.ONE_D, EffectMood.MUSIC, false, "120"),
        EffectInfo(121, "Firenoise", "火焰噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "121"),
        EffectInfo(122, "Squared Swirl", "方块漩涡", EffectCategory.TWO_D, EffectMood.AURORA, false, "122"),
        EffectInfo(123, "PacMan", "吃豆人", EffectCategory.ONE_D, EffectMood.AURORA, true, "123"),
        EffectInfo(124, "DNA", "基因链", EffectCategory.TWO_D, EffectMood.AURORA, false, "124"),
        EffectInfo(125, "Matrix", "数字雨", EffectCategory.TWO_D, EffectMood.TEXT, true, "125"),
        EffectInfo(126, "Metaballs", "融球", EffectCategory.TWO_D, EffectMood.AURORA, false, "126"),
        EffectInfo(127, "Freqmap", "频谱映射", EffectCategory.ONE_D, EffectMood.MUSIC, false, "127"),
        EffectInfo(128, "Gravcenter", "重力中心", EffectCategory.ONE_D, EffectMood.AURORA, false, "128"),
        EffectInfo(129, "DJ Light", "打碟灯", EffectCategory.ONE_D, EffectMood.MUSIC, false, "129"),
        EffectInfo(130, "Funky Plank", "律动板", EffectCategory.TWO_D, EffectMood.AURORA, false, "130"),
        EffectInfo(131, "Shimmer", "微光", EffectCategory.ONE_D, EffectMood.AURORA, false, "131"),
        EffectInfo(132, "Pulser", "脉冲", EffectCategory.TWO_D, EffectMood.AURORA, false, "132"),
        EffectInfo(133, "Blurz", "模糊", EffectCategory.ONE_D, EffectMood.AURORA, false, "133"),
        EffectInfo(134, "Drift", "漂移", EffectCategory.TWO_D, EffectMood.AURORA, false, "134"),
        EffectInfo(135, "Waverly", "波涌", EffectCategory.TWO_D, EffectMood.AURORA, false, "135"),
        EffectInfo(136, "Sun Radiation", "日冕", EffectCategory.TWO_D, EffectMood.AURORA, false, "136"),
        EffectInfo(137, "Colored Bursts", "彩爆", EffectCategory.TWO_D, EffectMood.AURORA, false, "137"),
        EffectInfo(138, "Julia", "朱莉娅集", EffectCategory.TWO_D, EffectMood.AURORA, false, "138"),
        EffectInfo(139, "Game Of Life", "生命游戏", EffectCategory.TWO_D, EffectMood.AURORA, false, "139"),
        EffectInfo(140, "Tartan", "格纹", EffectCategory.TWO_D, EffectMood.AURORA, false, "140"),
        EffectInfo(141, "Polar Lights", "极地之光", EffectCategory.TWO_D, EffectMood.AURORA, false, "141"),
        EffectInfo(142, "Swirl", "漩涡", EffectCategory.TWO_D, EffectMood.AURORA, false, "142"),
        EffectInfo(143, "Lissajous", "李萨如", EffectCategory.TWO_D, EffectMood.AURORA, false, "143"),
        EffectInfo(144, "Frizzles", "蜷曲", EffectCategory.TWO_D, EffectMood.AURORA, false, "144"),
        EffectInfo(145, "Plasma Ball", "等离子球", EffectCategory.TWO_D, EffectMood.AURORA, false, "145"),
        EffectInfo(146, "Flow Stripe", "流光条纹", EffectCategory.ONE_D, EffectMood.AURORA, false, "146"),
        EffectInfo(147, "Hiphotic", "催眠", EffectCategory.TWO_D, EffectMood.AURORA, false, "147"),
        EffectInfo(148, "Sindots", "正弦点", EffectCategory.TWO_D, EffectMood.AURORA, false, "148"),
        EffectInfo(149, "DNA Spiral", "基因螺旋", EffectCategory.TWO_D, EffectMood.AURORA, false, "149"),
        EffectInfo(150, "Black Hole", "黑洞", EffectCategory.TWO_D, EffectMood.AURORA, false, "150"),
        EffectInfo(151, "Wavesins", "波正弦", EffectCategory.ONE_D, EffectMood.AURORA, false, "151"),
        EffectInfo(152, "Rocktaves", "摇滚波", EffectCategory.ONE_D, EffectMood.AURORA, false, "152"),
        EffectInfo(153, "Akemi", "阿凯米律动", EffectCategory.TWO_D, EffectMood.MUSIC, false, "153"),
        EffectInfo(154, "PS Volcano", "火山", EffectCategory.TWO_D, EffectMood.AURORA, false, "154"),
        EffectInfo(155, "PS Fire", "火焰", EffectCategory.TWO_D, EffectMood.AURORA, false, "155"),
        EffectInfo(156, "PS Fireworks", "粒子焰火", EffectCategory.TWO_D, EffectMood.AURORA, false, "156"),
        EffectInfo(157, "PS Vortex", "涡旋", EffectCategory.TWO_D, EffectMood.AURORA, false, "157"),
        EffectInfo(158, "PS Fuzzy Noise", "朦胧噪声", EffectCategory.TWO_D, EffectMood.AURORA, false, "158"),
        EffectInfo(159, "PS Ballpit", "球池", EffectCategory.TWO_D, EffectMood.AURORA, false, "159"),
        EffectInfo(160, "PS Box", "盒子", EffectCategory.TWO_D, EffectMood.AURORA, false, "160"),
        EffectInfo(161, "PS Attractor", "吸引子", EffectCategory.TWO_D, EffectMood.AURORA, false, "161"),
        EffectInfo(162, "PS Impact", "冲击", EffectCategory.TWO_D, EffectMood.AURORA, false, "162"),
        EffectInfo(163, "PS Waterfall", "粒子瀑布", EffectCategory.TWO_D, EffectMood.AURORA, false, "163"),
        EffectInfo(164, "PS Spray", "喷溅", EffectCategory.TWO_D, EffectMood.AURORA, false, "164"),
        EffectInfo(165, "PS GEQ 2D", "二维频谱", EffectCategory.TWO_D, EffectMood.MUSIC, false, "165"),
        EffectInfo(166, "PS GEQ Nova", "频谱新星", EffectCategory.TWO_D, EffectMood.MUSIC, false, "166"),
        EffectInfo(167, "PS Ghost Rider", "粒子恶灵", EffectCategory.TWO_D, EffectMood.AURORA, false, "167"),
        EffectInfo(168, "PS Blobs", "粒子团块", EffectCategory.TWO_D, EffectMood.AURORA, false, "168"),
        EffectInfo(169, "PS DripDrop", "滴答", EffectCategory.TWO_D, EffectMood.AURORA, false, "169"),
        EffectInfo(170, "PS Pinball", "弹珠", EffectCategory.TWO_D, EffectMood.AURORA, false, "170"),
        EffectInfo(171, "PS Dancing Shadows", "粒子舞影", EffectCategory.TWO_D, EffectMood.AURORA, false, "171"),
        EffectInfo(172, "PS Fireworks 1D", "线型粒子焰火", EffectCategory.TWO_D, EffectMood.AURORA, false, "172"),
        EffectInfo(173, "PS Sparkler", "手持烟花", EffectCategory.TWO_D, EffectMood.AURORA, false, "173"),
        EffectInfo(174, "PS Hourglass", "沙漏", EffectCategory.TWO_D, EffectMood.AURORA, false, "174"),
        EffectInfo(175, "PS Spray 1D", "线型喷溅", EffectCategory.TWO_D, EffectMood.AURORA, false, "175"),
        EffectInfo(176, "PS 1D Balance", "线型平衡", EffectCategory.TWO_D, EffectMood.AURORA, false, "176"),
        EffectInfo(177, "PS Chase", "粒子追逐", EffectCategory.TWO_D, EffectMood.AURORA, false, "177"),
        EffectInfo(178, "PS Starburst", "星芒", EffectCategory.TWO_D, EffectMood.AURORA, false, "178"),
        EffectInfo(179, "PS GEQ 1D", "线型频谱", EffectCategory.TWO_D, EffectMood.MUSIC, false, "179"),
        EffectInfo(180, "PS Fire 1D", "线型火焰", EffectCategory.TWO_D, EffectMood.AURORA, false, "180"),
        EffectInfo(181, "PS Sonic Stream", "声波激流", EffectCategory.TWO_D, EffectMood.MUSIC, false, "181"),
        EffectInfo(182, "PS Sonic Boom", "声爆", EffectCategory.TWO_D, EffectMood.MUSIC, false, "182"),
        EffectInfo(183, "PS Springy", "弹簧", EffectCategory.TWO_D, EffectMood.AURORA, false, "183"),
        EffectInfo(184, "PS Galaxy", "星海", EffectCategory.TWO_D, EffectMood.AURORA, false, "184"),
        EffectInfo(185, "Color Clouds", "彩云", EffectCategory.ONE_D, EffectMood.AURORA, false, "185"),
        EffectInfo(186, "Slow Transition", "缓慢流转", EffectCategory.ONE_D, EffectMood.AURORA, false, "186"),
        EffectInfo(187, "Analog Clock 2D", "指针时钟", EffectCategory.TWO_D, EffectMood.TEXT, false, "187"),
        EffectInfo(188, "极光之舞", "极光之舞", EffectCategory.TWO_D, EffectMood.AURORA, true, "188"),
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
