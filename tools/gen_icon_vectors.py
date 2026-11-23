"""从图标参数生成自适应图标的矢量层（前景 / 背景 / 单色）。

与 tools/gen_app_icon.py 共用同一套构图参数，所以矢量版与位图版视觉统一。
矢量层的好处是任意密度都清晰；自适应图标的前景主体必须落在中央 72×72
安全区（x/y 18–90）内，否则会被系统裁成圆形时切掉。

用法:
    python tools/gen_icon_vectors.py
"""

from __future__ import annotations

import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "app", "src", "main", "res", "drawable")

# ---- 与 gen_app_icon.py 保持一致的构图参数 -------------------------------
CANVAS = 108.0

RIBBON_CX = 54.0
RIBBON_TOP = 13.0
RIBBON_BOTTOM = 70.0
RIBBON_AMP = 14.5
RIBBON_TURNS = 1.15
RIBBON_THICK = 1.9

RIPPLE_CY = 75.0
RIPPLE_RX = [31.0, 21.5, 12.5]
RIPPLE_RY = [8.6, 6.0, 3.5]
RIPPLE_W = 1.0

CYAN = "#00F5FF"
BLUE = "#1E78FF"
VIOLET = "#AF28FF"

# 自适应图标前景要缩进安全区
FG_SCALE = 0.62
FG_CX = 54.0
FG_CY = 52.0


def _rot_ellipse_path(cx, cy, a, b, tx, ty, segments=24) -> str:
    px, py = -ty, tx
    pts = []
    for i in range(segments):
        th = 2 * math.pi * i / segments
        u = a * math.cos(th)
        v = b * math.sin(th)
        pts.append((cx + u * tx + v * px, cy + u * ty + v * py))
    head = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    rest = "".join(f" L{x:.2f},{y:.2f}" for x, y in pts[1:])
    return head + rest + " Z"


def _ribbon_path(phase: float) -> str:
    """一束螺旋光带的矢量路径（多个椭圆拼成的连续带）。"""
    parts = []
    steps = 72
    for i in range(steps):
        u = i / (steps - 1)
        ang = phase + u * RIBBON_TURNS * 2 * math.pi
        cx = RIBBON_CX + math.sin(ang) * RIBBON_AMP
        cy = RIBBON_BOTTOM + (RIBBON_TOP - RIBBON_BOTTOM) * u
        dx = math.cos(ang) * RIBBON_AMP * RIBBON_TURNS * 2 * math.pi
        dy = (RIBBON_TOP - RIBBON_BOTTOM)
        norm = math.hypot(dx, dy) or 1.0
        tx, ty = dx / norm, dy / norm
        # 前景层整体缩小并居中到安全区
        sx = FG_CX + (cx - 54.0) * FG_SCALE
        sy = FG_CY + (cy - 54.0) * FG_SCALE
        parts.append(
            _rot_ellipse_path(
                sx, sy,
                RIBBON_THICK * 1.25 * FG_SCALE,
                RIBBON_THICK * FG_SCALE,
                tx, ty,
            )
        )
    return " ".join(parts)


def _ripple_path() -> str:
    """底部三圈涟漪的矢量路径（椭圆描边用 fillType 描边表达）。"""
    out = []
    for rx, ry in zip(RIPPLE_RX, RIPPLE_RY):
        cx = FG_CX + (RIBBON_CX - 54.0) * FG_SCALE
        cy = FG_CY + (RIPPLE_CY - 54.0) * FG_SCALE
        rxs = rx * FG_SCALE
        rys = ry * FG_SCALE
        w = RIPPLE_W * FG_SCALE
        out.append(
            f"M{cx - rxs:.2f},{cy:.2f} "
            f"a{rxs:.2f},{rys:.2f} 0 1,0 {rxs * 2:.2f},0 "
            f"a{rxs:.2f},{rys:.2f} 0 1,0 {-rxs * 2:.2f},0 Z"
        )
    return " ".join(out)


FOREGROUND = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
    自适应图标前景（光屿 / LumiIsle）：
    一束自下而上螺旋上升的双股光带，底部亮青 -> 中部蔚蓝 -> 顶部紫罗兰，
    脚下是三圈同心青色椭圆涟漪。

    与固件招牌灯效「极光之舞」(mode_lumi_aurora) 同源同色。
    构图与 tools/gen_app_icon.py 渲染的位图版本一致，两者必须同步修改。

    画布 108×108，主体缩在中央安全区内（自适应图标会被系统裁成圆形/方形）。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- 主光带：自下而上 青 -> 蓝 -> 紫 -->
    <path
        android:pathData="{_ribbon_path(0.0)}"
        android:fillAlpha="0.95">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="54"
                android:startY="{RIBBON_BOTTOM * FG_SCALE + FG_CY - 54.0 * FG_SCALE:.2f}"
                android:endX="54"
                android:endY="{RIBBON_TOP * FG_SCALE + FG_CY - 54.0 * FG_SCALE:.2f}">
                <item android:offset="0.0" android:color="{CYAN}" />
                <item android:offset="0.5" android:color="{BLUE}" />
                <item android:offset="1.0" android:color="{VIOLET}" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- 副光带：相位相反，稍暗，制造交织的丝绸感 -->
    <path
        android:pathData="{_ribbon_path(math.pi)}"
        android:fillAlpha="0.62">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="54"
                android:startY="{RIBBON_BOTTOM * FG_SCALE + FG_CY - 54.0 * FG_SCALE:.2f}"
                android:endX="54"
                android:endY="{RIBBON_TOP * FG_SCALE + FG_CY - 54.0 * FG_SCALE:.2f}">
                <item android:offset="0.0" android:color="{CYAN}" />
                <item android:offset="0.5" android:color="{BLUE}" />
                <item android:offset="1.0" android:color="{VIOLET}" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- 底部青色涟漪：三圈同心椭圆 -->
    <path
        android:pathData="{_ripple_path()}"
        android:strokeColor="#00E2F5"
        android:strokeWidth="{RIPPLE_W * FG_SCALE:.2f}"
        android:fillColor="#00000000" />
</vector>
"""

BACKGROUND = """<?xml version="1.0" encoding="utf-8"?>
<!--
    自适应图标背景：深空蓝灰对角渐变。
    与 tools/gen_app_icon.py 的 BG_STOPS 保持一致（位图兜底与矢量化要视觉统一）。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0.0" android:color="#1E2634" />
                <item android:offset="0.55" android:color="#181F2C" />
                <item android:offset="1.0" android:color="#121824" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""

MONOCHROME = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
    单色图标（Android 13+ 主题图标 / themed icon）：只取剪影，由系统上色。
    「双股螺旋光带 + 三圈涟漪」的轮廓，与彩色版构图一致。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- 主光带剪影 -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{_ribbon_path(0.0)}" />

    <!-- 副光带剪影 -->
    <path
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="0.75"
        android:pathData="{_ribbon_path(math.pi)}" />

    <!-- 涟漪剪影 -->
    <path
        android:pathData="{_ripple_path()}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="{RIPPLE_W * FG_SCALE:.2f}"
        android:fillColor="#00000000" />
</vector>
"""


def main() -> None:
    os.makedirs(RES, exist_ok=True)
    for name, content in (
        ("ic_launcher_foreground.xml", FOREGROUND),
        ("ic_launcher_background.xml", BACKGROUND),
        ("ic_launcher_monochrome.xml", MONOCHROME),
    ):
        path = os.path.join(RES, name)
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        print("写入", path)


if __name__ == "__main__":
    main()
