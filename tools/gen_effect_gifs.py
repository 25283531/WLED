#!/usr/bin/env python3
"""灯效预览 GIF 生成器（32×18 栅格，逐效果一份）

设计要点（2026-11-23 修订）
--------------------------
1. **全部按 32×18 栅格渲染**，不再把 1D 灯效画成一条光带。
   产品是 2D 圆管矩阵，1D 灯效在固件里也是沿 S 形（蛇形）排布铺满整块的，
   画成一行会和实际观感完全不一致。
2. 文件以效果 id 命名（`<idx>.gif`），id 与固件 `seg.fx` 一一对应，
   避免"名字对得上、图对不上"的错配。
3. 每个效果一个渲染函数；同一族（追逐 / 噪声 / 频谱 / 火焰 ...）共用实现，
   参数按效果名调整。这是**示意动画**，不是固件算法的逐像素复刻 ——
   WLED 有 189 个效果，逐个移植不现实，但族内行为与观感是对齐的。

用法
----
    python tools/gen_effect_gifs.py              # 生成全部
    python tools/gen_effect_gifs.py --only 31 63 # 只生成指定 id
    python tools/gen_effect_gifs.py --list
"""

from __future__ import annotations

import argparse
import colorsys
import json
import math
import os
import random
import re

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EFFECTS_FILE = os.path.join(ROOT, "firmware", "effects_enabled.json")
OUT_DIR = os.path.join(ROOT, "app", "app", "src", "main", "assets", "effect_previews")

#: 固件内置的 5×12 点阵字体（wbf 格式）。字幕预览直接用它渲染，
#: 这样 GIF 里的字形、字高、字距与设备上真正显示的一致。
WLED_FONT_FILE = os.path.join(ROOT, "firmware", "wled00", "src", "font", "font_5x12.h")

# ------------------------------------------------------------------ 画布
W, H = 32, 18
CELL, GAP = 4, 1
FRAMES, FPS = 16, 12
CANVAS_W, CANVAS_H = W * CELL, H * CELL
BG = (6, 7, 10)
SAVE_COLORS = 40

Grid = list  # list[list[(r,g,b)]]


# ------------------------------------------------------------------ 颜色
def hsv(h: float, s: float = 1.0, v: float = 1.0):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return int(r * 255), int(g * 255), int(b * 255)


def dim(c, k: float):
    k = max(0.0, min(1.0, k))
    return int(c[0] * k), int(c[1] * k), int(c[2] * k)


def mix(a, b, t: float):
    t = max(0.0, min(1.0, t))
    return int(a[0] + (b[0] - a[0]) * t), int(a[1] + (b[1] - a[1]) * t), int(a[2] + (b[2] - a[2]) * t)


def add(a, b, k: float = 1.0):
    return (min(255, int(a[0] + b[0] * k)),
            min(255, int(a[1] + b[1] * k)),
            min(255, int(a[2] + b[2] * k)))


WARM = (255, 190, 110)
CYAN = (90, 220, 255)
GREEN = (80, 240, 140)
VIOLET = (150, 120, 255)
PINK = (255, 120, 200)
ORANGE = (255, 130, 60)
RED = (255, 70, 70)
BLUE = (70, 120, 255)
WHITE = (255, 250, 240)

FIRE_STOPS = [0.0, (40, 4, 2), 0.35, (170, 34, 8), 0.65, (255, 110, 20), 0.85, (255, 200, 90), 1.0, (255, 255, 220)]
RAINBOW_STOPS = [
    0.0, (255, 40, 60), 0.17, (255, 190, 40), 0.33, (120, 255, 80),
    0.5, (40, 240, 220), 0.67, (60, 120, 255), 0.83, (170, 80, 255), 1.0, (255, 40, 200),
]
OCEAN_STOPS = [0.0, (2, 10, 30), 0.4, (12, 70, 140), 0.7, (30, 170, 200), 1.0, (180, 250, 255)]
AURORA_STOPS = [0.0, (6, 10, 24), 0.3, (24, 110, 130), 0.6, (60, 230, 180), 0.85, (170, 255, 210), 1.0, (230, 255, 255)]


def gradient(stops, t: float):
    t = max(0.0, min(1.0, t))
    for i in range(0, len(stops), 2):
        if i + 2 >= len(stops) or t <= stops[i + 2]:
            p0, c0 = stops[i], stops[i + 1]
            p1, c1 = (stops[i + 2], stops[i + 3]) if i + 2 < len(stops) - 1 else (1.0, stops[-1])
            span = max(1e-6, p1 - p0)
            return mix(c0, c1, (t - p0) / span)
    return stops[-1]


# ------------------------------------------------------------------ 栅格
def empty() -> Grid:
    return [[(0, 0, 0)] * W for _ in range(H)]


def serpentine(values) -> Grid:
    """LED 序号 -> 栅格坐标：S 形走线，起点在底部（与产品的贴片方式一致）。"""
    grid = empty()
    for index, color in enumerate(values):
        row, col = divmod(index, W)
        if row >= H:
            break
        y = H - 1 - row
        x = col if row % 2 == 0 else W - 1 - col
        grid[y][x] = color
    return grid


def from_strip(fn) -> Grid:
    """1D 灯效：先在一条 576 颗的灯带上算颜色，再按 S 形铺到矩阵。"""
    return serpentine([fn(i, W * H) for i in range(W * H)])


def noise1(x: float, seed: int) -> float:
    """确定性的一维平滑噪声（正弦叠加，比随机数更适合做流动感）。"""
    return (math.sin(x * 1.7 + seed) * 0.5
            + math.sin(x * 3.1 - seed * 1.7) * 0.3
            + math.sin(x * 6.7 + seed * 0.3) * 0.2)


# ------------------------------------------------------------------ 1D 渲染器
def r_solid(t, color):
    return from_strip(lambda i, n: color)


def r_blink(t, color, hz=1.0):
    on = (t * hz * 2) % 1.0 < 0.5
    return from_strip(lambda i, n: color if on else (0, 0, 0))


def r_breathe(t, color, hz=1.0):
    k = 0.5 - 0.5 * math.cos(t * 2 * math.pi * hz + math.pi / 2)
    return from_strip(lambda i, n: dim(color, 0.06 + 0.94 * k))


def r_wipe(t, color, cycles=1.0):
    head = (t * cycles * (W * H + 30)) % (W * H + 30)
    return from_strip(lambda i, n: color if i <= head else (0, 0, 0))


def r_rainbow(t, turns=1.0, cycles=1.0):
    return from_strip(lambda i, n: hsv(i / n * turns + t * cycles, 1.0, 1.0))


def r_hue_cycle(t, cycles=1.0, sat=0.9):
    return from_strip(lambda i, n: hsv(t * cycles, sat, 1.0))


def r_chase(t, color, width=3, blocks=4, cycles=1.0):
    period = max(1, W * H // blocks)

    def color_at(i, n):
        p = (i + int(t * cycles * period * blocks)) % period
        if p < width:
            return dim(color, 1.0 - p / max(1, width) * 0.75)
        return dim(color, 0.05)

    return from_strip(color_at)


def r_comet(t, color, tail=12, cycles=1.0, count=1):
    span = W * H + tail

    def color_at(i, n):
        best = 0.0
        for c in range(count):
            head = (t * cycles * span + c * span / count) % span
            d = (head - i) % span
            if d < tail:
                best = max(best, (1.0 - d / tail) ** 1.8)
        return dim(color, best)

    return from_strip(color_at)


def r_twinkle(t, palette, density=0.12, seed=1.0):
    rnd = random.Random(int(seed * 1000) + 7)
    points = [(rnd.randrange(W * H), rnd.random(), rnd.choice(palette)) for _ in range(int(W * H * density))]

    def color_at(i, n):
        for idx, phase, color in points:
            if idx == i:
                v = 0.5 + 0.5 * math.sin((t + phase) * 2 * math.pi * 2)
                return dim(color, v ** 2.2)
        return (0, 0, 0)

    return from_strip(color_at)


def r_sparkle(t, color, density=0.06, seed=2.0):
    rnd = random.Random(int(seed * 977) + 3)
    points = {rnd.randrange(W * H): rnd.random() for _ in range(int(W * H * density))}

    def color_at(i, n):
        phase = points.get(i)
        if phase is None:
            return dim(color, 0.12)
        v = max(0.0, math.sin((t + phase) * 2 * math.pi * 3))
        return dim(color, v ** 3)

    return from_strip(color_at)


def r_noise_flow(t, stops, scale=0.12, cycles=1.0, seed=1.0):
    return from_strip(
        lambda i, n: gradient(stops, 0.5 + 0.5 * noise1(i * scale + t * cycles * 6, seed)),
    )


def r_fire(t, seed=1.0, heat=1.0):
    def color_at(i, n):
        v = 0.5 + 0.5 * noise1(i * 0.25 + t * 10, seed)
        v = min(1.0, v * heat)
        return gradient(FIRE_STOPS, v)

    return from_strip(color_at)


def r_strobe(t, color, hz=4.0):
    on = (t * hz) % 1.0 < 0.12
    return from_strip(lambda i, n: color if on else (0, 0, 0))


def r_bouncing(t, colors, count=3, seed=5.0):
    rnd = random.Random(int(seed * 131))
    balls = [(rnd.randrange(W * H), rnd.uniform(0.5, 1.4), rnd.choice(colors)) for _ in range(count)]

    def color_at(i, n):
        for origin, speed, color in balls:
            pos = int((origin + t * speed * n) % n)
            d = min((i - pos) % n, (pos - i) % n)
            if d <= 2:
                return dim(color, 1.0 - d * 0.3)
        return (0, 0, 0)

    return from_strip(color_at)


def r_bars(t, colors, seed=1.0, count=12):
    rnd = random.Random(int(seed * 313))
    heights = [rnd.uniform(0.15, 1.0) for _ in range(count)]

    def color_at(i, n):
        row, col = divmod(i, W)
        band = col * count // W
        level = 0.5 + 0.5 * math.sin(t * 2 * math.pi * 2 + heights[band] * 6 + band * 0.7)
        h = heights[band] * level
        if row < h * H:
            return gradient(RAINBOW_STOPS, 1.0 - row / H)
        return (0, 0, 0)

    return from_strip(color_at)


def r_bars_2d(t, seed=1.0):
    rnd = random.Random(int(seed * 313))
    count = 10
    heights = [rnd.uniform(0.2, 1.0) for _ in range(count)]
    grid = empty()
    for x in range(W):
        band = x * count // W
        level = 0.5 + 0.5 * math.sin(t * 2 * math.pi * 2 + heights[band] * 6 + band * 0.8)
        h = heights[band] * level
        for y in range(H):
            if y > H - h * H:
                grid[y][x] = gradient(RAINBOW_STOPS, 1.0 - y / H)
    return grid


def r_freq_pixels(t):
    """频谱像素：4 个频段各一种颜色，数量随能量变化，位置随机散开。"""
    colors = [(0x00, 0xF5, 0xFF), (0x1E, 0x78, 0xFF), (0xAF, 0x28, 0xFF), (0x3C, 0xFF, 0xB0)]
    energies = [
        0.55 + 0.45 * math.sin(t * 2 * math.pi * 0.9),
        0.45 + 0.35 * math.sin(t * 2 * math.pi * 1.4 + 1.1),
        0.40 + 0.35 * math.sin(t * 2 * math.pi * 1.9 + 2.2),
        0.30 + 0.40 * math.sin(t * 2 * math.pi * 2.6 + 3.0),
    ]
    cells = [(x, y) for y in range(H) for x in range(W)]
    block = int(t * FRAMES) // 4   # 与固件一致：随机排列每几帧重排一次
    rnd = random.Random(block * 97 + 11)
    rnd.shuffle(cells)
    grid = empty()
    idx = 0
    for color, energy in zip(colors, energies):
        count = int(len(cells) * max(0.0, energy) * 0.25)
        for _ in range(count):
            x, y = cells[idx % len(cells)]
            idx += 1
            grid[y][x] = color
    return grid


def r_dots(t, colors, count=5, seed=6.0, tail=3):
    rnd = random.Random(int(seed * 89))
    dots = [(rnd.uniform(0, 1), rnd.uniform(0.3, 1.2), rnd.choice(colors)) for _ in range(count)]

    def color_at(i, n):
        best = (0, 0, 0)
        for phase, speed, color in dots:
            pos = (phase + t * speed) % 1.0 * n
            d = abs(((i - pos + n / 2) % n) - n / 2)
            if d < tail:
                best = add(best, dim(color, (1.0 - d / tail) ** 2))
        return best

    return from_strip(color_at)


def r_gradient_flow(t, stops, cycles=1.0):
    return from_strip(lambda i, n: gradient(stops, (i / n + t * cycles) % 1.0))


def r_rain(t, color=BLUE, drops=10, seed=3.0):
    rnd = random.Random(int(seed * 71))
    columns = [(rnd.randrange(W * H), rnd.uniform(0.6, 1.6)) for _ in range(drops)]

    def color_at(i, n):
        for start, speed in columns:
            head = int((start + t * speed * n * 1.6) % (n + 60))
            d = (head - i) % (n + 60)
            if d < 8:
                return dim(color, (1.0 - d / 8) ** 1.6)
        return (0, 0, 0)

    return from_strip(color_at)


def r_percent_bar(t, color=GREEN):
    fill = (0.5 + 0.5 * math.sin(t * 2 * math.pi)) * W * H
    return from_strip(lambda i, n: color if i < fill else dim(color, 0.06))


def r_traffic(t):
    third = W * H // 3

    def color_at(i, n):
        phase = int(t * 3) % 3
        if i < third:
            return RED if phase == 0 else dim(RED, 0.08)
        if i < third * 2:
            return (255, 200, 40) if phase == 1 else dim((255, 200, 40), 0.08)
        return GREEN if phase == 2 else dim(GREEN, 0.08)

    return from_strip(color_at)


def r_eyes(t, color=(255, 120, 40)):
    grid = empty()
    # 两对眼睛，每隔一会儿眨一次
    blink = (t * 2) % 1.0 < 0.15
    pairs = [(6, 4), (6, 12), (22, 4), (22, 12)]
    for x, y in pairs:
        for dx in range(2):
            for dy in range(2 if not blink else 1):
                if 0 <= x + dx < W and 0 <= y + dy < H:
                    grid[y + dy][x + dx] = color
    return grid


def r_text_scroll(t, text, color=WARM, vertical=False):
    """滚动字幕：与固件 `mode_2Dscrollingtext` + font 4 + 加粗一致。

    字形取自固件的 5×12 点阵字体，逐字加粗（笔画 2 颗灯珠），
    字高 12/18 行，纯黑底 + 单色高亮。
    """
    grid = empty()
    if vertical:
        width = text_width(text)
        x0 = max(0, (W - width) // 2)
        span = H + TEXT_H
        off = (H - TEXT_H) // 2 - int(t * span)
        draw_text(grid, text, x0, off, color)
        draw_text(grid, text, x0, off + span, color)
        return grid
    span = text_width(text) + W
    off = W // 2 - int(t * span)
    y0 = (H - TEXT_H) // 2
    draw_text(grid, text, off, y0, color)
    draw_text(grid, text, off + span, y0, color)
    return grid


def r_matrix(t, color=(60, 255, 120), seed=4.0):
    rnd = random.Random(int(seed * 37))
    columns = [(rnd.random(), rnd.uniform(0.7, 1.7)) for _ in range(W)]
    grid = empty()
    for x, (phase, speed) in enumerate(columns):
        head = (t * speed * H * 1.4 + phase * H * 3) % (H + 8) - 4
        for i in range(6):
            y = int(head) - i
            if 0 <= y < H:
                grid[y][x] = dim(color, (1.0 - i / 6) ** 1.6)
    return grid


# ------------------------------------------------------------------ 2D 渲染器
def r_lumi_aurora(t, twist=16):
    """光屿招牌灯效「极光之舞」。

    与固件 `mode_lumi_aurora`（firmware/wled00/FX.cpp）逐行对应：
      1. 底部第 0~1 行叠加青色呼吸涟漪底座
      2. 两条反向交织的螺旋丝带，圆周角随高度旋转（交织幅度 = twist）
      3. 主色调自下而上 亮青(#00F5FF) -> 蔚蓝(#1E78FF) -> 紫罗兰(#AF28FF)
      4. 丝带交叉处用饱和加法叠加，得到丝绸重叠的高亮通透感
    """
    grid = empty()
    # 固件里 t = (strip.now * (12 + speed/4)) >> 7，这里换算成 0~1 的循环时间
    tt = t * 2 * math.pi
    ripple = 0.5 - 0.5 * math.cos(tt * 18 / 60.0 * 2 * math.pi)
    ripple_breath = 90 + int(120 * ripple)

    for y in range(H):
        # 固件的 y=0 是矩阵最底部，本渲染器栅格 y=0 是画面顶部，所以要翻转。
        # 这样 posY=0（亮青）才落在画面下缘，与实物的「底部青色涟漪」一致。
        pos_y = int((H - 1 - y) * 255 / (H - 1)) if H > 1 else 0
        # 垂直主色调
        if pos_y < 128:
            frac = pos_y / 128.0
            ribbon = mix((0, 245, 255), (30, 120, 255), frac)
        else:
            frac = (pos_y - 128) / 127.0
            ribbon = mix((30, 120, 255), (175, 40, 255), frac)

        # 两条丝带在当前高度的圆周中心角（0~255 = 0~360 度）
        center_a = int(math.sin((pos_y * twist / 16 + tt * 12) / 256 * 2 * math.pi) * 127.5 + 127.5) & 0xFF
        center_b = int(math.cos((pos_y * twist / 14 - tt * 12) / 256 * 2 * math.pi) * 127.5 + 127.5) & 0xFF

        for x in range(W):
            angle_x = (x * 256) // W

            diff_a = abs(angle_x - center_a)
            if diff_a > 128:
                diff_a = 256 - diff_a
            diff_b = abs(angle_x - center_b)
            if diff_b > 128:
                diff_b = 256 - diff_b

            # 丝绸柔光衰减（宽度约占圆周 1/4）
            glow_a = 0.0
            if diff_a < 46:
                glow_a = ((255 - (diff_a * 255) // 46) / 255.0) ** 2
            glow_b = 0.0
            if diff_b < 42:
                glow_b = ((255 - (diff_b * 255) // 42) / 255.0) ** 2

            pixel = [int(c * glow_a) for c in ribbon]
            second = [int(c * glow_b * (195 / 255.0)) for c in ribbon]

            # 交叉处饱和加法叠加
            pixel = [min(255, pixel[i] + second[i]) for i in range(3)]

            # 底部青色涟漪底座（固件加在 y<=1，即矩阵最底部两行）
            if pos_y <= 1:
                strength = ripple_breath if pos_y == 0 else (ripple_breath >> 2)
                base = (0, int(220 * strength / 255), int(255 * strength / 255))
                pixel = [min(255, pixel[i] + base[i]) for i in range(3)]

            grid[y][x] = tuple(pixel)
    return grid


def r_plasma(t, cycles=1.0, hue=0.62, sat=0.9):
    grid = empty()
    tt = t * 2 * math.pi * cycles
    for y in range(H):
        for x in range(W):
            v = (math.sin(x * 0.45 + tt) + math.sin(y * 0.6 - tt * 0.8)
                 + math.sin((x + y) * 0.3 + tt * 1.3)
                 + math.sin(math.hypot(x - W / 2, y - H / 2) * 0.35 - tt)) / 4
            grid[y][x] = hsv(hue + v * 0.18, sat, 0.72 + 0.28 * v)
    return grid


def r_fireworks(t, seed=11.0):
    rnd = random.Random(int(seed * 53))
    bursts = [(rnd.uniform(4, W - 4), rnd.uniform(3, H - 3), rnd.random(), rnd.random()) for _ in range(4)]
    grid = empty()
    for cx, cy, phase, hue in bursts:
        p = (t + phase) % 1.0
        if p > 0.85:
            continue
        color = hsv(hue, 0.6, 1.0)
        if p < 0.15:
            grid[int(cy)][int(cx)] = add(grid[int(cy)][int(cx)], WHITE, (1 - p / 0.15) * 1.4)
        radius = (p ** 0.6) * 9
        fade = (1.0 - p / 0.85) ** 0.8
        for a in range(16):
            ang = a / 16 * 2 * math.pi + phase * 6
            rr = radius * (0.85 + 0.15 * math.sin(a * 2.7 + phase * 9))
            x = int(round(cx + math.cos(ang) * rr))
            y = int(round(cy + math.sin(ang) * rr))
            if 0 <= x < W and 0 <= y < H:
                grid[y][x] = add(grid[y][x], color, fade)
                if p > 0.1:
                    x2 = int(round(cx + math.cos(ang) * rr * 0.8))
                    y2 = int(round(cy + math.sin(ang) * rr * 0.8))
                    if 0 <= x2 < W and 0 <= y2 < H:
                        grid[y2][x2] = add(grid[y2][x2], color, fade * 0.45)
    return grid


def r_spiral(t, cycles=1.0):
    grid = empty()
    for y in range(H):
        for x in range(W):
            dx, dy = x - W / 2, y - H / 2
            ang = math.atan2(dy, dx) / (2 * math.pi)
            rad = math.hypot(dx, dy)
            v = 0.5 + 0.5 * math.sin((ang * 2 + rad * 0.12 - t * cycles) * 2 * math.pi)
            grid[y][x] = hsv(ang + t * 0.5, 0.9, v)
    return grid


def r_blobs(t, cycles=1.0, seed=8.0):
    rnd = random.Random(int(seed))
    centers = [(rnd.uniform(3, W - 3), rnd.uniform(2, H - 2), rnd.uniform(0.6, 1.4), rnd.random())
               for _ in range(5)]
    grid = empty()
    for y in range(H):
        for x in range(W):
            best, hue = 0.0, 0.0
            for cx, cy, speed, ph in centers:
                bx = cx + 5 * math.sin(t * 2 * math.pi * speed + ph * 6)
                by = cy + 3 * math.cos(t * 2 * math.pi * speed + ph * 5)
                v = math.exp(-((x - bx) ** 2 + (y - by) ** 2) / 14)
                if v > best:
                    best, hue = v, ph
            if best > 0.08:
                grid[y][x] = hsv(hue + t * 0.2, 0.78, min(1.0, best * 1.3))
    return grid


def r_life(t, seed=13.0):
    rnd = random.Random(int(seed))
    cells = [[rnd.random() < 0.28 for _ in range(W)] for _ in range(H)]
    steps = int(t * 12)
    for _ in range(steps):
        nxt = [[False] * W for _ in range(H)]
        for y in range(H):
            for x in range(W):
                n = sum(cells[(y + dy) % H][(x + dx) % W]
                        for dy in (-1, 0, 1) for dx in (-1, 0, 1) if (dx, dy) != (0, 0))
                nxt[y][x] = n == 3 or (cells[y][x] and n == 2)
        cells = nxt
    grid = empty()
    for y in range(H):
        for x in range(W):
            if cells[y][x]:
                grid[y][x] = hsv(0.45 + (x + y) / 60, 0.8, 1.0)
    return grid


def r_ripple(t, color=CYAN, cycles=1.0):
    grid = empty()
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - W / 2, y - H / 2)
            v = 0.5 + 0.5 * math.sin(d * 1.3 - t * 2 * math.pi * cycles)
            grid[y][x] = dim(color, v ** 2)
    return grid


def r_radial(t, color=WARM, cycles=1.0):
    grid = empty()
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - W / 2, y - H / 2)
            v = max(0.0, math.sin(d * 0.5 - t * 2 * math.pi * cycles))
            grid[y][x] = dim(color, v ** 1.5)
    return grid


def r_waves(t, stops=AURORA_STOPS, cycles=1.0):
    grid = empty()
    for y in range(H):
        for x in range(W):
            v = (math.sin(x * 0.32 + t * 2 * math.pi * cycles)
                 + math.sin(y * 0.55 - t * 2 * math.pi * cycles * 0.7)
                 + math.sin((x * 0.4 + y * 0.6) + t * 3)) / 3
            grid[y][x] = gradient(stops, 0.5 + 0.5 * v)
    return grid


def r_noise2d(t, stops=OCEAN_STOPS, seed=9.0, scale=0.35):
    grid = empty()
    for y in range(H):
        for x in range(W):
            v = 0.5 + 0.5 * noise1((x * scale + y * scale * 1.7) + t * 8, seed)
            grid[y][x] = gradient(stops, v)
    return grid


def r_lissajous(t, color=VIOLET, count=3):
    grid = empty()
    for k in range(count):
        px = W / 2 + (W / 2 - 2.5) * math.sin(t * 2 * math.pi * (1 + k * 0.3) + k)
        py = H / 2 + (H / 2 - 2.5) * math.sin(t * 2 * math.pi * (1.4 + k * 0.2))
        grid[int(py)][int(px)] = color
    return grid


def r_clock(t):
    grid = empty()
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - W / 2, (y - H / 2) * 1.2)
            if 7.6 < d < 8.6:
                grid[y][x] = dim(WHITE, 0.5)
    angle = t * 2 * math.pi
    for i in range(7):
        x = int(W / 2 + math.sin(angle) * i)
        y = int(H / 2 - math.cos(angle) * i * 0.62)
        if 0 <= x < W and 0 <= y < H:
            grid[y][x] = WHITE
    return grid


def r_image(t):
    """`Image` 效果的占位演示：一段像素字 + 横向滚动（实际显示的是用户上传的图）。"""
    grid = empty()
    width = text_width("LUMI")
    x0 = int(-width + t * (width + W))
    draw_text(grid, "LUMI", x0, (H - TEXT_H) // 2, (120, 220, 255))
    return grid


def r_candle(t, seed=2.0):
    def color_at(i, n):
        v = 0.55 + 0.45 * noise1(i * 0.08 + t * 14, seed)
        return gradient(FIRE_STOPS, v * 0.85)

    return from_strip(color_at)


def r_pacman(t):
    grid = empty()
    pos = int(t * W * 2) % W
    for x in range(W):
        if x < pos:
            grid[H // 2][x] = dim(WARM, 0.25)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            x, y = pos + dx, H // 2 + dy
            if 0 <= x < W and 0 <= y < H and (dx, dy) != (0, 0):
                grid[y][x] = (255, 220, 60)
    return grid


def r_tartan(t, cycles=1.0):
    grid = empty()
    for y in range(H):
        for x in range(W):
            a = 0.5 + 0.5 * math.sin(x * 0.5 + t * 2 * math.pi * cycles)
            b = 0.5 + 0.5 * math.sin(y * 0.7 - t * 2 * math.pi * cycles)
            grid[y][x] = add(dim(RED, a * 0.7), dim(BLUE, b * 0.7))
    return grid


def r_zoom(t, color=VIOLET):
    grid = empty()
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - W / 2, y - H / 2)
            v = 0.5 + 0.5 * math.sin(d * 0.6 - t * 2 * math.pi * 2)
            grid[y][x] = dim(color, v ** 1.6)
    return grid


# ------------------------------------------------------------------ 5x7 点阵字体
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
}
GLYPH_H, GLYPH_W, ADVANCE = 7, 5, 6


def load_wled_font(path: str):
    """解析固件的 wbf 点阵字体（12 字节头 + 可选宽度表 + 行优先 MSB 位图）。

    返回 (height, spacing, {code: [row_bits]})；解析失败时返回 None，调用方退回内置 5×7。
    """
    try:
        raw = open(path, encoding="utf-8").read()
    except OSError:
        return None
    text = raw[raw.index("static const unsigned char") :]
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)  # 行内注释里有示例 hex，必须先去掉
    data = [int(h, 16) for h in re.findall(r"0x([0-9A-Fa-f]{2})", text)]
    if len(data) < 13 or data[0] != 0x57:
        return None
    height, max_width, spacing, flags, first, last = data[1], data[2], data[3], data[4], data[5], data[6]
    count = last - first + 1
    pos = 12
    if flags & 0x01:
        widths = {first + i: data[pos + i] for i in range(count)}
        pos += count
    else:
        widths = {first + i: max_width for i in range(count)}
    glyphs = {}
    for code in range(first, last + 1):
        width = widths[code]
        glyph = []
        bit = 0
        for _ in range(height):
            row = []
            for _ in range(width):
                byte = data[pos + (bit >> 3)] if pos + (bit >> 3) < len(data) else 0
                row.append((byte >> (7 - (bit & 7))) & 1)
                bit += 1
            glyph.append(row)
        pos += (width * height + 7) // 8
        glyphs[code] = glyph
    return height, spacing, glyphs


#: 固件字体：字幕预览与设备显示必须同源，否则"预览好看、上机不认"。
_WLED_FONT = load_wled_font(WLED_FONT_FILE)
if _WLED_FONT:
    TEXT_H, TEXT_SPACING, TEXT_GLYPHS = _WLED_FONT
else:  # 兜底：文件缺失时用内置 5×7
    TEXT_H, TEXT_SPACING = GLYPH_H, 1
    TEXT_GLYPHS = {ord(ch): [[int(b) for b in row] for row in glyph] for ch, glyph in FONT5x7.items()}


def glyph_rows(ch: str):
    return TEXT_GLYPHS.get(ord(ch)) or TEXT_GLYPHS.get(ord(' ')) or [[0]]


def text_width(s: str, bold: bool = True) -> int:
    """整串文字的像素宽度。`bold` 与固件一致：每个字形多占 1 列。"""
    extra = 1 if bold else 0
    return sum(len(glyph_rows(ch)[0]) + TEXT_SPACING + extra for ch in s.upper()) - TEXT_SPACING


def draw_text(grid, s: str, x0: int, y0: int, color, bold: bool = True):
    """按固件字体的字距把字符串画到栅格上。

    `bold` 复刻 `FontManager::drawCharacter` 的加粗方式：每个点亮像素再往右补一颗，
    1 像素笔画因此变成 2 像素宽 —— 乳白扩散膜会晕开细笔画，不加粗在设备上读不清。
    """
    extra = 1 if bold else 0
    cursor = x0
    for ch in s.upper():
        glyph = glyph_rows(ch)
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if not bit:
                    continue
                for dx in range(1 + extra):
                    x, y = cursor + gx + dx, y0 + gy
                    if 0 <= x < W and 0 <= y < H:
                        grid[y][x] = color
        cursor += len(glyph[0]) + TEXT_SPACING + extra


# ------------------------------------------------------------------ 效果表
def build_effects() -> dict:
    """name -> 渲染函数。键是固件效果名（effects_enabled.json），灯效重编号后无需再改本表。"""
    fx = {
        "Solid": lambda t: r_solid(t, WARM),
        "Blink": lambda t: r_blink(t, WARM, 1.2),
        "Breathe": lambda t: r_breathe(t, (255, 205, 140), 1.2),
        "Colorloop": lambda t: r_hue_cycle(t, 1.2),
        "Rainbow": lambda t: r_rainbow(t, 1.0, 1.5),
        "Fade": lambda t: r_breathe(t, hsv(t), 0.8),
        "Theater": lambda t: r_chase(t, WARM, 2, 6, 2),
        "Running": lambda t: r_dots(t, [WARM], 1, 3, 6),
        "Dissolve": lambda t: r_noise_flow(t, RAINBOW_STOPS, 0.1, 1.5, 6),
        "Strobe Mega": lambda t: r_strobe(t, WHITE, 9),
        "Chase": lambda t: r_chase(t, ORANGE, 4, 3, 2),
        "Chase Flash Rnd": lambda t: r_twinkle(t, [WARM, CYAN], 0.3, 8),
        "Colorful": lambda t: r_twinkle(t, [RED, GREEN, BLUE, WARM, PINK], 0.4, 9),
        "Fireworks": lambda t: r_fireworks(t, 11),
        "Fire Flicker": lambda t: r_candle(t, 3),
        "Gradient": lambda t: r_gradient_flow(t, [0.0, RED, 0.5, PINK, 1.0, BLUE], 1),
        "Rolling Balls": lambda t: r_bouncing(t, [RED, GREEN, BLUE, WARM], 4, 31),
        "Fairy": lambda t: r_twinkle(t, [WARM, PINK, CYAN], 0.25, 14),
        "Image": lambda t: r_image(t),
        "Chase 3": lambda t: r_chase(t, CYAN, 3, 3, 2),
        "Tri Fade": lambda t: r_wipe_tri(t, 0),
        "Juggle": lambda t: r_dots(t, [WARM, CYAN, PINK], 3, 18, 4),
        "Palette": lambda t: r_gradient_flow(t, RAINBOW_STOPS, 0.6),
        "Colorwaves": lambda t: r_noise_flow(t, RAINBOW_STOPS, 0.14, 1.6, 13),
        "Fill Noise": lambda t: r_breathe(t, PINK, 2.0),
        "Noise 4": lambda t: r_noise_flow(t, AURORA_STOPS, 0.25, 0.8, 20),
        "Colortwinkles": lambda t: r_noise_flow(t, OCEAN_STOPS, 0.3, 0.9, 21),
        "Lake": lambda t: r_twinkle(t, [RED, GREEN, BLUE, WARM, PINK, CYAN], 0.3, 22),
        "Meteor": lambda t: r_noise_flow(t, OCEAN_STOPS, 0.05, 0.5, 23),
        "Copy Segment": lambda t: r_comet(t, WARM, 16, 1.5, 1),
        "Railway": lambda t: r_solid(t, WARM),
        "Ripple": lambda t: r_chase(t, WARM, 2, 8, 4),
        "Twinklefox": lambda t: r_ripple_strip(t),
        "Glitter": lambda t: r_dots(t, [WARM, CYAN, PINK], 4, 25, 4),
        "Fireworks Starburst": lambda t: r_candle(t, 34),
        "Bouncing Balls": lambda t: r_fireworks(t, 27),
        "Popcorn": lambda t: r_comet(t, WARM, 12, 1.6, 1),
        "Drip": lambda t: r_sparkle(t, (255, 230, 160), 0.12, 36),
        "Plasma": lambda t: r_rain(t, CYAN, 8, 37),
        "Sunrise": lambda t: r_sparkle(t, WARM, 0.07, 30),
        "Phased": lambda t: r_gradient_flow(t, [0.0, (40, 0, 10), 0.5, (200, 60, 20), 0.85, (255, 190, 120), 1.0, (255, 250, 230)], -0.5),
        "Sine": lambda t: r_noise_flow(t, RAINBOW_STOPS, 0.18, 1.2, 42),
        "Flow": lambda t: r_sine_wave(t),
        "Chunchun": lambda t: r_gradient_flow(t, AURORA_STOPS, 1.4),
        "Dancing Shadows": lambda t: r_bouncing(t, [PINK, WARM], 2, 43),
        "Washing Machine": lambda t: r_noise_flow(t, [0.0, (10, 6, 20), 0.6, (90, 40, 160), 1.0, (240, 200, 255)], 0.08, 1.0, 33),
        "Blends": lambda t: r_zoom(t, VIOLET),
        "Crazy Bees": lambda t: r_dots2d(t, [CYAN, VIOLET], 4, 60),
        "Ghost Rider": lambda t: r_dots2d(t, [WARM, PINK], 6, 61),
        "Blobs": lambda t: r_waves(t, [0.0, (8, 4, 16), 0.6, (140, 30, 60), 1.0, (255, 170, 120)], 1.1),
        "Scrolling Text": lambda t: r_blobs(t, 0.8, 62),
        "Distortion Waves": lambda t: r_spiral(t, 0.8),
        "Soap": lambda t: r_waves(t, AURORA_STOPS, 2.0),
        "Octopus": lambda t: r_blobs(t, 0.6, 74),
        "Waving Cell": lambda t: r_spiral(t, 0.7),
        "Pixels": lambda t: r_waves(t, AURORA_STOPS, 1.1),
        "Pixelwave": lambda t: r_pixels(t),
        "Juggles": lambda t: r_sine_wave(t),
        "Matripix": lambda t: r_dots(t, [WARM, CYAN, PINK], 4, 44, 5),
        "Gravimeter": lambda t: r_wipe(t, VIOLET, 2),
        "Plasmoid": lambda t: r_percent_bar(t, VIOLET),
        "Puddles": lambda t: r_noise_flow(t, [0.0, (10, 4, 30), 0.5, (120, 40, 200), 1.0, (200, 240, 255)], 0.1, 1.4, 45),
        "Midnoise": lambda t: r_noise_flow(t, OCEAN_STOPS, 0.07, 0.9, 46),
        "Waterfall": lambda t: r_noise_flow(t, AURORA_STOPS, 0.2, 1.4, 47),
        "Freqpixels": lambda t: r_freq_pixels(t),
        "Noisefire": lambda t: r_bars_2d(t, 49),
        "Noisemove": lambda t: r_bars_2d(t, 63),
        "Noise2D": lambda t: r_rain(t, (120, 200, 255), 16, 50),
        "PacMan": lambda t: r_noise2d(t, AURORA_STOPS, 64),
        "DNA": lambda t: r_noise2d(t, AURORA_STOPS, 54),
        "Matrix": lambda t: r_ripple(t, CYAN, 1.5),
        "Metaballs": lambda t: r_noise2d(t, FIRE_STOPS, 65),
        "Gravcenter": lambda t: r_spiral(t, 1.6),
        "Funky Plank": lambda t: r_pacman(t),
        "Pulser": lambda t: r_matrix(t),
        "Blurz": lambda t: r_blobs(t, 1.3, 66),
        "Drift": lambda t: r_bars_2d(t, 55),
        "Waverly": lambda t: r_blobs(t, 1.0, 56),
        "Sun Radiation": lambda t: r_bars_2d(t, 57),
        "Colored Bursts": lambda t: r_bars_2d(t, 67),
        "Julia": lambda t: r_sparkle2d(t, WHITE, 0.05, 59),
        "Game Of Life": lambda t: r_radial(t, PINK, 1.4),
        "Tartan": lambda t: r_noise2d(t, [0.0, (20, 10, 40), 1.0, (200, 160, 255)], 58),
        "Polar Lights": lambda t: r_noise2d(t, OCEAN_STOPS, 68),
        "Swirl": lambda t: r_waves(t, OCEAN_STOPS, 1.8),
        "Lissajous": lambda t: r_radial(t, WARM, 1.0),
        "Frizzles": lambda t: r_fireworks(t, 69),
        "Flow Stripe": lambda t: r_life(t, 70),
        "Hiphotic": lambda t: r_tartan(t, 0.6),
        "Sindots": lambda t: r_noise2d(t, AURORA_STOPS, 71),
        "DNA Spiral": lambda t: r_spiral(t, 1.2),
        "Black Hole": lambda t: r_lissajous(t, VIOLET, 3),
        "Wavesins": lambda t: r_noise2d(t, RAINBOW_STOPS, 72),
        "Rocktaves": lambda t: r_radial(t, CYAN, 1.6),
        "PS Vortex": lambda t: r_spiral(t, 1.0),
        "PS Fuzzy Noise": lambda t: r_zoom(t, (120, 40, 220)),
        "PS Ballpit": lambda t: r_waves(t, AURORA_STOPS, 1.2),
        "PS Box": lambda t: r_waves(t, OCEAN_STOPS, 1.5),
        "PS Attractor": lambda t: r_bars_2d(t, 75),
        "PS Spray": lambda t: r_fireworks(t, 78),
        "PS Ghost Rider": lambda t: r_spiral(t, 1.8),
        "PS Blobs": lambda t: r_noise2d(t, [0.0, (14, 10, 24), 1.0, (200, 200, 230)], 79),
        "PS Dancing Shadows": lambda t: r_lissajous(t, CYAN, 4),
        "PS Chase": lambda t: r_waves(t, [0.0, (8, 4, 16), 0.6, (140, 30, 60), 1.0, (255, 170, 120)], 1.4),
        "PS GEQ 1D": lambda t: r_rain2d(t),
        "PS Springy": lambda t: r_noise2d(t, [0.0, (6, 4, 12), 1.0, (120, 60, 160)], 87),
        "PS Galaxy": lambda t: r_fireworks(t, 88),
        "Color Clouds": lambda t: r_sparkle2d(t, (255, 230, 160), 0.07, 89),
        "极光之舞": lambda t: r_bars_2d(t, 92),
        "Lumi Forest": lambda t: r_chase_2d(t, WARM),
        "Lumi Caustics": lambda t: r_radial(t, PINK, 2.4),
        "Stage Light": lambda t: r_noise2d(t, VIOLET_STOPS, 85),
        "Tri-band Breath": lambda t: r_noise_flow(t, AURORA_STOPS, 0.06, 1.0, 11),
        "Audio Spiral": lambda t: r_gradient_flow(t, RAINBOW_STOPS, 0.25),
        "Sonic Pulse": lambda t: r_clock(t),
        "Bass Lava": lambda t: r_lumi_aurora(t),
        "Spectrum Lamp": lambda t: r_lumi_forest(t),
        "Audio Solution": lambda t: r_solution(t),
    }
    return fx


VIOLET_STOPS = [0.0, (8, 6, 24), 0.45, (70, 40, 170), 0.8, (150, 120, 255), 1.0, (240, 235, 255)]


def r_chase_rainbow(t):
    def color_at(i, n):
        p = (i + int(t * 96)) % 64
        if p < 4:
            return hsv(i / n, 1.0, 1.0)
        return hsv(i / n, 1.0, 0.05)
    return from_strip(color_at)


def r_tetrix(t):
    grid = empty()
    rnd = random.Random(7)
    blocks = [(rnd.randrange(W), rnd.uniform(0.4, 1.2), TABLE_COLORS[rnd.randrange(len(TABLE_COLORS))])
              for _ in range(8)]
    for x, speed, color in blocks:
        for dy in range(3):
            y = int((t * speed * H * 2 + dy) % H)
            if 0 <= y < H:
                grid[H - 1 - y][x] = color
    return grid


TABLE_COLORS = [RED, (255, 200, 40), GREEN, CYAN, VIOLET, PINK]


def r_wipe_tri(t, mode=1):
    colors = [RED, GREEN, BLUE]

    def color_at(i, n):
        phase = (i / n + t) % 1.0
        slot = int(phase * 3) % 3
        if mode == 2:
            return colors[slot]
        if mode == 1:
            return colors[slot] if (i * 3 / n) % 1 < 0.66 else (0, 0, 0)
        return dim(colors[slot], 0.6)

    return from_strip(color_at)


def r_lightning(t):
    rnd = random.Random(13)
    on = (t * 5) % 1.0 < 0.18
    if not on:
        return from_strip(lambda i, n: (0, 0, 0))
    grid = empty()
    y = 0
    x = rnd.randrange(W)
    while y < H:
        grid[y][min(W - 1, max(0, x))] = WHITE
        x += rnd.choice((-1, 0, 1))
        y += 1
    return grid


def r_solid_oscillate(t):
    k = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    half = int(W * 0.5 + k * W * 0.5)
    return from_strip(lambda i, n: WARM if (i % W) < half else (0, 0, 0))


def r_ripple_strip(t):
    def color_at(i, n):
        row = i // W
        v = 0.5 + 0.5 * math.sin(row * 1.2 - t * 2 * math.pi * 1.5)
        return dim(CYAN, v ** 2)
    return from_strip(color_at)


def r_heartbeat(t):
    phase = (t * 2) % 1.0
    k = max(0.0, math.sin(phase * math.pi * 4) ** 6)
    return from_strip(lambda i, n: dim(RED, 0.05 + 0.95 * k))


def r_phased(t):
    def color_at(i, n):
        v = 0.5 + 0.5 * math.sin(i * 0.3 + t * 2 * math.pi * 2)
        return dim(hsv(i / n), v ** 3)
    return from_strip(color_at)


def r_sine_wave(t):
    def color_at(i, n):
        row = i // W
        v = 0.5 + 0.5 * math.sin((row / H * 2 - t) * 2 * math.pi)
        return dim(CYAN, v ** 2)
    return from_strip(color_at)


def r_spiral_strip(t):
    def color_at(i, n):
        row = i // W
        col = i % W
        v = 0.5 + 0.5 * math.sin(col * 0.4 + row * 0.6 - t * 2 * math.pi * 2)
        return hsv(col / W + t * 0.4, 0.85, v)
    return from_strip(color_at)


def r_tv_noise(t):
    rnd = random.Random(int(t * FRAMES) + 99)
    return from_strip(lambda i, n: dim(WHITE, rnd.random() * 0.85))


def r_pixels(t):
    rnd = random.Random(int(t * 6))
    palette = [RED, GREEN, BLUE, WARM, CYAN, PINK]
    return from_strip(lambda i, n: palette[rnd.randrange(len(palette))] if rnd.random() < 0.35 else (0, 0, 0))


def r_sparkle2d(t, color, density=0.06, seed=1.0):
    rnd = random.Random(int(seed * 977) + 3)
    points = [(rnd.randrange(W), rnd.randrange(H), rnd.random()) for _ in range(int(W * H * density))]
    grid = empty()
    for x, y, phase in points:
        v = max(0.0, math.sin((t + phase) * 2 * math.pi * 3))
        grid[y][x] = dim(color, v ** 3)
    return grid


def r_dots2d(t, colors, count=5, seed=1.0):
    rnd = random.Random(int(seed))
    dots = [(rnd.uniform(0, W), rnd.uniform(0, H), rnd.uniform(0.4, 1.4), rnd.random(),
             rnd.choice(colors)) for _ in range(count)]
    grid = empty()
    for bx, by, speed, ph, color in dots:
        x = int((bx + t * speed * W) % W)
        y = int((by + 2 * math.sin(t * 2 * math.pi * speed + ph * 6)) % H)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if 0 <= x + dx < W and 0 <= y + dy < H:
                    k = 1.0 if (dx, dy) == (0, 0) else 0.35
                    grid[y + dy][x + dx] = dim(color, k)
    return grid


def r_chase_2d(t, color=WARM):
    grid = empty()
    period = max(4, W // 4)
    for y in range(H):
        for x in range(W):
            phase = (x + y * 2 + int(t * 32)) % period
            if phase < 2:
                grid[y][x] = dim(color, 1.0 - phase * 0.35)
    return grid


def r_rain2d(t, color=(120, 200, 255)):
    rnd = random.Random(31)
    columns = [(rnd.uniform(0.6, 1.6), rnd.random()) for _ in range(W)]
    grid = empty()
    for x, (speed, phase) in enumerate(columns):
        head = int((t * speed * H * 1.6 + phase * H * 3) % (H + 6)) - 3
        for i in range(5):
            y = head - i
            if 0 <= y < H:
                grid[y][x] = dim(color, (1.0 - i / 5) ** 1.5)
    return grid


def r_solution(t):
    """音律溶液：底部琥珀→暗红→深靛的溶液随音量向上推进，节拍带来瞬时冲锋，
    顶部保持用户常亮基色（预览用冰青），交界处叠加圆周涡流。"""
    top = (0, 245, 255)
    invader_stops = [0.0, (255, 165, 35), 0.48, (165, 14, 28), 1.0, (26, 8, 58)]
    vol = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    line = 0.18 + 0.54 * (vol ** 0.85)
    line += 0.10 * math.exp(-((t * 2) % 1.0) * 6.0)   # 节拍冲锋后弹性回落
    swirl = t * 2 * math.pi * 1.4
    band = 0.14
    grid = empty()
    for y in range(H):
        ny = (H - 1 - y) / (H - 1)                    # 0 = 画面底部
        for x in range(W):
            theta = x / W * 2 * math.pi
            eddy = (math.sin(theta * 2 + swirl + ny * 4.5) * 0.6
                    + math.cos(theta * 3 - swirl * 1.3 + ny * 7.0) * 0.4)
            local = max(0.05, min(0.96, line + eddy * (0.045 + 0.03 * vol)))
            dist = ny - local
            if dist > band * 0.5:
                grid[y][x] = top
                continue
            depth = max(0.0, min(1.0, ny / max(local, 0.05)))
            invader = gradient(invader_stops, depth)
            smoke = 0.85 + 0.15 * math.sin(theta * 4 - swirl * 1.6 + ny * 10)
            invader = dim(invader, smoke)
            if dist < -band * 0.5:
                grid[y][x] = invader
            else:
                m = (dist + band * 0.5) / band
                m = m * m * (3 - 2 * m)
                grid[y][x] = mix(invader, top, m)
    return grid


# ------------------------------------------------------------------ Lumi 新增 10 个（fx 189..198）
# 与固件同名函数对应：纵向用 pos（0=画面底部），周向用 x/W 的环绕距离。
FOREST_STOPS = [0.0, (30, 91, 46), 0.4, (63, 163, 77), 0.75, (139, 212, 80), 1.0, (200, 232, 90)]
DUSK_STOPS = [0.0, (226, 58, 30), 0.22, (255, 122, 46), 0.42, (255, 194, 74),
              0.62, (232, 92, 196), 0.8, (123, 91, 224), 1.0, (79, 216, 232)]
POLAR_STOPS = [0.0, (232, 85, 184), 0.21, (155, 91, 232), 0.45, (91, 124, 240),
               0.7, (63, 208, 240), 1.0, (47, 227, 138)]
LAVA_STOPS = [0.0, (26, 0, 4), 0.35, (179, 0, 27), 0.7, (255, 107, 26), 1.0, (224, 69, 123)]
CAUSTIC_STOPS = [0.0, (1, 42, 51), 0.45, (14, 107, 122), 0.8, (127, 242, 255), 1.0, (223, 255, 255)]


def _wrap_dist(a: float, b: float) -> float:
    d = abs(a - b) % 1.0
    return min(d, 1.0 - d)


def r_lumi_forest(t):
    grid = empty()
    for y in range(H):
        pos = (H - 1 - y) / (H - 1)
        for x in range(W):
            wob = math.sin((x / W + t * 0.25) * 2 * math.pi) * 0.06
            grid[y][x] = gradient(FOREST_STOPS, max(0.0, min(1.0, pos + wob)))
    rnd = random.Random(11)
    for i in range(7):
        px = (rnd.random() + t * 0.05 * (1 if i % 2 else -1)) % 1.0
        py = 0.2 + 0.7 * (0.5 + 0.5 * math.sin(rnd.random() * 6.28 + t * 3))
        br = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(t * 5 + i))
        x = int(px * W) % W
        y = int((1.0 - py) * (H - 1))
        grid[y][x] = add(grid[y][x], (255, 233, 168), br)
    return grid


def r_lumi_dusk(t):
    grid = empty()
    sun = 0.25 + 0.6 * (0.5 + 0.5 * math.sin(t * 0.5))
    for y in range(H):
        pos = (H - 1 - y) / (H - 1)
        cloud = 0.5 + 0.5 * math.sin(y * 1.3 + t * 2.0)
        for x in range(W):
            idx = pos + math.sin((x / W + t * 0.15) * 2 * math.pi) * 0.03 + (cloud - 0.5) * 0.08
            c = gradient(DUSK_STOPS, max(0.0, min(1.0, idx)))
            c = dim(c, 0.78 + 0.22 * cloud)
            d = math.hypot(_wrap_dist(x / W, 0.5) * 1.6, pos - sun)
            if d < 0.22:
                c = add(c, (255, 231, 192), (1 - d / 0.22) ** 2)
            grid[y][x] = c
    return grid


def r_lumi_polar(t):
    grid = empty()
    for y in range(H):
        pos = (H - 1 - y) / (H - 1)
        ca = 0.5 + 0.5 * math.sin(pos * 4.7 + t * 2.0)
        cb = 0.5 + 0.5 * math.sin(pos * 3.1 - t * 2.6 + 1.3)
        for x in range(W):
            ux = x / W
            c = gradient(POLAR_STOPS, pos)
            ga = max(0.0, 1.0 - _wrap_dist(ux, ca) / 0.16)
            gb = max(0.0, 1.0 - _wrap_dist(ux, cb) / 0.12)
            g = max(ga, gb)
            if g > 0:
                c = add(c, (159, 255, 224), g * 0.55)
            grid[y][x] = c
    rnd = random.Random(5)
    for i in range(5):
        x = rnd.randrange(W)
        y = int((1 - (0.2 + 0.65 * rnd.random())) * (H - 1))
        tw = 0.4 + 0.6 * (0.5 + 0.5 * math.sin(t * 4 + i * 2))
        grid[y][x] = add(grid[y][x], (232, 246, 255), tw)
    return grid


def r_lumi_hearth(t):
    grid = empty()
    rnd = random.Random(17)
    for x in range(W):
        base = 0.55 + 0.28 * math.sin(x * 0.7 + rnd.random() * 6.28)
        flick = 0.10 * math.sin(t * 9 + x * 1.9) + 0.06 * math.sin(t * 14.7 + x * 0.6)
        h = max(0.08, min(1.0, base + flick))
        for y in range(H):
            pos = (H - 1 - y) / (H - 1)
            if pos > h:
                continue
            lvl = 1.0 - pos / h
            c = gradient(FIRE_STOPS, lvl)
            edge = 1.0 - 0.35 * min(1.0, _wrap_dist(x / W, 0.5) * 2)
            grid[y][x] = dim(c, (0.45 + 0.55 * lvl) * edge)
    return grid


def r_lumi_lava(t):
    grid = empty()
    blobs = []
    for i in range(5):
        cx = 0.5 + 0.34 * math.sin(t * 0.35 + i * 2.1)
        cy = 0.5 + 0.26 * math.sin(t * 0.27 + i * 1.7 + 0.9)
        blobs.append((cx, cy, 0.17 + 0.05 * math.sin(t * 0.5 + i)))
    for y in range(H):
        pos = (H - 1 - y) / (H - 1)
        for x in range(W):
            ux = x / W
            field = 0.0
            for cx, cy, r in blobs:
                d = math.hypot(_wrap_dist(ux, cx) * 1.3, pos - cy) / r
                if d < 1.0:
                    field += (1.0 - d) ** 2
            field = min(1.0, field)
            c = gradient(LAVA_STOPS, 0.15 + 0.85 * field) if field > 0.02 else (5, 1, 3)
            grid[y][x] = dim(c, 0.45 + 0.55 * field)
    for i in range(6):
        bx = (i * 0.37 + t * 0.05) % 1.0
        by = (0.15 + i * 0.13 + t * 0.12) % 1.0
        x = int(bx * W) % W
        y = int((1 - by) * (H - 1))
        grid[y][x] = add(grid[y][x], (255, 176, 112), 0.7)
    return grid


def r_lumi_caustics(t):
    grid = empty()
    for y in range(H):
        pos = (H - 1 - y) / (H - 1)
        for x in range(W):
            ux = x / W
            w = (math.sin((ux * 3 + t * 0.55) * 2 * math.pi)
                 + math.sin((pos * 2.5 - t * 0.9) * 2 * math.pi)
                 + math.sin(((ux + pos) * 2 + t * 1.3) * 2 * math.pi))
            ridge = max(0.0, 1.0 - abs(w) / 2.4)
            ridge = ridge ** 3
            grid[y][x] = dim(gradient(CAUSTIC_STOPS, ridge), 0.55 + 0.45 * ridge)
    return grid


def r_lumi_fountain(t):
    grid = empty()
    bass = 0.45 + 0.55 * (0.5 + 0.5 * math.sin(t * 6.0))
    band = bass * 0.5
    for y in range(H):
        pos = (H - 1 - y) / (H - 1)
        if pos <= band:
            f = pos / max(1e-6, band)
            c = gradient([0.0, (255, 61, 0), 0.55, (255, 122, 0), 1.0, (255, 208, 96)], f)
            for x in range(W):
                grid[y][x] = dim(c, 0.75 + 0.25 * bass)
    rnd = random.Random(23)
    for i in range(10):
        phase = (t * (0.8 + 0.4 * rnd.random()) + rnd.random()) % 1.0
        y = int((1 - (0.35 + 0.6 * phase)) * (H - 1))
        x = int(((rnd.random() + (phase - 0.5) * 0.5 * (1 if i % 2 else -1)) % 1.0) * W) % W
        if 0 <= y < H:
            grid[y][x] = add(grid[y][x], (207, 246, 255), 0.35 + 0.65 * (1 - phase))
    ry = int((1 - (t * 0.6) % 1.0 * 0.95) * (H - 1))
    if 0 <= ry < H:
        for x in range(W):
            grid[ry][x] = add(grid[ry][x], (255, 217, 160), 0.55)
    return grid


def r_lumi_pulser(t):
    grid = empty()
    for y in range(H):
        for x in range(W):
            grid[y][x] = dim((10, 42, 51), 0.09 + 0.05 * math.sin(t * 1.5))
    for k in range(3):
        phase = (t * 0.75 + k / 3.0) % 1.0
        ring = 1.0 - phase
        trail = 0.32
        for y in range(H):
            pos = (H - 1 - y) / (H - 1)
            dd = ring - pos
            if dd < -0.02 or dd > trail:
                continue
            fade = 1.0 if dd <= 0.03 else 1.0 - dd / trail
            c = (255, 246, 224) if dd <= 0.03 else (255, 176, 112)
            for x in range(W):
                grid[y][x] = add(grid[y][x], c, fade * 0.9)
    return grid


def r_lumi_scanner(t):
    grid = empty()
    charge = [0.0] * H
    for k in range(2):
        tri = abs(((t * 0.45 + k * 0.5) % 1.0) * 2 - 1.0)
        pos = 1.0 - tri
        for i in range(H):
            row_pos = 1.0 - i / (H - 1)
            charge[i] = max(charge[i], max(0.0, 1.0 - abs(row_pos - pos) * 3.5))
    for y in range(H):
        c = charge[y]
        if c <= 0.01:
            continue
        for x in range(W):
            grid[y][x] = add(grid[y][x], (127, 233, 255), c * 0.85)
    return grid


def r_lumi_rain(t):
    grid = empty()
    rnd = random.Random(29)
    for x in range(W):
        speed = 0.9 + 0.7 * rnd.random()
        phase = rnd.random()
        head = (t * speed + phase) % 1.0
        y = int(head * (H - 1))
        for i in range(6):
            yy = y - i
            if 0 <= yy < H:
                c = (220, 239, 255) if i == 0 else (127, 216, 255)
                grid[yy][x] = add(grid[yy][x], c, (1.0 - i / 6) * 0.9)
        if y >= H - 1:
            for dx in (-1, 0, 1):
                xx = x + dx
                if 0 <= xx < W:
                    grid[H - 1][xx] = add(grid[H - 1][xx], (159, 232, 255), 0.5)
    return grid


# ------------------------------------------------------------------ 出图
def to_image(grid: Grid) -> Image.Image:
    img = Image.new("RGB", (CANVAS_W, CANVAS_H), BG)
    d = ImageDraw.Draw(img)
    for y in range(H):
        for x in range(W):
            rgb = grid[y][x]
            if rgb != (0, 0, 0):
                x0, y0 = x * CELL + GAP // 2, y * CELL + GAP // 2
                d.rounded_rectangle([x0, y0, x0 + CELL - GAP, y0 + CELL - GAP], radius=1, fill=rgb)
    # 扩散罩的柔光：只叠一层，保持灯珠颗粒感的同时让画面不刺眼
    glow = img.filter(ImageFilter.GaussianBlur(2.6))
    glow = glow.point(lambda v: int(v * 0.42))
    return ImageChops.add(img, glow)


def build(idx: int, fn, out_dir: str) -> str:
    frames = [to_image(fn(f / FRAMES)) for f in range(FRAMES)]
    if SAVE_COLORS:
        montage = Image.new("RGB", (CANVAS_W, CANVAS_H * len(frames)), BG)
        for i, f in enumerate(frames):
            montage.paste(f, (0, i * CANVAS_H))
        base = montage.quantize(colors=SAVE_COLORS)
        frames = [f.quantize(palette=base, dither=Image.NONE) for f in frames]
    path = os.path.join(out_dir, f"{idx}.gif")
    frames[0].save(path, save_all=True, append_images=frames[1:],
                   duration=int(1000 / FPS), loop=0, optimize=True)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", type=int, help="只生成指定 id")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--out", default=OUT_DIR)
    args = ap.parse_args()

    entries = [
        e
        for e in json.loads(open(EFFECTS_FILE, encoding="utf-8").read())
        if e and e.get("name")
    ]
    effects = build_effects()
    if args.list:
        for e in entries:
            mark = "" if e["name"] in effects else "  (缺少渲染器)"
            print(f'{e["idx"]:4d}  {e["name"]:24s}{mark}')
        return

    os.makedirs(args.out, exist_ok=True)
    selected = [e for e in entries if not args.only or e["idx"] in args.only]
    total = 0
    for e in selected:
        idx, name = e["idx"], e["name"]
        fn = effects.get(name)
        if fn is None:
            print(f"!! 缺少效果实现: {idx} {name}")
            continue
        path = build(idx, fn, args.out)
        size = os.path.getsize(path)
        total += size
        print(f"{idx:4d}.gif {size / 1024:7.1f} KB")
    print(f"合计 {total / 1024 / 1024:.2f} MB（{len(selected)} 个）")


if __name__ == "__main__":
    main()
