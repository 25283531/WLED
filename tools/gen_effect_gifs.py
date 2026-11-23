#!/usr/bin/env python3
"""
通用灯效预览 GIF 生成器
========================

设计原则（2026-11-23 决策）
--------------------------
GIF 是 **效果示意**，不是某台设备的像素级仿真。因此：

* 画布固定 16:9，与真实灯珠数量/列数解耦；
* 同一张 GIF 可同时用于 1D 灯带、2D 矩阵、圆管缠绕等各种硬件形态；
* 内部用一块虚拟栅格（32 x 18）绘制，再等比放大，保证不同效果风格统一。

1D 类效果渲染成横向发光光带（模拟灯带/圆管上的光弧），
2D 类效果铺满整块虚拟矩阵。

用法
----
    python tools/gen_effect_gifs.py                # 生成全部
    python tools/gen_effect_gifs.py --only plasma  # 只生成指定效果
    python tools/gen_effect_gifs.py --list         # 列出可用效果名

解释器需要 Pillow：
    C:/Users/Administrator/.workbuddy/binaries/python/envs/default/Scripts/python.exe
"""

from __future__ import annotations

import argparse
import colorsys
import math
import os
import random
from typing import Callable

from PIL import Image, ImageChops, ImageDraw, ImageFilter

# ---------------------------------------------------------------- 画布参数

GRID_W, GRID_H = 32, 18          # 虚拟栅格（16:9）
CELL = 8                          # 每格像素 -> 画布 256 x 144
GAP = 2                           # 灯珠间隙（形成点阵感）
FRAMES = 24
FPS = 12
SAVE_COLORS = 128                 # 输出 GIF 的统一调色板色数（0 = 不量化）
BG = (8, 10, 14)
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "effect_previews")

CANVAS_W, CANVAS_H = GRID_W * CELL, GRID_H * CELL


# ---------------------------------------------------------------- 颜色工具

def hsv(h: float, s: float = 1.0, v: float = 1.0) -> tuple[int, int, int]:
    h = h % 1.0
    s = max(0.0, min(1.0, s))
    v = max(0.0, min(1.0, v))
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def scale(rgb: tuple[int, int, int], k: float) -> tuple[int, int, int]:
    k = max(0.0, min(1.0, k))
    return int(rgb[0] * k), int(rgb[1] * k), int(rgb[2] * k)


def add(a, b, k: float = 1.0):
    return (min(255, a[0] + int(b[0] * k)),
            min(255, a[1] + int(b[1] * k)),
            min(255, a[2] + int(b[2] * k)))


def lerp_rgb(a, b, t: float):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------- 渲染器

class Frame:
    """一帧虚拟栅格。1D 效果只写 1 行，输出时自动加发光扩散。"""

    def __init__(self, w=GRID_W, h=GRID_H):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0)] * w for _ in range(h)]

    def set(self, x: int, y: int, rgb):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = rgb

    def add(self, x: int, y: int, rgb, k: float = 1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = add(self.px[y][x], rgb, k)

    def fill(self, rgb):
        self.px = [[rgb] * self.w for _ in range(self.h)]

    def to_image(self, one_d: bool) -> Image.Image:
        img = Image.new("RGB", (CANVAS_W, CANVAS_H), BG)
        d = ImageDraw.Draw(img)

        for y in range(self.h):
            for x in range(self.w):
                rgb = self.px[y][x]
                if rgb != (0, 0, 0):
                    self._dot(d, x, y, rgb)

        if one_d:
            # 灯带/圆管是被扩散罩包住的，光会晕开：用一份模糊副本叠加，比画"辉光行"自然
            glow = img.filter(ImageFilter.GaussianBlur(5))
            glow = glow.point(lambda v: int(v * 0.55))
            img = ImageChops.add(img, glow)

        return img

    def _dot(self, d: ImageDraw.ImageDraw, x: int, y: int, rgb):
        x0, y0 = x * CELL + GAP // 2, y * CELL + GAP // 2
        d.rounded_rectangle([x0, y0, x0 + CELL - GAP, y0 + CELL - GAP], radius=2, fill=rgb)


# ---------------------------------------------------------------- 5x7 点阵字体

FONT5x7 = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'B': ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    'C': ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    'D': ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'F': ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    'G': ["01111", "10000", "10000", "10111", "10001", "10001", "01111"],
    'H': ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'J': ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    'K': ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'M': ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'P': ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    'Q': ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    'V': ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    'W': ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    'X': ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    'Y': ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    'Z': ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    '0': ["01110", "10011", "10101", "10101", "10101", "11001", "01110"],
    '1': ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    '2': ["01110", "10001", "00001", "00110", "01000", "10000", "11111"],
    '3': ["11111", "00010", "00100", "00010", "00001", "10001", "01110"],
    '4': ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    '5': ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    '6': ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    '7': ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    '8': ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    '9': ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
    ' ': ["00000"] * 7,
    '-': ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    '.': ["00000", "00000", "00000", "00000", "00000", "01100", "01100"],
    ':': ["00000", "01100", "01100", "00000", "01100", "01100", "00000"],
}

GLYPH_H = 7
GLYPH_W = 5
ADVANCE = 6          # 字宽 + 1px 间距


def text_width(s: str) -> int:
    return len(s) * ADVANCE - 1


def draw_text(fr: Frame, s: str, x0: int, y0: int, rgb):
    for i, ch in enumerate(s.upper()):
        glyph = FONT5x7.get(ch) or FONT5x7[' ']
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == '1':
                    fr.set(x0 + i * ADVANCE + gx, y0 + gy, rgb)


# ---------------------------------------------------------------- 效果实现
# 约定：1D 效果只写中间那一行（y = GRID_H // 2）

CY = GRID_H // 2


def fx_solid(t: float) -> Frame:
    fr = Frame()
    for x in range(GRID_W):
        fr.set(x, CY, (255, 214, 130))
    return fr


def fx_blink(t: float) -> Frame:
    fr = Frame()
    on = (t * 4) % 2 < 1          # 每个循环闪 2 次，太快会糊成一片
    for x in range(GRID_W):
        fr.set(x, CY, (255, 210, 120) if on else (12, 10, 6))
    return fr


def fx_breathe(t: float) -> Frame:
    fr = Frame()
    # 相位偏移 π/2：首帧处于中等亮度，避免 App 网格里的静态缩略图看起来是黑的
    k = 0.08 + 0.92 * (0.5 - 0.5 * math.cos(t * 2 * math.pi + math.pi / 2))
    for x in range(GRID_W):
        fr.set(x, CY, scale((255, 205, 140), k))
    return fr


def fx_wipe(t: float) -> Frame:
    fr = Frame()
    head = int(t * (GRID_W + 8))          # 首帧已有 1 颗亮起
    for x in range(GRID_W):
        if x <= head:
            fr.set(x, CY, (120, 220, 255))
    return fr


def fx_rainbow(t: float) -> Frame:
    fr = Frame()
    for x in range(GRID_W):
        fr.set(x, CY, hsv(x / GRID_W + t * 1.5, 1.0, 1.0))
    return fr


def fx_chase(t: float) -> Frame:
    fr = Frame()
    # 每帧整数像素位移，否则 32/周期性整除会让画面看起来"没在动"
    off = int(t * FRAMES) * 2
    for x in range(GRID_W):
        p = (x + off) % 8
        if p < 3:
            k = 1.0 - p * 0.28
            fr.set(x, CY, scale((255, 140, 70), k))
        elif p == 3:
            fr.set(x, CY, scale((255, 140, 70), 0.12))
    return fr


def fx_comet(t: float) -> Frame:
    fr = Frame()
    span = GRID_W + 10
    head = (t * span * 2 + 8) % span - 5   # +8 让首帧就有彗星头；整数倍才能无缝循环
    for i in range(9):
        x = int(head) - i
        k = (1.0 - i / 9.0) ** 2
        if 0 <= x < GRID_W:
            fr.set(x, CY, scale((180, 230, 255), k))
    return fr


def fx_twinkle(t: float) -> Frame:
    rnd = random.Random(7)
    fr = Frame()
    pts = [(rnd.randrange(GRID_W), rnd.random()) for _ in range(14)]
    for x, seed in pts:
        v = 0.5 + 0.5 * math.sin((t + seed) * 2 * math.pi * 1.5)
        fr.set(x, CY, scale((255, 255, 235), v ** 3))
    return fr


def fx_fire(t: float) -> Frame:
    fr = Frame()
    for x in range(GRID_W):
        n = (math.sin(x * 0.7 + t * 6) + math.sin(x * 1.9 - t * 9) * 0.5
             + math.sin(x * 3.3 + t * 13) * 0.25)
        v = 0.45 + 0.35 * n
        hue = 0.09 - 0.07 * max(0.0, v)
        fr.set(x, CY, hsv(hue, 1.0, max(0.05, min(1.0, v))))
    return fr


def fx_colorloop(t: float) -> Frame:
    fr = Frame()
    rgb = hsv(t, 0.85, 1.0)
    for x in range(GRID_W):
        fr.set(x, CY, rgb)
    return fr


def fx_plasma(t: float) -> Frame:
    fr = Frame()
    tt = t * 2 * math.pi
    for y in range(GRID_H):
        for x in range(GRID_W):
            v = (math.sin(x * 0.45 + tt) + math.sin(y * 0.6 - tt * 0.8)
                 + math.sin((x + y) * 0.3 + tt * 1.3)
                 + math.sin(math.hypot(x - GRID_W / 2, y - GRID_H / 2) * 0.35 - tt)) / 4
            fr.set(x, y, hsv(0.62 + v * 0.18, 0.88, 0.72 + 0.28 * v))
    return fr


def fx_fireworks(t: float) -> Frame:
    rnd = random.Random(11)
    bursts = [(rnd.uniform(4, GRID_W - 4), rnd.uniform(3, GRID_H - 3),
               rnd.random(), rnd.random()) for _ in range(5)]
    fr = Frame()
    for cx, cy_, phase, hue in bursts:
        p = (t + phase) % 1.0
        if p > 0.8:
            continue
        rgb = hsv(hue * 3, 0.55, 1.0)
        # 起爆瞬间的核心闪光
        if p < 0.18:
            fr.add(int(cx), int(cy_), (255, 255, 255), (1 - p / 0.18) * 1.4)
        radius = (p ** 0.6) * 15
        fade = (1.0 - p / 0.8) ** 0.8
        for a in range(18):
            ang = a / 18 * 2 * math.pi + phase * 6
            rr = radius * (0.85 + 0.15 * math.sin(a * 2.7 + phase * 9))
            x = int(round(cx + math.cos(ang) * rr))
            y = int(round(cy_ + math.sin(ang) * rr))
            fr.add(x, y, rgb, fade)
            # 拖尾：沿半径方向再补一颗暗一点的
            if p > 0.1:
                fr.add(int(round(cx + math.cos(ang) * rr * 0.82)),
                       int(round(cy_ + math.sin(ang) * rr * 0.82)), rgb, fade * 0.45)
    return fr


def fx_matrix_rain(t: float) -> Frame:
    rnd = random.Random(3)
    cols = [(x, rnd.random(), rnd.uniform(0.6, 1.6)) for x in range(GRID_W)]
    fr = Frame()
    for x, phase, spd in cols:
        head = (t * spd * GRID_H * 1.3 + phase * GRID_H * 3) % (GRID_H + 8) - 4
        for i in range(5):
            y = int(head) - i
            if 0 <= y < GRID_H:
                k = (1.0 - i / 5.0) ** 1.6
                fr.add(x, y, (60, 255, 120), k)
    return fr


TEXT = "COLOR LAMP"      # 宽度 59 > 32，固件会自动走横向滚动
TEXT_V = "LAMP"          # 宽度 23 <= 32，固件会走纵向滚动


def fx_text_h(t: float) -> Frame:
    """横向滚动：文字沿圆周方向移动（圆管上观感最佳）。

    画两份（间距 = 一个完整周期）实现无缝循环，同时保证首帧画面中间就有字。
    """
    fr = Frame()
    span = text_width(TEXT) + GRID_W
    off = GRID_W // 2 - int(t * span)
    y0 = (GRID_H - GLYPH_H) // 2
    draw_text(fr, TEXT, off, y0, (255, 196, 92))
    draw_text(fr, TEXT, off + span, y0, (255, 196, 92))
    return fr


def fx_text_v(t: float) -> Frame:
    """纵向滚动：整行文字上下移动（文字宽度未超出矩阵时固件的默认行为）。"""
    fr = Frame()
    span = GRID_H + GLYPH_H
    x0 = (GRID_W - text_width(TEXT_V)) // 2
    base = (GRID_H - GLYPH_H) // 2
    off = base - int(t * span)
    draw_text(fr, TEXT_V, x0, off, (120, 220, 255))
    draw_text(fr, TEXT_V, x0, off + span, (120, 220, 255))
    return fr


# name -> (渲染函数, 是否按 1D 光带渲染)
EFFECTS: dict[str, tuple[Callable[[float], Frame], bool]] = {
    "solid":      (fx_solid, True),
    "blink":      (fx_blink, True),
    "breathe":    (fx_breathe, True),
    "wipe":       (fx_wipe, True),
    "rainbow":    (fx_rainbow, True),
    "chase":      (fx_chase, True),
    "comet":      (fx_comet, True),
    "twinkle":    (fx_twinkle, True),
    "fire":       (fx_fire, True),
    "colorloop":  (fx_colorloop, True),
    "plasma":     (fx_plasma, False),
    "fireworks":  (fx_fireworks, False),
    "matrix_rain": (fx_matrix_rain, False),
    "text_h":     (fx_text_h, False),
    "text_v":     (fx_text_v, False),
}


# ---------------------------------------------------------------- 主流程

def build(name: str, out_dir: str) -> str:
    fn, one_d = EFFECTS[name]
    frames = []
    for f in range(FRAMES):
        t = f / FRAMES
        frames.append(fn(t).to_image(one_d))

    # 统一调色板量化：渐变类效果（plasma/fire）原始 24 位色会让 GIF 膨胀到 300KB+。
    # 调色板必须由**全部帧**的合图统计出来——只取单帧的话，像 comet 这种中间帧几乎全黑，
    # 调色板里没有亮色，整段动画会被压成纯黑。
    if SAVE_COLORS:
        montage = Image.new("RGB", (CANVAS_W, CANVAS_H * len(frames)), BG)
        for i, f in enumerate(frames):
            montage.paste(f, (0, i * CANVAS_H))
        base = montage.quantize(colors=SAVE_COLORS)
        frames = [f.quantize(palette=base, dither=Image.NONE) for f in frames]

    path = os.path.join(out_dir, f"{name}.gif")
    frames[0].save(path, save_all=True, append_images=frames[1:],
                   duration=int(1000 / FPS), loop=0, optimize=True)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", help="只生成指定效果")
    ap.add_argument("--list", action="store_true", help="列出可用效果")
    ap.add_argument("--out", default=OUT_DIR)
    args = ap.parse_args()

    if args.list:
        for k, (_, one_d) in EFFECTS.items():
            print(f"{k:12s} {'1D' if one_d else '2D'}")
        return

    os.makedirs(args.out, exist_ok=True)
    names = args.only or list(EFFECTS)
    total = 0
    for n in names:
        if n not in EFFECTS:
            print(f"!! 未知效果: {n}")
            continue
        p = build(n, args.out)
        size = os.path.getsize(p)
        total += size
        print(f"{os.path.basename(p):20s} {size/1024:7.1f} KB")
    print(f"{'合计':20s} {total/1024:7.1f} KB  ({len(names)} 个)")


if __name__ == "__main__":
    main()
