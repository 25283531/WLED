"""从固件灯效清单生成 App 侧的 Kotlin 灯效目录。

数据源: firmware/effects_enabled.json（由固件源码解析得到，idx 即运行时 fx 值）
输出:   app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/effects/EffectCatalog.kt

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

# 灯效名 -> GIF 资源名（app/src/main/assets/effect_previews/<name>.gif）
PREVIEWS = {
    "Solid Pattern": "solid",
    "Blink": "blink",
    "Breathe": "breathe",
    "Wipe": "wipe",
    "Colorloop": "colorloop",
    "Rainbow": "rainbow",
    "Twinkle": "twinkle",
    "Chase": "chase",
    "Fireworks": "fireworks",
    "Meteor": "comet",
    "Fire 2012": "fire",
    "Plasma": "plasma",
    "Scrolling Text": "text_h",
    "Matrix": "matrix_rain",
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
        " * 一个固件灯效。",
        " *",
        " * @property id 运行时 fx 值，可直接写入 `seg.fx`。",
        " * @property name 固件提供的英文名称（`/json/effects` 同一份数据，此处固化以便离线展示）。",
        " * @property category 见 [EffectCategory]。",
        " * @property isPopular 是否列入「常用」。",
        " * @property preview 预览 GIF 的资源名（assets/effect_previews/<preview>.gif），无预览时为 null。",
        " */",
        "data class EffectInfo(",
        "    val id: Int,",
        "    val name: String,",
        "    val category: EffectCategory,",
        "    val isPopular: Boolean,",
        "    val preview: String?,",
        ") {",
        "    val hasPreview: Boolean get() = preview != null",
        "}",
        "",
        "object EffectCatalog {",
        "    val all: List<EffectInfo> = listOf(",
    ]

    for entry in effects:
        name = entry["name"]
        category = categorize(entry)
        popular = "true" if name in POPULAR_NAMES else "false"
        preview = PREVIEWS.get(name)
        preview_literal = kotlin_string(preview) if preview else "null"
        lines.append(
            f"        EffectInfo({entry['idx']}, {kotlin_string(name)}, "
            f"EffectCategory.{CATEGORY_CONST[category]}, {popular}, {preview_literal}),"
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
    print(f"共 {len(effects)} 个灯效: {counts}, 常用 {len(POPULAR_NAMES)}, 有预览 {len(PREVIEWS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
