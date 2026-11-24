"""从固件源码解析出「调色板 id -> 名称」的权威映射。

背景（和 tools/gen_effect_index.py 同一个道理）：

    `seg.pal` 要写的是调色板的**运行时 id**，也就是 `JSON_palette_names` 数组里的下标。
    固件随时可能在这个数组中间插入新调色板（比如加一个 "Aurora 3"），
    后面所有调色板的 id 就会整体前移。

    于是「硬编码一个调色板 id」是个隐形雷：参考代码里常见的 `palId: 49`，
    在某个版本里是 Sakura，过一个版本就变成 Aurora 2 了 —— 不报错，只是颜色悄悄变了。

    所以 App 侧一律按**名字**引用调色板，用这个脚本生成的名字表在运行时反查 id。

输出:
    app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/effects/PaletteCatalog.kt
"""

from __future__ import annotations

import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FX_FCN = os.path.join(ROOT, "firmware", "wled00", "FX_fcn.cpp")
TARGET = os.path.join(
    ROOT,
    "app", "app", "src", "main", "java", "ca", "cgagnier", "wlednativeandroid",
    "ui", "effects", "PaletteCatalog.kt",
)

# 固件里 `*` 前缀的调色板是「只取当前颜色槽位」的特殊调色板，不随时间变化。
# 例如 `* Color 1` 就是 WLED 界面上的 "Primary Color"。
STAR_PREFIX = "*"

HEADER = '''// 由 tools/gen_palette_catalog.py 自动生成，请勿手工编辑。
// 数据源: firmware/wled00/FX_fcn.cpp 的 JSON_palette_names（固件 /json/palettes 的同一份数据）
//         firmware/wled00/palettes.cpp 的调色板颜色定义
package ca.cgagnier.wlednativeandroid.ui.effects

/**
 * 一个固件调色板。
 *
 * @property id 运行时调色板值，可直接写入 `seg.pal`。
 * @property name 固件显示名。带 `*` 前缀的是「只取当前颜色槽位」的特殊调色板，
 *   不随时间变化 —— 例如 `* Color 1` 就是固件界面上的 "Primary Color"。
 * @property colors 该调色板的 16 个色标，`0xRRGGBB`。动态调色板（只取颜色槽位的那种）
 *   没有固定色，为空列表。
 */
data class PaletteInfo(
    val id: Int,
    val name: String,
    val isStatic: Boolean,
    val colors: List<Int> = emptyList(),
) {
    /** 去掉 `*` 前缀后的规范名，便于按语义反查（`* Color 1` -> `Color 1`）。 */
    val canonicalName: String get() = name.removePrefix("*").trim()

    /** 能否画出真实的渐变条。动态调色板没有固定色，只能按名字示意。 */
    val hasPreview: Boolean get() = colors.size >= 2
}

object PaletteCatalog {
    val all: List<PaletteInfo> = listOf(
'''

FOOTER = '''    )

    private val byId: Map<Int, PaletteInfo> = all.associateBy { it.id }

    fun find(id: Int?): PaletteInfo? = id?.let { byId[it] }

    fun nameOf(id: Int?): String? = find(id)?.name

    /**
     * 按名字反查调色板 id。
     *
     * 与灯效同理：**不要硬编码调色板 id**。固件在调色板数组中间插入一个新项，
     * 后面所有 id 都会前移，写死的数字会静默变成另一个调色板。
     * 先试原名，再试去掉 `*` 的规范名。
     */
    fun byName(name: String): PaletteInfo? {
        val trimmed = name.trim()
        return all.firstOrNull { it.name == trimmed }
            ?: all.firstOrNull { it.canonicalName == trimmed }
    }
}
'''


def parse_palettes() -> list[str]:
    text = open(FX_FCN, encoding="utf-8", errors="replace").read()
    match = re.search(
        r"const char JSON_palette_names\[\] PROGMEM = R\"=====\((.*?)\)=====\"",
        text,
        re.S,
    )
    if not match:
        raise SystemExit("未在 FX_fcn.cpp 中找到 JSON_palette_names")
    names = json.loads(match.group(1))
    if not isinstance(names, list) or not names:
        raise SystemExit("JSON_palette_names 解析结果不是非空数组")
    return [str(n) for n in names]


# ── 调色板真实颜色 ────────────────────────────────────────────────
#
# 调色板的颜色定义在 firmware/wled00/palettes.cpp 里，两种存储形式：
#
#   1. FastLED 调色板（id 6-12）：
#      const TProgmemRGBPalette16 PartyColors_gc22 PROGMEM = { 0x9B00D5, ... };   // 16 个 0xRRGGBB
#      注意字节序：CRGB(uint32_t) 取的是 0xRRGGBB，即 R=(c>>16)&0xFF、G=(c>>8)&0xFF、B=c&0xFF。
#
#   2. 渐变调色板（id 13-72）：
#      const uint8_t Sunset_Real_gp[] PROGMEM = { 0,181,0,0, 22,218,85,0, ... };    // 16 组 RGBA
#
# id 0-5 是「动态调色板」，本身没有固定颜色：
#   0 Default(用 PartyColors 渲染) / 1 随机 / 2 主色 / 3 主色+次色 / 4 主色渐变 / 5 三色
# 固件对它们的处理见 json.cpp 的 serializePalettes()。

PALETTES_CPP = os.path.join(ROOT, "firmware", "wled00", "palettes.cpp")
FASTLED_H = os.path.join(
    ROOT, "firmware", "wled00", "src", "dependencies", "fastled_slim", "fastled_slim.h"
)

#: id 0 在固件里就是用 PartyColors 渲染的（见 json.cpp serializePalettes 的 case 0）。
DEFAULT_PALETTE_SOURCE = "PartyColors_gc22"


def _strip_c_comments(text: str) -> str:
    """去掉块注释与行注释，避免注释里的数字/单词被当成数据。"""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def _parse_int_list(body: str) -> list[int]:
    return [int(x, 0) for x in re.findall(r"0x[0-9A-Fa-f]+|\b\d+\b", body)]


def _parse_named_colors() -> dict[str, int]:
    """FastLED 的 CRGB 具名色表：`MidnightBlue=0x191970,`。

    有些 FastLED 调色板（Ocean / Cloud / Lava / Forest）用的就是这些具名色，
    直接按十六进制数字解析会漏掉它们。
    """
    text = _strip_c_comments(open(FASTLED_H, encoding="utf-8", errors="replace").read())
    # 只取连续的枚举块：连续多行都是 `Name=0xXXXXXX,` 才算数，
    # 避免把文件里其它地方同形的赋值误收进来。
    best: list[tuple[str, int]] = []
    current: list[tuple[str, int]] = []
    for line in text.split("\n"):
        m = re.match(r"\s{4,}(\w+)=0x([0-9A-Fa-f]{6}),\s*$", line)
        if m:
            current.append((m.group(1), int(m.group(2), 16)))
        else:
            if len(current) > len(best):
                best = current
            current = []
    if len(current) > len(best):
        best = current
    return {name: value for name, value in best}


def parse_palette_colors() -> dict[int, list[int]]:
    """调色板 id -> 16 个 0xRRGGBB 颜色。动态调色板（1-5）不在其中。"""
    text = _strip_c_comments(open(PALETTES_CPP, encoding="utf-8", errors="replace").read())
    named = _parse_named_colors()

    # 所有具名调色板常量：名字 -> 颜色列表
    consts: dict[str, list[int]] = {}

    def substitute_named(body: str) -> str:
        """把 `CRGB::MidnightBlue` 换成 0x191970，好让统一的数字解析器处理。"""
        def repl(match: re.Match) -> str:
            value = named.get(match.group(1))
            return f"0x{value:06X}" if value is not None else "0x000000"

        return re.sub(r"CRGB::(\w+)", repl, body)

    # FastLED 形式：16 个 0xRRGGBB（也可能写成 CRGB::具名色）
    for match in re.finditer(
        r"const\s+TProgmemRGBPalette16\s+(\w+)\s*PROGMEM\s*=\s*\{(.*?)\}\s*;",
        text,
        re.S,
    ):
        consts[match.group(1)] = _parse_int_list(substitute_named(match.group(2)))

    # 渐变形式：16 组 RGBA（每组第 4 字节是填充，取前 3 个）
    # 固件里类型写成 `uint8_t` 或 `byte` 两种，都要认。
    for match in re.finditer(
        r"const\s+(?:uint8_t|byte)\s+(\w+)\s*\[\]\s*PROGMEM\s*=\s*\{(.*?)\}\s*;",
        text,
        re.S,
    ):
        raw = _parse_int_list(match.group(2))
        colors: list[int] = []
        for i in range(0, len(raw) - 3, 4):
            r, g, b = raw[i], raw[i + 1], raw[i + 2]
            colors.append((r << 16) | (g << 8) | b)
        consts[match.group(1)] = colors

    def array_items(pattern: str) -> list[str]:
        m = re.search(pattern, text, re.S)
        if not m:
            raise SystemExit(f"未在 palettes.cpp 中找到数组：{pattern}")
        # 两个数组的写法不一样：fastledPalettes 的项带 `&`（&PartyColors_gc22），
        # gGradientPalettes 的不带（Sunset_Real_gp）；而且数组末项后面没有逗号。
        # 所以逐行取「行首的标识符」，两种写法都吃得下。
        # 注释已在 _strip_c_comments 里去掉，不会把注释里的词当成数组项。
        items: list[str] = []
        for line in m.group(1).split("\n"):
            line = line.strip()
            if not line:
                continue
            item = re.match(r"&?(\w+)", line)
            if item:
                items.append(item.group(1))
        return items

    fastled = array_items(
        r"const\s+TProgmemRGBPalette16\s*\*const\s+fastledPalettes\[\]\s*PROGMEM\s*=\s*\{(.*?)\}\s*;"
    )
    gradient = array_items(
        r"const\s+uint8_t\*\s*(?:const\s+)?gGradientPalettes\[\]\s*PROGMEM\s*=\s*\{(.*?)\}\s*;"
    )

    colors: dict[int, list[int]] = {}
    # id 0 = Default，固件用 PartyColors 渲染
    if DEFAULT_PALETTE_SOURCE in consts:
        colors[0] = consts[DEFAULT_PALETTE_SOURCE]
    # id 6-12 = FastLED 调色板
    for offset, name in enumerate(fastled):
        if name in consts:
            colors[6 + offset] = consts[name]
    # id 13-72 = 渐变调色板
    for offset, name in enumerate(gradient):
        if name in consts:
            colors[13 + offset] = consts[name]
    return colors




def render(names: list[str], colors: dict[int, list[int]]) -> str:
    lines = [HEADER]
    for index, name in enumerate(names):
        escaped = name.replace("\\", "\\\\").replace('"', '\\"')
        palette_colors = colors.get(index, [])
        color_literal = (
            "listOf(" + ", ".join(f"0x{c:06X}" for c in palette_colors) + ")"
            if palette_colors
            else "emptyList()"
        )
        lines.append(
            f'        PaletteInfo({index}, "{escaped}", '
            f"{str(name.startswith(STAR_PREFIX)).lower()}, {color_literal}),\n"
        )
    lines.append(FOOTER)
    return "".join(lines)


def main() -> None:
    names = parse_palettes()
    colors = parse_palette_colors()
    missing = [i for i in range(len(names)) if i not in colors and i != 0]
    content = render(names, colors)
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)
    print(f"已生成 {os.path.relpath(TARGET, ROOT)}（{len(names)} 个调色板）")
    print(f"  带颜色预览：{len(colors)} 个；无固定色（动态调色板）：{len(missing)} 个 -> {missing}")
    if missing and missing != list(range(1, 6)):
        print("  ⚠️ 非预期的缺失 id，请检查 palettes.cpp 是否改名")


if __name__ == "__main__":
    main()
