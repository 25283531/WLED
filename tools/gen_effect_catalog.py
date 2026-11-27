"""从固件灯效清单生成 App 侧的 Kotlin 灯效目录。

数据源: firmware/effects_enabled.json（由 tools/gen_effect_index.py 从固件源码解析）
        —— 槽位与固件运行时 fx 值严格一一对应，`idx` 就是可以直接写进 `seg.fx` 的值。
输出:   app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/effects/EffectCatalog.kt

「律动模式」（mood）是 App 侧的产品分类，固件没有这个概念：
    AURORA 幻彩极光 —— 一切以色彩流动为主的氛围效果
    MUSIC  音乐律动 —— 由音频频谱驱动的效果（GEQ、频谱、声波类）
    TEXT   动态字幕 —— 直接显示用户内容的字画（文字、图片、时钟、数字雨）
「睡眠夜灯」不是分类，而是设备页的一键暖光动作，故不在此建模。
分类按固件宏名（FX_MODE_*）白名单硬编码，与 id 解耦，宁可漏收也不猜。

用法:
    python tools/gen_effect_catalog.py
"""

from __future__ import annotations

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "firmware", "effects_enabled.json")
TARGET = os.path.join(
    ROOT,
    "app", "app", "src", "main", "java", "ca", "cgagnier", "wlednativeandroid",
    "ui", "effects", "EffectCatalog.kt",
)

# 「字画」：自定义图片 + 自定义文字（固件里只有这两个效果直接消费用户内容）
ART_EFFECT_NAMES = {"Image", "Scrolling Text"}

# 「常用」：按实际使用频率人工挑选，覆盖开关灯之外最常触碰的效果
POPULAR_NAMES = {
    "Blink", "Breathe", "Colorloop", "Rainbow", "Chase",
    "Fireworks", "Meteor", "Ripple",
    "Bouncing Balls", "Plasma",
    "PacMan", "Scrolling Text", "Matrix", "Image",
    "极光之舞",
}

# 「音乐律动」：由音频频谱驱动的效果
MUSIC_IDS = {
    "FX_MODE_FREQPIXELS", "FX_MODE_PS1DGEQ",
    "FX_MODE_STAGELIGHT", "FX_MODE_TRIBREATH", "FX_MODE_AUDIOSPIRAL",
    "FX_MODE_SONICPULSE", "FX_MODE_BASSLAVA", "FX_MODE_SPECTRUMLAMP",
    "FX_MODE_AUDIOSOLUTION",
}

# 「动态字幕」：直接显示用户内容（文字、图片、时钟、数字雨）
TEXT_IDS = {
    "FX_MODE_IMAGE", "FX_MODE_2DSCROLLTEXT", "FX_MODE_2DMATRIX",
}

# 中文显示名。没收录的灯效直接用固件英文名，不做机器翻译。
NAME_ZH = {
    'FX_MODE_2DANALOGCLOCK': '指针时钟', 'FX_MODE_2DBLACKHOLE': '黑洞',
    'FX_MODE_2DBLOBS': '团块', 'FX_MODE_2DCOLOREDBURSTS': '彩爆', 'FX_MODE_2DCRAZYBEES': '疯狂蜜蜂',
    'FX_MODE_2DDISTORTIONWAVES': '扭曲波', 'FX_MODE_2DDNA': '基因链', 'FX_MODE_2DDNASPIRAL': '基因螺旋',
    'FX_MODE_2DDRIFT': '漂移', 'FX_MODE_2DDRIFTROSE': '玫瑰漂移', 'FX_MODE_2DFIRENOISE': '火焰噪声',
    'FX_MODE_2DFRIZZLES': '蜷曲', 'FX_MODE_2DFUNKYPLANK': '律动板', 'FX_MODE_2DGAMEOFLIFE': '生命游戏',
    'FX_MODE_2DGHOSTRIDER': '恶灵骑士', 'FX_MODE_2DHIPHOTIC': '催眠',
    'FX_MODE_2DJULIA': '朱莉娅集', 'FX_MODE_2DLISSAJOUS': '李萨如', 'FX_MODE_2DMATRIX': '数字雨',
    'FX_MODE_2DMETABALLS': '融球', 'FX_MODE_2DNOISE': '二维噪声', 'FX_MODE_2DOCTOPUS': '章鱼',
    'FX_MODE_2DPLASMABALL': '等离子球', 'FX_MODE_2DPLASMAROTOZOOM': '旋转缩放', 'FX_MODE_2DPOLARLIGHTS': '极地之光',
    'FX_MODE_2DPULSER': '脉冲', 'FX_MODE_2DSCROLLTEXT': '动态字幕', 'FX_MODE_2DSINDOTS': '正弦点',
    'FX_MODE_2DSOAP': '皂泡', 'FX_MODE_2DSPACESHIPS': '飞船', 'FX_MODE_2DSQUAREDSWIRL': '方块漩涡',
    'FX_MODE_2DSUNRADIATION': '日冕', 'FX_MODE_2DSWIRL': '漩涡', 'FX_MODE_2DTARTAN': '格纹',
    'FX_MODE_2DWAVERLY': '波涌', 'FX_MODE_2DWAVINGCELL': '波动细胞', 'FX_MODE_ANDROID': '安卓',
    'FX_MODE_AURORA': '极光', 'FX_MODE_BLENDS': '混色', 'FX_MODE_BLINK': '明灭', 'FX_MODE_BLURZ': '模糊',
    'FX_MODE_BOUNCINGBALLS': '弹跳球', 'FX_MODE_BREATH': '呼吸', 'FX_MODE_CANDLE': '烛光',
    'FX_MODE_CANDLE_MULTI': '多簇烛光', 'FX_MODE_CHASE_COLOR': '追逐', 'FX_MODE_CHASE_FLASH': '追逐闪白',
    'FX_MODE_CHASE_FLASH_RANDOM': '乱序追闪', 'FX_MODE_CHASE_RAINBOW_WHITE': '彩虹奔流', 'FX_MODE_CHUNCHUN': '啾啾',
    'FX_MODE_COLORCLOUDS': '彩云', 'FX_MODE_COLORFUL': '缤纷', 'FX_MODE_COLORTWINKLE': '彩屑闪烁',
    'FX_MODE_COLORWAVES': '色浪', 'FX_MODE_COLOR_SWEEP': '扫光', 'FX_MODE_COLOR_WIPE': '擦拭',
    'FX_MODE_COMET': '灯塔', 'FX_MODE_COPY': '复制段落', 'FX_MODE_DANCING_SHADOWS': '舞影', 'FX_MODE_DISSOLVE': '溶解',
    'FX_MODE_DRIP': '滴落', 'FX_MODE_DYNAMIC': '动感',
    'FX_MODE_EXPLODING_FIREWORKS': '线型焰火', 'FX_MODE_FADE': '渐隐', 'FX_MODE_FAIRY': '仙子尘',
    'FX_MODE_FAIRYTWINKLE': '仙尘闪烁', 'FX_MODE_FILLNOISE8': '噪声填充', 'FX_MODE_FIREWORKS': '焰火',
    'FX_MODE_FIRE_2012': '壁炉篝火', 'FX_MODE_FIRE_FLICKER': '烛火摇曳', 'FX_MODE_FLASH_SPARKLE': '暗夜星屑',
    'FX_MODE_FLOW': '流动', 'FX_MODE_FLOWSTRIPE': '流光条纹',
    'FX_MODE_FREQPIXELS': '频谱像素', 'FX_MODE_GLITTER': '碎钻',
    'FX_MODE_GRADIENT': '渐变', 'FX_MODE_GRAVCENTER': '重力中心', 'FX_MODE_GRAVIMETER': '重力表',
    'FX_MODE_HALLOWEEN_EYES': '万圣之眼', 'FX_MODE_HEARTBEAT': '心跳', 'FX_MODE_ICU': '监护仪',
    'FX_MODE_IMAGE': '自定义图片', 'FX_MODE_JUGGLE': '抛球', 'FX_MODE_JUGGLES': '流光飞舞', 'FX_MODE_LAKE': '湖光',
    'FX_MODE_LARSON_SCANNER': '巡航', 'FX_MODE_LIGHTNING': '闪电', 'FX_MODE_LUMI_AURORA': '极光之舞',
    'FX_MODE_LUMI_FOREST': '林间萤光', 'FX_MODE_LUMI_DUSK': '暮色晚霞', 'FX_MODE_LUMI_POLAR': '极地冰川',
    'FX_MODE_LUMI_HEARTH': '壁炉火焰', 'FX_MODE_LUMI_LAVA': '熔岩灯', 'FX_MODE_LUMI_CAUSTICS': '深海波光',
    'FX_MODE_MATRIPIX': '矩阵像素', 'FX_MODE_METEOR': '流星', 'FX_MODE_MIDNOISE': '中段噪声',
    'FX_MODE_MULTI_COMET': '多重彗尾', 'FX_MODE_MULTI_STROBE': '疾速频闪', 'FX_MODE_NOISE16_1': '噪声 I',
    'FX_MODE_NOISE16_4': '噪声 IV', 'FX_MODE_NOISEFIRE': '噪声之火',
    'FX_MODE_NOISEMOVE': '噪声游移', 'FX_MODE_NOISEPAL': '噪声调色', 'FX_MODE_OSCILLATE': '振荡',
    'FX_MODE_PACIFICA': '深海潮汐', 'FX_MODE_PACMAN': '吃豆人', 'FX_MODE_PALETTE': '调色板',
    'FX_MODE_PARTICLEATTRACTOR': '吸引子', 'FX_MODE_PARTICLEBLOBS': '粒子团块', 'FX_MODE_PARTICLEBOX': '盒子',
    'FX_MODE_PARTICLEFIRE': '火焰', 'FX_MODE_PARTICLEFIREWORKS': '粒子焰火',
    'FX_MODE_PARTICLEGALAXY': '星海', 'FX_MODE_PARTICLEGHOSTRIDER': '粒子恶灵', 'FX_MODE_PARTICLEIMPACT': '冲击',
    'FX_MODE_PARTICLEPERLIN': '朦胧噪声', 'FX_MODE_PARTICLEPIT': '球池',
    'FX_MODE_PARTICLESPRAY': '喷溅', 'FX_MODE_PARTICLEVOLCANO': '火山', 'FX_MODE_PARTICLEVORTEX': '涡旋',
    'FX_MODE_PARTICLEWATERFALL': '粒子瀑布', 'FX_MODE_PERCENT': '百分比', 'FX_MODE_PERLINMOVE': '柏林游移',
    'FX_MODE_PHASED': '相位', 'FX_MODE_PIXELS': '像素', 'FX_MODE_PIXELWAVE': '像素波', 'FX_MODE_PLASMA': '等离子',
    'FX_MODE_PLASMOID': '等离子团', 'FX_MODE_POPCORN': '爆米花', 'FX_MODE_PS1DGEQ': '线型频谱',
    'FX_MODE_PS1DSPRAY': '线型喷溅',
    'FX_MODE_PS1DSPRINGY': '弹簧', 'FX_MODE_PSBALANCE': '线型平衡', 'FX_MODE_PSCHASE': '粒子追逐',
    'FX_MODE_PSDANCINGSHADOWS': '粒子舞影', 'FX_MODE_PSDRIP': '滴答', 'FX_MODE_PSFIRE1D': '线型火焰',
    'FX_MODE_PSFIREWORKS1D': '线型粒子焰火', 'FX_MODE_PSHOURGLASS': '沙漏', 'FX_MODE_PSPINBALL': '弹珠',
    'FX_MODE_PSSPARKLER': '手持烟花', 'FX_MODE_PSSTARBURST': '星芒', 'FX_MODE_PUDDLES': '水洼',
    'FX_MODE_RAILWAY': '铁道', 'FX_MODE_RAIN': '落雨', 'FX_MODE_RAINBOW': '循环彩', 'FX_MODE_RAINBOW_CYCLE': '七彩流光',
    'FX_MODE_RANDOM_CHASE': '溪流 II', 'FX_MODE_RANDOM_COLOR': '随机彩', 'FX_MODE_RIPPLE': '涟漪',
    'FX_MODE_ROCKTAVES': '摇滚波', 'FX_MODE_ROLLINGBALLS': '滚球',
    'FX_MODE_RUNNING_LIGHTS': '奔走', 'FX_MODE_RUNNING_RANDOM': '溪流', 'FX_MODE_SCAN': '扫描',
    'FX_MODE_SHIMMER': '微光', 'FX_MODE_SINELON': '正弦彗星', 'FX_MODE_SINEWAVE': '正弦波',
    'FX_MODE_SLOW_TRANSITION': '缓慢流转', 'FX_MODE_SOLID_GLITTER': '纯色碎钻', 'FX_MODE_SPARKLE': '闪耀',
    'FX_MODE_SPOTS': '光斑', 'FX_MODE_STARBURST': '星爆焰火', 'FX_MODE_STATIC': '纯色',
    'FX_MODE_STATIC_PATTERN': '纯色图案', 'FX_MODE_STROBE': '频闪', 'FX_MODE_SUNRISE': '日出',
    'FX_MODE_TETRIX': '俄罗斯方块', 'FX_MODE_THEATER_CHASE': '剧院追光', 'FX_MODE_TRAFFIC_LIGHT': '红绿灯',
    'FX_MODE_TRICOLOR_CHASE': '三重追逐', 'FX_MODE_TRICOLOR_FADE': '三色渐隐', 'FX_MODE_TRICOLOR_WIPE': '三色擦拭',
    'FX_MODE_TRI_STATIC_PATTERN': '三色图案', 'FX_MODE_TV_SIMULATOR': '电视雪花', 'FX_MODE_TWINKLE': '星闪',
    'FX_MODE_TWINKLEFOX': '萤火', 'FX_MODE_TWINKLEUP': '上升星点', 'FX_MODE_TWO_DOTS': '双点',
    'FX_MODE_WASHING_MACHINE': '洗衣机', 'FX_MODE_WATERFALL': '瀑布', 'FX_MODE_WAVESINS': '波正弦',
    'FX_MODE_STAGELIGHT': '舞台灯', 'FX_MODE_TRIBREATH': '三频呼吸', 'FX_MODE_AUDIOSPIRAL': '音频螺旋',
    'FX_MODE_SONICPULSE': '声波脉冲', 'FX_MODE_BASSLAVA': '低频熔岩', 'FX_MODE_SPECTRUMLAMP': '频谱灯',
    'FX_MODE_AUDIOSOLUTION': '音律溶液',
}

CATEGORY_CONST = {
    "ONE_D": "ONE_D",
    "TWO_D": "TWO_D",
    "ART": "ART",
}


def categorize(entry: dict) -> str:
    name = entry["name"]
    fx = entry["fx"]
    if name in ART_EFFECT_NAMES:
        return "ART"
    # FX_MODE_LUMI_AURORA 是自定义命名的 2D 效果，用 setPixelColorXY 逐点绘制，
    # 宏名不带 2D 前缀，只能靠白名单识别。LUMI 系列同理。
    if fx.startswith("FX_MODE_LUMI"):
        return "TWO_D"
    if fx.startswith(("FX_MODE_2D", "FX_MODE_PARTICLE", "FX_MODE_PS")):
        return "TWO_D"
    return "ONE_D"


def mood_of(entry: dict) -> str:
    fx = entry["fx"]
    if fx in TEXT_IDS:
        return "TEXT"
    if fx in MUSIC_IDS:
        return "MUSIC"
    return "AURORA"


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main() -> int:
    if not os.path.exists(SOURCE):
        print(f"缺少数据源: {SOURCE}", file=sys.stderr)
        return 1

    with open(SOURCE, encoding="utf-8") as handle:
        raw = json.load(handle)

    # 清单是槽位表，未被固件注册的槽位是 null（固件里是 "RSVD" 占位）。
    # 只有真正注册了的效果才会出现在 App 的灯效库里。
    effects = [e for e in raw if e and e.get("name")]

    seen_names = {e["name"] for e in effects}
    missing = (ART_EFFECT_NAMES | POPULAR_NAMES) - seen_names
    if missing:
        print(f"警告: 以下名称在固件清单中不存在 -> {sorted(missing)}", file=sys.stderr)

    lines = [
        "// 由 tools/gen_effect_catalog.py 自动生成，请勿手工编辑。",
        "// 数据源: firmware/effects_enabled.json（idx 即 WLED 运行时 fx 值）",
        "package ca.cgagnier.wlednativeandroid.ui.effects",
        "",
        "/** 灯效分类。1D 与 2D 由固件效果类型决定，「字画」是能直接显示用户内容的效果子集。 */",
        "enum class EffectCategory { ONE_D, TWO_D, ART }",
        "",
        "/**",
        " * 律动模式：App 侧的产品分类，对应设备页的律动胶囊与灯效库的 Tab。",
        " *",
        " * 固件没有这个概念，分类由 [EffectCatalog] 的 id 白名单决定。",
        " */",
        "enum class EffectMood { AURORA, MUSIC, TEXT }",
        "",
        "/**",
        " * 一个固件灯效。",
        " *",
        " * @property id 运行时 fx 值，可直接写入 `seg.fx`。",
        " * @property name 固件提供的英文名称（`/json/effects` 同一份数据，此处固化以便离线展示）。",
        " * @property nameZh 中文显示名；未收录时与 [name] 相同。",
        " * @property category 见 [EffectCategory]。",
        " * @property mood 律动胶囊的分组，见 [EffectMood]。",
        " * @property isPopular 是否列入「常用」。",
        " * @property preview 预览 GIF 的资源名（assets/effect_previews/<preview>.gif），即效果 id。",
        " */",
        "data class EffectInfo(",
        "    val id: Int,",
        "    val name: String,",
        "    val nameZh: String,",
        "    val category: EffectCategory,",
        "    val mood: EffectMood,",
        "    val isPopular: Boolean,",
        "    val preview: String?,",
        ") {",
        "    val hasPreview: Boolean get() = preview != null",
        "",
        "    /** 有中文名时优先显示中文，英文名作为副标题。 */",
        "    val hasChineseName: Boolean get() = nameZh != name",
        "}",
        "",
        "object EffectCatalog {",
        "    val all: List<EffectInfo> = listOf(",
    ]

    for entry in effects:
        name = entry["name"]
        idx = entry["idx"]
        category = categorize(entry)
        popular = "true" if name in POPULAR_NAMES else "false"
        # 预览图以效果 id 命名，与固件 fx 值一一对应，杜绝「名字对、图不对」
        # 预览图以效果 id 命名，与固件 fx 值一一对应，杜绝「名字对、图不对」
        preview_literal = kotlin_string(str(idx))
        zh = NAME_ZH.get(entry["fx"], name)
        lines.append(
            f"        EffectInfo({idx}, {kotlin_string(name)}, {kotlin_string(zh)}, "
            f"EffectCategory.{CATEGORY_CONST[category]}, EffectMood.{mood_of(entry)}, "
            f"{popular}, {preview_literal}),"
        )

    lines += [
        "    )",
        "",
        "    private val byId: Map<Int, EffectInfo> = all.associateBy { it.id }",
        "",
        "    fun find(id: Int?): EffectInfo? = id?.let { byId[it] }",
        "",
        "    fun nameOf(id: Int?): String? = find(id)?.name",
        "",
        "    /** 按固件英文名反查灯效。业务代码不写死 id —— 固件插入新效果后 id 会整体前移。 */",
        "    fun byName(name: String): EffectInfo? = all.firstOrNull { it.name == name.trim() }",
        "",
        "    val popular: List<EffectInfo> = all.filter { it.isPopular }",
        "",
        "    fun byCategory(category: EffectCategory): List<EffectInfo> = all.filter { it.category == category }",
        "",
        "    fun byMood(mood: EffectMood): List<EffectInfo> = all.filter { it.mood == mood }",
        "}",
        "",
    ]

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))

    counts = {}
    for entry in effects:
        key = categorize(entry)
        counts[key] = counts.get(key, 0) + 1
    print(f"已生成 {TARGET}")
    moods = {}
    for entry in effects:
        key = mood_of(entry)
        moods[key] = moods.get(key, 0) + 1
    missing_zh = [e["name"] for e in effects if e["fx"] not in NAME_ZH]
    print(f"共 {len(effects)} 个灯效: {counts}, 常用 {len(POPULAR_NAMES)}, 预览 {len(effects)}")
    print(f"律动模式: {moods}")
    if missing_zh:
        print(f"未收录中文名: {missing_zh}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
