#!/usr/bin/env python3
"""粗粒度静态检查：确认 Kotlin 文件里的 project-internal import 都能找到对应声明。

不替代编译器，只用来在推送 CI 之前抓"引用了不存在的符号 / 包名写错"这类低级错误。
"""
from __future__ import annotations

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
PREFIX = "ca.cgagnier.wlednativeandroid"

PACKAGE_RE = re.compile(r"^package\s+([\w.]+)", re.M)
IMPORT_RE = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+\w+)?", re.M)
# 顶层声明：class / object / interface / enum class / fun / val / var / typealias / data class / sealed ...
DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|abstract\s+|open\s+|sealed\s+|data\s+|value\s+|inline\s+|const\s+|enum\s+|annotation\s+|suspend\s+|operator\s+|override\s+|external\s+|tailrec\s+|lateinit\s+)*"
    r"(?:fun\s+interface|class|interface|object|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?"
    r"(?:[\w.]+\.)?"  # 扩展函数接收者
    r"(\w+)",
    re.M,
)


def declared_names(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8", errors="replace")
    names: set[str] = set()
    for match in DECL_RE.finditer(text):
        line = text[text.rfind("\n", 0, match.start()) + 1: match.start()]
        if line.strip():
            continue  # 声明必须从行首开始（允许注解在上一行）
        names.add(match.group(1))
    return names


def main() -> int:
    # package -> 顶层声明名集合
    packages: dict[str, set[str]] = {}
    # package -> 该包下所有文件（用于判断"包存在但符号不可见"）
    for src in SRC_DIRS:
        if not src.exists():
            continue
        for path in src.rglob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="replace")
            pkg_match = PACKAGE_RE.search(text)
            if not pkg_match:
                continue
            packages.setdefault(pkg_match.group(1), set()).update(declared_names(path))

    problems: list[str] = []
    checked = 0
    for src in SRC_DIRS:
        if not src.exists():
            continue
        for path in src.rglob("*.kt"):
            if "build" in path.parts:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            own_pkg = (PACKAGE_RE.search(text) or [None, ""])[1] if PACKAGE_RE.search(text) else ""
            for imp in IMPORT_RE.finditer(text):
                fq = imp.group(1)
                if not fq.startswith(PREFIX):
                    continue
                # 编译期生成的符号
                if fq.endswith(".R") or fq.endswith(".BuildConfig"):
                    continue
                checked += 1
                pkg, _, symbol = fq.rpartition(".")
                if symbol == "*":
                    if pkg not in packages:
                        problems.append(f"{path.relative_to(ROOT)}: 包不存在 -> {fq}")
                    continue
                # 顶层符号
                if symbol in packages.get(pkg, set()):
                    continue
                # 嵌套符号 / 伴生对象成员：包名部分可能是类名，逐级回退
                parts = fq.split(".")
                resolved = False
                for cut in range(len(parts) - 1, 2, -1):
                    cand_pkg = ".".join(parts[:cut])
                    cand_name = parts[cut]
                    if cand_name in packages.get(cand_pkg, set()):
                        resolved = True
                        break
                if not resolved:
                    problems.append(f"{path.relative_to(ROOT)}: 符号不存在 -> {fq}")

    print(f"检查 import {checked} 条，问题 {len(problems)} 个")
    for line in problems:
        print("  " + line)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
