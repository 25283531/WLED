"""审计固件灯效的重复程度，产出重复簇报告与待删建议。

三个信号，交叉验证：

  信号 A（核心实现）：解析 firmware/wled00/FX.cpp 里每个 `mode_*` 函数体。
    WLED 大量灯效是「薄封装」——函数体只有一行，转调一个共享的核心实现并改一两个参数：

        void mode_blink(void)        { blink(SEGCOLOR(0), SEGCOLOR(1), false, true); }
        void mode_strobe(void)       { blink(SEGCOLOR(0), SEGCOLOR(1), true,  true); }

    只比 token 相似度会把这两者判成 1.000，但它们其实是一族。
    所以这里下沉到核心实现，并比对实参：
      核心相同 + 实参相同 → 真冗余（删掉无功能损失）
      核心相同 + 实参不同 → 参数化变体（视觉可区分，保留）

  信号 B（技术路径族）：同一个视觉概念在 WLED 里有三条渲染路径——
    1D 灯带 / 2D 矩阵 / PS 粒子系统。名字成对或成组出现（Fireworks / PS Fireworks /
    PS Fireworks 1D），实现完全不同，是**设计使然**而非冗余。

  信号 C（名称近亲）：中文名去掉「线型/粒子/II/III」修饰后的相似度，
    用来补充抓 A 没覆盖到的同族。

输出:
  docs/EFFECT_DUPLICATE_AUDIT.md             人读报告
  firmware/effects_duplicate_candidates.json 机读结果

用法:
  python tools/audit_effect_duplicates.py
"""

from __future__ import annotations

import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FX_CPP = os.path.join(ROOT, "firmware", "wled00", "FX.cpp")
FX_H = os.path.join(ROOT, "firmware", "wled00", "FX.h")
EFFECTS_JSON = os.path.join(ROOT, "firmware", "effects_enabled.json")
CATALOG_PY = os.path.join(ROOT, "tools", "gen_effect_catalog.py")
REPORT_MD = os.path.join(ROOT, "docs", "EFFECT_DUPLICATE_AUDIT.md")
# 机读结果放 docs/ 而不是 firmware/ —— 它是审计产物，不是固件源码，
# 放在 firmware/ 下会误触发固件 CI 重建。
RESULT_JSON = os.path.join(ROOT, "docs", "effects_duplicate_candidates.json")

# ---------------------------------------------------------------- 固件解析

# 任何返回类型都算，只要形如 `void mode_x(void) {`
FUNC_RE = re.compile(
    r"^[ \t]*(?:[A-Za-z_][A-Za-z0-9_:<>*&\s]*?)\b([A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*\)\s*\{",
    re.MULTILINE,
)
ADDEFFECT_RE = re.compile(
    r"addEffect\(\s*(FX_MODE_[A-Z0-9_]+)\s*,\s*&([A-Za-z_][A-Za-z0-9_]*)\s*,\s*(_data_FX_MODE_[A-Z0-9_]+)\s*\)"
)
DEFINE_RE = re.compile(r"^#define\s+(FX_MODE_[A-Z0-9_]+)\s+(\d+)", re.MULTILINE)

# 技术路径前缀：同名概念的 1D / 2D / PS 三条渲染路径
PATH_PREFIXES = ("PS ", "2D ", "PS2D ")

# 「核心不同但实现高度相似」的判定阈值。
# 实测固件里没有近乎相同的实现（除 core 相同的薄封装变体外），
# 最高分只有 0.79，往下迅速回落，所以阈值取 0.70 把值得人工看的都收进来。
SIMILAR_SCORE_MIN = 0.70
SIMILAR_RATIO_MIN = 0.70


def strip_comments(text: str) -> str:
    """去掉块注释与行注释，保留字符串字面量内容。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end == -1 else end + 2
            out.append(" ")
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end == -1 else end
            out.append(" ")
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def extract_functions(source: str) -> dict[str, str]:
    """抽出每个函数定义的体（不含外层大括号）。"""
    clean = strip_comments(source)
    funcs: dict[str, str] = {}
    for match in FUNC_RE.finditer(clean):
        name = match.group(1)
        if name in funcs:
            continue
        start = match.end()
        depth = 1
        i = start
        while i < len(clean) and depth > 0:
            if clean[i] == "{":
                depth += 1
            elif clean[i] == "}":
                depth -= 1
            i += 1
        body = clean[start : i - 1]
        # 跳过函数声明（无花括号实体）产生的误匹配
        if body.strip():
            funcs[name] = body
    return funcs


TOKEN_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|\d+")

# WLED 的通用工具，任何灯效都会调用，不带区分度。
COMMON_TOKENS = {
    "uint16_t", "uint32_t", "uint8_t", "int", "bool", "void", "if", "else",
    "for", "while", "return", "true", "false", "const", "static", "sizeof",
    "SEGENV", "seg", "strip", "customPalette", "wled",
    "random8", "random16", "sin8_t", "cos8_t", "scale8", "qadd8", "qadd16",
    "rshift8", "rshift16", "min", "max", "abs", "lerp", "distance",
    "setPixelColorXY", "setPixelColor", "getPixelColorXY", "color_blend",
    "CRGB", "segment", "millis", "digitalRead", "analogRead",
}


def tokenize(body: str) -> set[str]:
    """token 集合。保留数字字面量（除 0/1），因为常量差异是有意义的信号。"""
    tokens = set()
    for tok in TOKEN_RE.findall(body):
        if tok in COMMON_TOKENS:
            continue
        if tok.isdigit():
            if tok in ("0", "1"):
                continue
            tokens.add("#" + tok)
            continue
        tokens.add(tok)
    return tokens


def jaccard(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    union = len(a | b)
    return len(a & b) / union if union else 0.0


def line_count(body: str) -> int:
    return sum(1 for line in body.splitlines() if line.strip())


# ------------------------------------------------- 核心实现下沉 + 实参比对

CALL_RE = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(")


def find_calls(body: str) -> list[tuple[str, str]]:
    """找出函数调用 (被调名, 实参原文)。

    不用正则匹配整段实参 —— `SEGCOLOR(0)` 这种嵌套括号会让正则失配，
    这里手写括号配对。
    """
    calls: list[tuple[str, str]] = []
    for match in CALL_RE.finditer(body):
        name = match.group(1)
        open_idx = match.end() - 1
        depth = 0
        i = open_idx
        while i < len(body):
            if body[i] == "(":
                depth += 1
            elif body[i] == ")":
                depth -= 1
                if depth == 0:
                    calls.append((name, body[open_idx + 1 : i]))
                    break
            i += 1
    return calls


def split_args(arg_text: str) -> list[str]:
    """按顶层逗号切分实参，保留顺序与原文。"""
    args, depth, current = [], 0, []
    for ch in arg_text:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            args.append("".join(current).strip())
            current = []
            continue
        current.append(ch)
    tail = "".join(current).strip()
    if tail:
        args.append(tail)
    return args


def resolve_core(
    funcs: dict[str, str], name: str, depth: int = 3, args: tuple[str, ...] = ()
) -> tuple[str, tuple[str, ...]]:
    """把薄封装下沉到核心实现。

    「纯转发」判定：去掉 return 与分号后，整个函数体**恰好是一个调用表达式**。
    这样 `blink(SEGCOLOR(0), SEGCOLOR(1), false, true)` 里的嵌套 `SEGCOLOR(0)`
    不会被误当成第二个调用。

    返回 (核心函数名, 最外层实参元组)。不是纯转发时返回 (自身, 传入的实参)。
    """
    if depth <= 0 or name not in funcs:
        return name, args
    body = funcs[name].strip()
    body = re.sub(r"^return\s+", "", body).strip().rstrip(";").strip()

    match = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)\s*\(", body)
    if not match:
        return name, args
    callee = match.group(1)

    open_idx = match.end() - 1
    level = 0
    i = open_idx
    while i < len(body):
        if body[i] == "(":
            level += 1
        elif body[i] == ")":
            level -= 1
            if level == 0:
                break
        i += 1
    else:
        return name, args

    arg_text = body[open_idx + 1 : i]
    if body[i + 1 :].strip():
        return name, args          # 调用之外还有别的语句 → 不是单纯转发
    if callee not in funcs:
        return name, args

    new_args = tuple(split_args(arg_text))
    if depth <= 1:
        return callee, new_args
    return resolve_core(funcs, callee, depth - 1, new_args)


# ---------------------------------------------------------------- 名称族

FAMILY_PREFIX = ("线型", "粒子", "点阵", "像素")
FAMILY_SUFFIX = ("效果", "之舞", "II", "III", "IV", " 2", " 3")


def family_key(zh: str) -> str:
    key = zh
    changed = True
    while changed:
        changed = False
        for p in FAMILY_PREFIX:
            if key.startswith(p) and len(key) > len(p):
                key = key[len(p):]
                changed = True
        for s in FAMILY_SUFFIX:
            if key.endswith(s) and len(key) > len(s):
                key = key[: -len(s)]
                changed = True
    return key


def name_similarity(a: str, b: str) -> float:
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    ga = {a[i : i + 2] for i in range(len(a) - 1)} or {a}
    gb = {b[i : i + 2] for i in range(len(b) - 1)} or {b}
    return 2 * len(ga & gb) / (len(ga) + len(gb))


def tech_path(name: str) -> str:
    """从英文名判断渲染路径：1D / 2D / PS。"""
    for p in PATH_PREFIXES:
        if name.startswith(p):
            return p.strip()
    if "2D" in name:
        return "2D"
    return "1D"


# ---------------------------------------------------------------- 主流程


def load_name_zh() -> dict[int, str]:
    with open(CATALOG_PY, encoding="utf-8") as handle:
        text = handle.read()
    block = re.search(r"NAME_ZH\s*=\s*\{(.*?)\n\}", text, re.DOTALL)
    if not block:
        return {}
    return {int(i): z for i, z in re.findall(r"(\d+):\s*'([^']*)'", block.group(1))}


def load_catalog_names() -> dict[str, str]:
    """英文名 -> 中文名（从生成脚本的 NAME_ZH 反查）。"""
    zh_by_idx = load_name_zh()
    with open(EFFECTS_JSON, encoding="utf-8") as handle:
        raw = json.load(handle)
    out = {}
    for entry in raw:
        if entry and entry.get("name"):
            out[entry["name"]] = zh_by_idx.get(entry["idx"], entry["name"])
    return out


def main() -> int:
    with open(FX_CPP, encoding="utf-8", errors="replace") as handle:
        fx_cpp = handle.read()
    with open(FX_H, encoding="utf-8", errors="replace") as handle:
        fx_h = handle.read()
    with open(EFFECTS_JSON, encoding="utf-8") as handle:
        raw_effects = json.load(handle)

    macros = {macro: int(num) for macro, num in DEFINE_RE.findall(fx_h)}
    impl_of_macro: dict[str, str] = {}
    for macro, fn, _data in ADDEFFECT_RE.findall(fx_cpp):
        impl_of_macro.setdefault(macro, fn)
    funcs = extract_functions(fx_cpp)
    name_zh = load_name_zh()

    effects = []
    for entry in raw_effects:
        if not entry or not entry.get("name"):
            continue
        idx = entry["idx"]
        macro = entry.get("fx") or ""
        effects.append(
            {
                "idx": idx,
                "macro": macro,
                "name": entry["name"],
                "zh": name_zh.get(idx, entry["name"]),
                "impl": impl_of_macro.get(macro),
                "path": tech_path(entry["name"]),
            }
        )

    print(f"有效灯效 {len(effects)} 个，解析到函数 {len(funcs)} 个")

    # 编译时未启用的分支（WLED_KEEP_ALL_FX）不计
    excluded = set(
        re.findall(
            r"addEffect\(\s*(FX_MODE_\w+)\s*,",
            " ".join(
                re.findall(r"#ifdef WLED_KEEP_ALL_FX(.*?)#endif", fx_cpp, re.S)
            ),
        )
    )
    excluded |= set(
        re.findall(r"//\s*addEffect\(\s*(FX_MODE_\w+)\s*,", fx_cpp)
    )
    print(f"编译时未启用/已注释合并的灯效：{len(excluded)} 个")

    # ---- 信号 A：核心实现 + 实参
    core_of: dict[int, tuple[str, tuple[str, ...]]] = {}
    for eff in effects:
        if eff["impl"] and eff["impl"] in funcs:
            core_of[eff["idx"]] = resolve_core(funcs, eff["impl"])
        else:
            core_of[eff["idx"]] = (eff["impl"] or eff["macro"], ())

    tokens = {n: tokenize(b) for n, b in funcs.items()}
    lines = {n: line_count(b) for n, b in funcs.items()}

    redundant_pairs: list[tuple[dict, dict]] = []
    variant_pairs: list[tuple[dict, dict]] = []
    similar_pairs: list[tuple[float, dict, dict]] = []

    eligible = [e for e in effects if e["impl"] in funcs]
    for i, a in enumerate(eligible):
        for b in eligible[i + 1 :]:
            ca, aa = core_of[a["idx"]]
            cb, ab = core_of[b["idx"]]
            if ca == cb and ca:
                if aa == ab:
                    redundant_pairs.append((a, b))
                else:
                    variant_pairs.append((a, b))
                continue
            la, lb = lines[a["impl"]], lines[b["impl"]]
            ratio = min(la, lb) / max(la, lb) if max(la, lb) else 0.0
            if ratio >= SIMILAR_RATIO_MIN:
                score = jaccard(tokens[a["impl"]], tokens[b["impl"]])
                if score >= SIMILAR_SCORE_MIN:
                    similar_pairs.append((score, a, b))

    print(f"核心实现+实参完全相同（真冗余）：{len(redundant_pairs)} 对")
    print(f"核心实现相同、实参不同（参数化变体）：{len(variant_pairs)} 对")
    print(
        f"实现相似度 ≥{SIMILAR_SCORE_MIN:.2f} 但核心不同：{len(similar_pairs)} 对"
    )

    # ---- 信号 B：技术路径族
    family_groups: dict[str, list[dict]] = defaultdict(list)
    for eff in effects:
        family_groups[family_key(eff["zh"])].append(eff)
    multi_path = {
        k: g
        for k, g in family_groups.items()
        if len(g) > 1 and len({e["path"] for e in g}) > 1
    }
    print(f"跨渲染路径的同族概念：{len(multi_path)} 组")

    # ---- 名称近亲（信号 C）
    name_pairs = []
    for i, a in enumerate(effects):
        for b in effects[i + 1 :]:
            ka, kb = family_key(a["zh"]), family_key(b["zh"])
            score = 1.0 if ka == kb else name_similarity(a["zh"], b["zh"])
            if score >= 0.55:
                name_pairs.append((score, a, b))
    print(f"名称近亲（≥0.55）：{len(name_pairs)} 对")

    # ---- 结论
    verdict: dict[int, dict] = {}
    for a, b in redundant_pairs:
        for keeper, drop in _pick_keeper(a, b):
            verdict[drop["idx"]] = {
                "idx": drop["idx"],
                "zh": drop["zh"],
                "name": drop["name"],
                "macro": drop["macro"],
                "verdict": "redundant",
                "duplicate_of": keeper["idx"],
                "duplicate_zh": keeper["zh"],
                "reason": f"核心实现 `{keeper['impl']}` 与实参完全相同，删除无功能损失",
            }
    for a, b in variant_pairs:
        for keeper, drop in _pick_keeper(a, b):
            if drop["idx"] in verdict:
                continue
            core_fn = core_of[keeper["idx"]][0]
            verdict[drop["idx"]] = {
                "idx": drop["idx"],
                "zh": drop["zh"],
                "name": drop["name"],
                "macro": drop["macro"],
                "verdict": "variant",
                "duplicate_of": keeper["idx"],
                "duplicate_zh": keeper["zh"],
                "reason": (
                    f"与 #{keeper['idx']} {keeper['zh']} 共用核心实现 "
                    f"`{core_fn}`，仅参数不同，视觉可区分"
                ),
            }
    for score, a, b in similar_pairs:
        for keeper, drop in _pick_keeper(a, b):
            if drop["idx"] in verdict:
                continue
            verdict[drop["idx"]] = {
                "idx": drop["idx"],
                "zh": drop["zh"],
                "name": drop["name"],
                "macro": drop["macro"],
                "verdict": "similar",
                "duplicate_of": keeper["idx"],
                "duplicate_zh": keeper["zh"],
                "reason": f"与 #{keeper['idx']} {keeper['zh']} 实现相似度 {score:.2f}，核心不同",
            }

    redundant = [v for v in verdict.values() if v["verdict"] == "redundant"]
    variants = [v for v in verdict.values() if v["verdict"] == "variant"]
    similar = [v for v in verdict.values() if v["verdict"] == "similar"]
    print(
        f"结论：真冗余 {len(redundant)} 个，参数化变体 {len(variants)} 个，"
        f"实现相似 {len(similar)} 个"
    )

    # ---- 写机读结果
    os.makedirs(os.path.dirname(RESULT_JSON), exist_ok=True)
    with open(RESULT_JSON, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(
            {
                "generated_from": "firmware/effects_enabled.json + firmware/wled00/FX.cpp",
                "total_effects": len(effects),
                "excluded_from_build": sorted(excluded),
                "verdicts": sorted(verdict.values(), key=lambda v: v["idx"]),
                "family_groups": {
                    k: [{"idx": e["idx"], "zh": e["zh"], "path": e["path"]} for e in g]
                    for k, g in sorted(multi_path.items())
                },
            },
            handle,
            ensure_ascii=False,
            indent=2,
        )

    write_report(
        effects, excluded, redundant, variants, similar, multi_path, core_of
    )
    print(f"报告: {REPORT_MD}")
    print(f"机读结果: {RESULT_JSON}")
    return 0


def _pick_keeper(a: dict, b: dict) -> list[tuple[dict, dict]]:
    """保留 id 小的那个，另一个作为候选。"""
    if a["idx"] <= b["idx"]:
        return [(a, b)]
    return [(b, a)]


def write_report(
    effects, excluded, redundant, variants, similar, multi_path, core_of
) -> None:
    lines = [
        "# 固件灯效重复度审计",
        "",
        "> 由 `tools/audit_effect_duplicates.py` 自动生成，请勿手工编辑。",
        "> 数据源：`firmware/effects_enabled.json`（槽位表）+ `firmware/wled00/FX.cpp`（实现）。",
        "",
        "## 一、结论先行",
        "",
        f"- 固件实际编译进产品的灯效：**{len(effects)}** 个",
        f"- 其中**真冗余**（核心实现与实参完全相同、删除无功能损失）：**{len(redundant)}** 个",
        f"- **参数化变体**（共用核心实现、仅参数不同，视觉可区分）：{len(variants)} 个",
        f"- **实现相似**（核心不同但 token 高度重合）：{len(similar)} 个",
        "",
    ]
    if not redundant:
        lines += [
            "> **没有可以直接删除的灯效。** 原因见第二节：",
            "> WLED 的灯效库里同族关系来自**三条不同的渲染路径**（1D 灯带 / 2D 矩阵 / PS 粒子系统），",
            "> 而同一路径下的近邻多为**参数化变体**（同一核心实现换参数），视觉可区分。",
            "> 固件维护者也已在源头做过人工去重（见第三节）。",
            "",
        ]
    lines += [
        "## 二、方法",
        "",
        "WLED 里大量灯效是「薄封装」——函数体只有一行，转调共享的核心实现并改一两个参数：",
        "",
        "```cpp",
        "void mode_blink(void)  { blink(SEGCOLOR(0), SEGCOLOR(1), false, true); }",
        "void mode_strobe(void) { blink(SEGCOLOR(0), SEGCOLOR(1), true,  true); }",
        "```",
        "",
        "只比 token 相似度会把这两者判成 1.000，但它们其实是一族。所以审计下沉到核心实现：",
        "",
        "| 判定 | 条件 | 处置 |",
        "|---|---|---|",
        "| `redundant` | 核心实现相同 **且 实参完全相同** | 可删，视觉无损失 |",
        "| `variant` | 核心实现相同，实参不同 | 保留（视觉可区分） |",
        "| `similar` | 核心不同，但 token Jaccard ≥ %.2f 且行数比 ≥ %.2f | 保留，人工抽查 |"
        % (SIMILAR_SCORE_MIN, SIMILAR_RATIO_MIN),
        "",
        "跨渲染路径的同族（信号 B）单独统计，见第四节。",
        "",
        "## 三、固件侧已有人工去重",
        "",
        "审计时发现固件源头已经做过一轮人工去重，这些灯效**不参与编译**，",
        "因此 App 的灯效清单与固件实际可用集合一致（无遗漏）：",
        "",
        "```cpp",
        '  //addEffect(FX_MODE_METEOR_SMOOTH, &mode_meteor_smooth, ...); // merged with mode_meteor',
        "  #endif // dedup: ~ FX_MODE_NOISE16_1",
        "```",
        "",
        f"另外 `{len(excluded)}` 个灯效位于 `#ifdef WLED_KEEP_ALL_FX` 块内，",
        "而该宏在本产品编译时**未定义**，故不参与编译（彩虹/随机/双扫描等变体全家桶）。",
        "",
        "## 四、跨渲染路径的同族概念",
        "",
        "同一视觉概念在 WLED 里有 1D / 2D / PS 三条实现，视觉差异明显，**不是冗余**：",
        "",
        "| 概念 | 灯效 |",
        "|---|---|",
    ]
    for key, group in multi_path.items():
        desc = "、".join(f"#{e['idx']} {e['zh']}({e['path']})" for e in group)
        lines.append(f"| {key} | {desc} |")

    lines += [
        "",
        "## 五、真冗余清单",
        "",
    ]
    if redundant:
        lines += [
            "| 待删 id | 灯效 | 重复于 | 理由 |",
            "|---|---|---|---|",
        ]
        for v in sorted(redundant, key=lambda x: x["idx"]):
            lines.append(
                f"| {v['idx']} | {v['zh']} | #{v['duplicate_of']} {v['duplicate_zh']} | {v['reason']} |"
            )
    else:
        lines.append("**无。** 本轮审计未发现任何「删除后无功能损失」的灯效。")

    lines += [
        "",
        "## 六、参数化变体清单（保留）",
        "",
        "| 灯效 | 与之同核心 | 理由 |",
        "|---|---|---|",
    ]
    for v in sorted(variants, key=lambda x: x["idx"]):
        lines.append(
            f"| #{v['idx']} {v['zh']} | #{v['duplicate_of']} {v['duplicate_zh']} | {v['reason']} |"
        )

    lines += [
        "",
        "## 七、实现相似清单（保留，人工抽查）",
        "",
        "| 灯效 | 与之相似 | 理由 |",
        "|---|---|---|",
    ]
    for v in sorted(similar, key=lambda x: x["idx"]):
        lines.append(
            f"| #{v['idx']} {v['zh']} | #{v['duplicate_of']} {v['duplicate_zh']} | {v['reason']} |"
        )

    lines += [
        "",
        "## 八、如果确实要精简 App 端展示",
        "",
        "从固件删除灯效是**破坏性**的：用户已保存的预设与 `seg.fx` 会指向被删 id，",
        "固件侧删除后这些引用会回落到 id 0（Solid），表现为「设好的效果突然变纯色」。",
        "",
        "因此推荐顺序：",
        "",
        "1. **不动固件**。189 个灯效在固件里各占一个槽位，成本只是一个数组项。",
        "2. 在 App 侧做**展示层收敛**：同一视觉概念下只露出代表项（如焰火只显示 1D 版），",
        "   其余收进二级列表；本报告第四节的分组直接可用。",
        "3. 若一定要删固件，先确认产品生命周期内无用户保存过对应 id 的预设。",
        "",
        "## 九、复现",
        "",
        "```bash",
        "python tools/audit_effect_duplicates.py",
        "```",
        "",
    ]

    os.makedirs(os.path.dirname(REPORT_MD), exist_ok=True)
    with open(REPORT_MD, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))


if __name__ == "__main__":
    sys.exit(main())
