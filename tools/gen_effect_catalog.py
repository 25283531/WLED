"""从固件灯效清单生成 App 侧的 Kotlin 灯效目录。

数据源: firmware/effects_enabled.json（由 tools/gen_effect_index.py 从固件源码解析）
        —— 槽位与固件运行时 fx 值严格一一对应，`idx` 就是可以直接写进 `seg.fx` 的值。
输出:   app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/effects/EffectCatalog.kt

「律动模式」（mood）是 App 侧的产品分类，固件没有这个概念：
    AURORA 幻彩极光 —— 一切以色彩流动为主的氛围效果
    MUSIC  音乐律动 —— 由音频频谱驱动的效果（GEQ、频谱、声波类）
    TEXT   动态字幕 —— 直接显示用户内容的字画（文字、图片、时钟、数字雨）
「睡眠夜灯」不是分类，而是设备页的一键暖光动作，故不在此建模。
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
    "Solid Pattern", "Aurora", "Candle", "极光之舞",
}

# 「音乐律动」：由音频频谱驱动的效果
MUSIC_IDS = {68, 136, 137, 139, 141, 148, 155, 159, 186, 198, 199, 212, 214, 215}

# 「动态字幕」：直接显示用户内容（文字、图片、时钟、数字雨）
TEXT_IDS = {53, 122, 153, 220}

# 中文显示名。没收录的灯效直接用固件英文名，不做机器翻译。
NAME_ZH = {
    0: '纯色', 1: '明灭', 2: '呼吸', 3: '擦拭', 5: '随机彩', 6: '扫光', 7: '动感', 8: '循环彩', 9: '七彩流光',
    10: '扫描', 12: '渐隐', 13: '剧院追光', 15: '奔走', 17: '星闪', 18: '溶解', 20: '闪耀', 21: '暗夜星屑',
    23: '频闪', 25: '疾速频闪', 27: '安卓', 28: '追逐', 31: '追逐闪白', 32: '乱序追闪', 33: '彩虹奔流', 34: '缤纷',
    35: '红绿灯', 38: '极光', 39: '溪流', 40: '巡航', 41: '灯塔', 42: '焰火', 43: '落雨', 44: '俄罗斯方块',
    45: '烛火摇曳', 46: '渐变', 48: '滚球', 49: '仙子尘', 50: '双点', 51: '仙尘闪烁', 53: '自定义图片', 54: '三重追逐',
    55: '三色擦拭', 56: '三色渐隐', 57: '闪电', 58: '监护仪', 59: '多重彗尾', 61: '溪流 II', 62: '振荡', 64: '抛球',
    65: '调色板', 66: '壁炉篝火', 67: '色浪', 68: '节拍', 69: '噪声填充', 70: '噪声 I', 73: '噪声 IV', 74: '彩屑闪烁',
    75: '湖光', 76: '流星', 77: '复制段落', 78: '铁道', 79: '涟漪', 80: '萤火', 82: '万圣之眼', 83: '纯色图案',
    84: '三色图案', 85: '光斑', 87: '碎钻', 88: '烛光', 89: '星爆焰火', 90: '线型焰火', 91: '弹跳球', 92: '正弦彗星',
    95: '爆米花', 96: '滴落', 97: '等离子', 98: '百分比', 100: '心跳', 101: '深海潮汐', 102: '多簇烛光', 103: '纯色碎钻',
    104: '日出', 105: '相位', 106: '上升星点', 107: '噪声调色', 108: '正弦波', 110: '流动', 111: '啾啾', 112: '舞影',
    113: '洗衣机', 114: '旋转缩放', 115: '混色', 116: '电视雪花', 118: '飞船', 119: '疯狂蜜蜂', 120: '恶灵骑士',
    121: '团块', 122: '动态字幕', 123: '玫瑰漂移', 124: '扭曲波', 125: '皂泡', 126: '章鱼', 127: '波动细胞',
    128: '像素', 129: '像素波', 130: '流光飞舞', 131: '矩阵像素', 132: '重力表', 133: '等离子团', 134: '水洼',
    135: '中段噪声', 136: '噪声电平', 137: '频谱波', 139: '音频律动', 140: '瀑布', 141: '频谱像素', 143: '噪声之火',
    145: '噪声游移', 146: '二维噪声', 147: '柏林游移', 148: '峰值涟漪', 149: '火焰噪声', 150: '方块漩涡', 151: '吃豆人',
    152: '基因链', 153: '数字雨', 154: '融球', 155: '频谱映射', 156: '重力中心', 159: '打碟灯', 160: '律动板',
    161: '微光', 162: '脉冲', 163: '模糊', 164: '漂移', 165: '波涌', 166: '日冕', 167: '彩爆', 168: '朱莉娅集',
    172: '生命游戏', 173: '格纹', 174: '极地之光', 175: '漩涡', 176: '李萨如', 177: '蜷曲', 178: '等离子球',
    179: '流光条纹', 180: '催眠', 181: '正弦点', 182: '基因螺旋', 183: '黑洞', 184: '波正弦', 185: '摇滚波',
    186: '阿凯米律动', 187: '火山', 188: '火焰', 189: '粒子焰火', 190: '涡旋', 191: '朦胧噪声', 192: '球池',
    193: '盒子', 194: '吸引子', 195: '冲击', 196: '粒子瀑布', 197: '喷溅', 198: '二维频谱', 199: '频谱新星',
    200: '粒子恶灵', 201: '粒子团块', 202: '滴答', 203: '弹珠', 204: '粒子舞影', 205: '线型粒子焰火', 206: '手持烟花',
    207: '沙漏', 208: '线型喷溅', 209: '线型平衡', 210: '粒子追逐', 211: '星芒', 212: '线型频谱', 213: '线型火焰',
    214: '声波激流', 215: '声爆', 216: '弹簧', 217: '星海', 218: '彩云', 219: '缓慢流转', 220: '指针时钟',
    221: '极光之舞',
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
    # 宏名不带 2D 前缀，只能靠白名单识别。
    if fx == "FX_MODE_LUMI_AURORA":
        return "TWO_D"
    if fx.startswith(("FX_MODE_2D", "FX_MODE_PARTICLE", "FX_MODE_PS")):
        return "TWO_D"
    return "ONE_D"


def mood_of(entry: dict) -> str:
    idx = entry["idx"]
    if idx in TEXT_IDS:
        return "TEXT"
    if idx in MUSIC_IDS:
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
    missing_zh = [e["name"] for e in effects if e["idx"] not in NAME_ZH]
    print(f"共 {len(effects)} 个灯效: {counts}, 常用 {len(POPULAR_NAMES)}, 预览 {len(effects)}")
    print(f"律动模式: {moods}")
    if missing_zh:
        print(f"未收录中文名: {missing_zh}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
