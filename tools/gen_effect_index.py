"""从固件源码解析出「槽位 -> 效果」的权威映射。

关键事实（决定了 App 下发 seg.fx 时该用哪个值）：

    固件用 `WS2812FX::addEffect(id, fn, data)` 动态注册效果，id 就是 `FX_MODE_*`
    宏的数值，也就是**运行时的 fx 值**。注册表 `_mode` 是长度为 MODE_COUNT 的
    数组，下标 = fx 值，没被注册的槽位保留 "RSVD" 占位。

    `/json/effects` 由 `serializeModeNames()` 遍历 `_modeData[0.._modeCount-1]`
    生成，只跳过**空字符串**；而占位符是 "RSVD"（非空），所以 RSVD 槽位
    **同样占据数组下标**。

    => 固件返回的 effects 数组长度 = MODE_COUNT，**下标 == 运行时 fx 值**。

    历史上这里存的是一份「重新紧凑编号」的 187 条清单，idx 与固件槽位
    184/187 不一致（Copy Segment 固件槽位 77，旧清单却写 idx 0）。App 直接把
    清单 idx 当 fx 下发，会打到错误的固件槽位 —— 这就是「灯效名称和灯效
    关联错了」的真正根因。

输出:
    firmware/effects_enabled.json —— 槽位对齐的完整效果表（App 的灯效库、
    mock 服务器、预览 GIF 命名都以它为准）
"""

from __future__ import annotations

import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FX_H = os.path.join(ROOT, "firmware", "wled00", "FX.h")
FX_CPP = os.path.join(ROOT, "firmware", "wled00", "FX.cpp")
TARGET = os.path.join(ROOT, "firmware", "effects_enabled.json")


def parse_macros() -> dict[str, int]:
    text = open(FX_H, encoding="utf-8", errors="replace").read()
    macros: dict[str, int] = {}
    # Anchor at line start so commented-out defines (e.g. "//#define FX_MODE_METEOR_SMOOTH 77")
    # are not parsed. Otherwise their stale value collides with the live macro that reuses the slot.
    for match in re.finditer(r"^[ \t]*#define\s+(FX_MODE_\w+)\s+(\d+)", text, re.M):
        macros.setdefault(match.group(1), int(match.group(2)))
    mode_count = int(re.search(r"^[ \t]*#define\s+MODE_COUNT\s+(\d+)", text, re.M).group(1))
    return macros, mode_count


def parse_mode_data(source: str) -> dict[str, str]:
    """_data_FX_MODE_XXX 变量名 -> 效果数据串。"""
    return dict(
        re.findall(
            r'static const char (_data_FX_MODE_\w+)\[\] PROGMEM = "([^"]*)"',
            source,
        )
    )


def parse_registrations(source: str) -> list[tuple[str, str]]:
    """从 addEffect(...) 语句里取出 (FX_MODE 宏, 数据串变量名)。

    必须以注册语句为权威，而不是靠宏名去猜数据串名 —— 粒子系统那批效果的
    数据串变量名与宏名并不一致（例如 FX_MODE_PSDRIP 用的是
    _data_FX_MODE_PARTICLEDRIP）。
    """
    return re.findall(
        r"^[ \t]*addEffect\(\s*(FX_MODE_\w+)\s*,\s*&\w+\s*,\s*(_data_FX_MODE_\w+)\s*\)",
        source,
        re.M,
    )


def strip_keep_all_fx(source: str, registrations):
    """WLED_KEEP_ALL_FX 宏本仓库未定义，该分支不参与编译，要排除掉。"""
    for block in re.findall(r"#ifdef WLED_KEEP_ALL_FX(.*?)#endif", source, re.S):
        excluded = set(
            re.findall(r"addEffect\(\s*(FX_MODE_\w+)\s*,", block)
        )
        registrations = [r for r in registrations if r[0] not in excluded]
    return registrations


def main() -> None:
    macros, mode_count = parse_macros()
    source = open(FX_CPP, encoding="utf-8", errors="replace").read()
    mode_data = parse_mode_data(source)
    registrations = strip_keep_all_fx(source, parse_registrations(source))

    # 槽位 0 是 Solid（由 _mode.push_back 直接放入，不走 addEffect）
    slots: list[dict | None] = [None] * mode_count
    slots[0] = {
        "idx": 0,
        "fx": "FX_MODE_STATIC",
        "name": "Solid",
        "fn": "mode_static",
    }

    rejected: list[tuple[str, int]] = []
    for fx_macro, data_var in registrations:
        slot = macros.get(fx_macro)
        if slot is None or slot >= mode_count:
            rejected.append((fx_macro, slot if slot is not None else -1))
            continue
        if slots[slot] is not None:
            # addEffect 不会覆盖已占用的槽位
            rejected.append((fx_macro, slot))
            continue
        raw = mode_data.get(data_var, "")
        slots[slot] = {
            "idx": slot,
            "fx": fx_macro,
            "name": raw.split("@")[0],
            "fn": None,
        }

    with open(TARGET, "w", encoding="utf-8") as handle:
        json.dump(slots, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    used = sum(1 for s in slots if s is not None)
    print(f"MODE_COUNT      : {mode_count}")
    print(f"已占用槽位      : {used}")
    print(f"RSVD 占位槽位   : {mode_count - used}")
    print(f"注册被拒（槽位冲突）: {len(rejected)}")
    for fx_macro, slot in rejected:
        print(f"    {fx_macro} -> slot {slot}")
    print(f"输出            : {TARGET}")
    print(f"槽位 0..7       : {[s['name'] if s else None for s in slots[:8]]}")
    print(f"槽位 219..221   : {[s['name'] if s else None for s in slots[219:]]}")


if __name__ == "__main__":
    main()
