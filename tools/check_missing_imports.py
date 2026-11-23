#!/usr/bin/env python3
"""检查"用到了仓库内的类却没 import"这一类漏写。

做法：先收集整个仓库里声明的类/对象名（唯一名），再逐个文件扫描被使用的大写标识符，
如果该名字在仓库里有声明，但既不是本文件声明的、也不在同一 package、也没有被 import，
就报出来。编译器同样会报 Unresolved reference，但本地不编译，所以用它兜底。
"""
from __future__ import annotations

import collections
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_DIRS = [
    ROOT / "app/app/src/main/java",
    ROOT / "app/shared/src/commonMain/kotlin",
    ROOT / "app/shared/src/androidMain/kotlin",
    ROOT / "app/app/src/test/java",
    ROOT / "app/shared/src/commonTest/kotlin",
]

PACKAGE_RE = re.compile(r"^package\s+([\w.]+)", re.M)
IMPORT_RE = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", re.M)
DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|abstract\s+|open\s+|sealed\s+|data\s+"
    r"|value\s+|inline\s+|enum\s+|annotation\s+|suspend\s+|operator\s+|override\s+|external\s+)*"
    r"(?:fun\s+interface|class|interface|object|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)",
    re.M,
)
IDENT_RE = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\b")
# 只把真正的类型声明算进"类名表"，扩展函数的接收者不算
CLASS_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|abstract\s+|open\s+|sealed\s+|data\s+"
    r"|value\s+|inline\s+|enum\s+|annotation\s+)*"
    r"(?:fun\s+interface|class|interface|object|typealias)\s+(\w+)",
    re.M,
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def strip_noise(text: str) -> str:
    """去掉注释与字符串字面量，避免把 KDoc 里提到的类名当成代码引用。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        two = text[i:i + 2]
        if two == "/*":
            depth, i = 1, i + 2
            while i < n and depth:
                if text[i:i + 2] == "/*":
                    depth, i = depth + 1, i + 2
                elif text[i:i + 2] == "*/":
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
            out.append(" ")
        elif two == "//":
            while i < n and text[i] != "\n":
                i += 1
        elif text[i] == '"':
            if text[i:i + 3] == '"""':
                i += 3
                while i < n and text[i:i + 3] != '"""':
                    i += 1
                i += 3
            else:
                i += 1
                while i < n and text[i] != '"':
                    i += 2 if text[i] == "\\" else 1
                i += 1
            out.append('""')
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def main() -> int:
    # 类名 -> 所在 package 集合（同名类可能存在于多个包，保守起见只统计出现次数为 1 的名字）
    class_pkgs: dict[str, set[str]] = collections.defaultdict(set)

    files: list[Path] = []
    for src in SRC_DIRS:
        if src.exists():
            files.extend(p for p in src.rglob("*.kt") if "build" not in p.parts)

    pkg_of: dict[Path, str] = {}
    for path in files:
        text = read(path)
        m = PACKAGE_RE.search(text)
        pkg = m.group(1) if m else ""
        pkg_of[path] = pkg
        for m in CLASS_RE.finditer(text):
            name = m.group(1)
            if name[0].isupper():
                class_pkgs[name].add(pkg)

    # 只保留全仓库唯一的名字，避免把同名不同包的类误报
    known = {name for name, pkgs in class_pkgs.items() if len(pkgs) == 1}

    problems: list[str] = []
    for path in files:
        text = strip_noise(read(path))
        pkg = pkg_of[path]
        imported: set[str] = set()
        for m in IMPORT_RE.finditer(text):
            if m.group(2):
                imported.add(m.group(2))
            else:
                imported.add(m.group(1).rpartition(".")[2])
        local: set[str] = {m.group(1) for m in DECL_RE.finditer(text)}
        siblings = {n for n, p in class_pkgs.items() if pkg in p}

        for m in IDENT_RE.finditer(text):
            name = m.group(1)
            if name not in known:
                continue
            if name in imported or name in local or name in siblings:
                continue
            # 限定引用（`Glyph.Wifi`、`foo.Bar`）不需要 import 该名字
            if m.start() > 0 and text[m.start() - 1] == ".":
                continue
            # 忽略 import 行本身
            line_start = text.rfind("\n", 0, m.start()) + 1
            if text[line_start: m.start()].strip().startswith(("import ", "package ")):
                continue
            problems.append(f"{path.relative_to(ROOT)}: 使用了 {name} 但未 import（定义在 {next(iter(class_pkgs[name]))}）")

    seen = set()
    unique = [p for p in problems if not (p in seen or seen.add(p))]
    print(f"疑似漏 import {len(unique)} 处")
    for line in unique:
        print("  " + line)
    return 1 if unique else 0


if __name__ == "__main__":
    sys.exit(main())
