"""从固件灯效清单生成 App 侧的 Kotlin 灯效目录。

数据源: firmware/effects_enabled.json（由固件源码解析得到，idx 即运行时 fx 值）
输出:   app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/effects/EffectCatalog.kt

「律动模式」（mood）是 App 侧的产品分类，固件没有这个概念：
    AURORA 幻彩极光 —— 一切以色彩流动为主的氛围效果
    MUSIC  音乐律动 —— 由音频频谱驱动的效果（GEQ、频谱、声波类）
    TEXT   动态字幕 —— 直接显示用户内容的字画（文字、图片、时钟、数字雨）
    SLEEP  睡眠夜灯 —— 低亮度、低刺激的慢节奏效果
分类按效果 id 白名单硬编码，宁可漏收也不猜。

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
    "Blink", "Breathe", "Wipe", "Colorloop", "Rainbow", "Twinkle", "Chase",
    "Scanner", "Fireworks", "Meteor", "Ripple", "Lighthouse", "Fire 2012",
    "Solid Glitter", "Multi Comet", "Bouncing Balls", "Sinelon", "Plasma",
    "PacMan", "Rotozoomer", "Scrolling Text", "GEQ", "Matrix", "Image",
    "Solid Pattern", "Aurora", "Candle",
}

# 「音乐律动」：由音频频谱驱动的效果
MUSIC_IDS = {
    48,   # Bpm
    102, 103, 105, 109, 110, 112,  # Noisemeter / Freqwave / Freqpixels / Ripple Peak / Freqmap / DJ Light
    126,  # GEQ
    155,  # Akemi
    167, 168, 182,  # PS GEQ 2D / PS GEQ Nova / PS GEQ 1D
    184, 185,       # PS Sonic Stream / PS Sonic Boom
}

# 「动态字幕」：直接显示用户内容（文字、图片、时钟、数字雨）
TEXT_IDS = {38, 123, 127, 132}

# 「睡眠夜灯」：低亮度、慢节奏，适合睡前
SLEEP_IDS = {
    2, 10, 13, 15, 52, 53, 57, 72, 80, 81, 82, 84, 93,
}

# 中文显示名。没收录的灯效直接用固件英文名，不做机器翻译。
NAME_ZH = {
    0: "复制段落", 1: "明灭", 2: "呼吸", 3: "擦拭", 4: "随机彩", 5: "扫光",
    6: "动感", 7: "循环彩", 8: "七彩流光", 9: "扫描", 10: "渐隐", 11: "剧院追光",
    12: "奔走", 13: "星闪", 14: "溶解", 15: "暗夜星屑", 16: "频闪", 17: "疾速频闪",
    18: "安卓", 19: "追逐", 20: "追逐闪白", 21: "乱序追闪", 22: "彩虹奔流", 23: "缤纷",
    24: "红绿灯", 25: "极光", 26: "彩云", 27: "溪流", 28: "巡航", 29: "落雨",
    30: "色浪", 31: "焰火", 32: "俄罗斯方块", 33: "烛火摇曳", 34: "渐变", 35: "仙子尘",
    36: "双点", 37: "仙尘闪烁", 38: "自定义图片", 39: "三重追逐", 40: "三色擦拭", 41: "三色渐隐",
    42: "闪电", 43: "监护仪", 44: "溪流 II", 45: "振荡", 46: "抛球", 47: "调色板",
    48: "节拍", 49: "噪声填充", 50: "噪声 I", 51: "噪声 IV", 52: "彩屑闪烁", 53: "湖光",
    54: "流星", 55: "铁道", 56: "涟漪", 57: "萤火", 58: "万圣之眼", 59: "纯色图案",
    60: "三色图案", 61: "光斑", 62: "灯塔", 63: "壁炉篝火", 64: "线型焰火", 65: "闪耀",
    66: "碎钻", 67: "纯色碎钻", 68: "多重彗尾", 69: "滚球", 70: "星爆焰火", 71: "舞影",
    72: "烛光", 73: "弹跳球", 74: "爆米花", 75: "滴落", 76: "正弦彗星", 77: "等离子",
    78: "百分比", 79: "心跳", 80: "深海潮汐", 81: "多簇烛光", 82: "日出", 83: "相位",
    84: "上升星点", 85: "噪声调色", 86: "正弦波", 87: "流动", 88: "啾啾", 89: "洗衣机",
    90: "混色", 91: "电视雪花", 92: "吃豆人", 93: "缓慢流转", 94: "像素", 95: "像素波",
    96: "流光飞舞", 97: "矩阵像素", 98: "重力表", 99: "等离子团", 100: "水洼", 101: "中段噪声",
    102: "噪声电平", 103: "频谱波", 104: "瀑布", 105: "频谱像素", 106: "噪声之火", 107: "噪声游移",
    108: "柏林游移", 109: "峰值涟漪", 110: "频谱映射", 111: "重力中心", 112: "打碟灯", 113: "模糊",
    114: "流光条纹", 115: "波正弦", 116: "摇滚波", 117: "微光", 118: "旋转缩放", 119: "飞船",
    120: "疯狂蜜蜂", 121: "恶灵骑士", 122: "团块", 123: "动态字幕", 124: "玫瑰漂移", 125: "扭曲波",
    126: "音频律动", 127: "指针时钟", 128: "二维噪声", 129: "火焰噪声", 130: "方块漩涡", 131: "基因链",
    132: "数字雨", 133: "融球", 134: "律动板", 135: "脉冲", 136: "漂移", 137: "波涌",
    138: "日冕", 139: "彩爆", 140: "朱莉娅集", 141: "生命游戏", 142: "格纹", 143: "极地之光",
    144: "漩涡", 145: "李萨如", 146: "蜷曲", 147: "等离子球", 148: "催眠", 149: "正弦点",
    150: "基因螺旋", 151: "黑洞", 152: "皂泡", 153: "章鱼", 154: "波动细胞", 155: "阿凯米律动",
    156: "火山", 157: "火焰", 158: "粒子焰火", 159: "涡旋", 160: "朦胧噪声", 161: "球池",
    162: "盒子", 163: "吸引子", 164: "冲击", 165: "粒子瀑布", 166: "喷溅", 167: "二维频谱",
    168: "频谱新星", 169: "粒子恶灵", 170: "粒子团块", 171: "星海", 172: "滴答", 173: "弹珠",
    174: "粒子舞影", 175: "线型粒子焰火", 176: "手持烟花", 177: "沙漏", 178: "线型喷溅", 179: "线型平衡",
    180: "粒子追逐", 181: "星芒", 182: "线型频谱", 183: "线型火焰", 184: "声波激流", 185: "声爆",
    186: "弹簧",
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
    if fx.startswith(("FX_MODE_2D", "FX_MODE_PARTICLE", "FX_MODE_PS")):
        return "TWO_D"
    return "ONE_D"


def mood_of(entry: dict) -> str:
    idx = entry["idx"]
    if idx in TEXT_IDS:
        return "TEXT"
    if idx in MUSIC_IDS:
        return "MUSIC"
    if idx in SLEEP_IDS:
        return "SLEEP"
    return "AURORA"


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main() -> int:
    if not os.path.exists(SOURCE):
        print(f"缺少数据源: {SOURCE}", file=sys.stderr)
        return 1

    with open(SOURCE, encoding="utf-8") as handle:
        effects = json.load(handle)

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
        " * 律动模式：App 侧的产品分类，对应首页的四个胶囊与灯效库的四个 Tab。",
        " *",
        " * 固件没有这个概念，分类由 [EffectCatalog] 的 id 白名单决定。",
        " */",
        "enum class EffectMood { AURORA, MUSIC, TEXT, SLEEP }",
        "",
        "/**",
        " * 一个固件灯效。",
        " *",
        " * @property id 运行时 fx 值，可直接写入 `seg.fx`。",
        " * @property name 固件提供的英文名称（`/json/effects` 同一份数据，此处固化以便离线展示）。",
        " * @property nameZh 中文显示名；未收录时与 [name] 相同。",
        " * @property category 见 [EffectCategory]。",
        " * @property mood 首页四胶囊的分组，见 [EffectMood]。",
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
        zh = NAME_ZH.get(idx, name)
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
    missing_zh = [e["name"] for e in effects if e["idx"] not in NAME_ZH]
    print(f"共 {len(effects)} 个灯效: {counts}, 常用 {len(POPULAR_NAMES)}, 预览 {len(effects)}")
    print(f"律动模式: {moods}")
    if missing_zh:
        print(f"未收录中文名: {missing_zh}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
