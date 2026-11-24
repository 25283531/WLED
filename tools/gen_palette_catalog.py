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
package ca.cgagnier.wlednativeandroid.ui.effects

/**
 * 一个固件调色板。
 *
 * @property id 运行时调色板值，可直接写入 `seg.pal`。
 * @property name 固件显示名。带 `*` 前缀的是「只取当前颜色槽位」的特殊调色板，
 *   不随时间变化 —— 例如 `* Color 1` 就是固件界面上的 "Primary Color"。
 */
data class PaletteInfo(
    val id: Int,
    val name: String,
    val isStatic: Boolean,
) {
    /** 去掉 `*` 前缀后的规范名，便于按语义反查（`* Color 1` -> `Color 1`）。 */
    val canonicalName: String get() = name.removePrefix("*").trim()
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


def render(names: list[str]) -> str:
    lines = [HEADER]
    for index, name in enumerate(names):
        escaped = name.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'        PaletteInfo({index}, "{escaped}", {str(name.startswith(STAR_PREFIX)).lower()}),\n')
    lines.append(FOOTER)
    return "".join(lines)


def main() -> None:
    names = parse_palettes()
    content = render(names)
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)
    print(f"已生成 {os.path.relpath(TARGET, ROOT)}（{len(names)} 个调色板）")


if __name__ == "__main__":
    main()
