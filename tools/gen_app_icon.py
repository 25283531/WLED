"""生成 APP 图标 PNG（mipmap-* 各密度）与自适应图标矢量层。

构图对应用户提供的「光屿 LumiIsle」图标原图：
    深空蓝灰底上，一条自下而上螺旋上升的双股光带，
    颜色由底部亮青 (#00F5FF) 过渡到中部蔚蓝 (#1E78FF) 再到顶部紫罗兰 (#AF28FF)，
    脚下是三圈同心青色椭圆涟漪。

与固件的招牌灯效「极光之舞」(mode_lumi_aurora) 同源同色 —— 图标与灯效互相呼应。

    - 自适应图标（API 26+）走 res/drawable/ic_launcher_{background,foreground}.xml 矢量
    - API 24–25 没有自适应图标，用 mipmap-*/ic_launcher_rgb*.png 位图兜底
    - 前景层主体必须落在中央 72/108 安全区内，涟漪可以压到下缘

用法:
    python tools/gen_app_icon.py            # 写入 res/mipmap-*/
    python tools/gen_app_icon.py --preview  # 额外输出预览到 build/icon-preview.png
"""

from __future__ import annotations

import argparse
import math
import os

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "app", "src", "main", "res")

CANVAS = 108.0

# ------------------------------------------------------------------ 构图参数
RIBBON_CX = 54.0
RIBBON_TOP = 13.0          # 光带顶端（画布坐标）
RIBBON_BOTTOM = 70.0       # 光带底端
RIBBON_AMP = 14.5          # 螺旋横向摆幅（半宽）
RIBBON_TURNS = 1.15        # 上升过程中绕轴的圈数
RIBBON_THICK = 1.9         # 丝带半厚
RIBBON_SOFT = 1.5          # 边缘羽化半径（画布单位）

# 底部涟漪：三圈同心椭圆
RIPPLE_CY = 75.0
RIPPLE_RX = [31.0, 21.5, 12.5]
RIPPLE_RY = [8.6, 6.0, 3.5]
RIPPLE_W = 1.0

# 配色（与固件 mode_lumi_aurora 一致）
CYAN = (0, 245, 255)
BLUE = (30, 120, 255)
VIOLET = (175, 40, 255)

BG_STOPS = [
    (0.00, (30, 38, 52)),
    (0.55, (24, 31, 44)),
    (1.00, (18, 24, 36)),
]

RING_COLOR = (0, 226, 245)


def _diag_field(stops, size: int) -> np.ndarray:
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / max(1, size - 1)
    t = (xx + yy) * 0.5
    out = np.zeros((size, size, 3), dtype=np.float32)
    for j in range(3):
        out[..., j] = np.interp(t, [s[0] for s in stops], [s[1][j] for s in stops])
    return out


def _rot_ellipse(cx, cy, a, b, tx, ty, segments: int = 28):
    """生成一个长轴沿 (tx,ty) 的椭圆多边形顶点。"""
    px, py = -ty, tx
    pts = []
    for i in range(segments):
        th = 2 * math.pi * i / segments
        u = a * math.cos(th)
        v = b * math.sin(th)
        pts.append((cx + u * tx + v * px, cy + u * ty + v * py))
    return pts


def _ribbon_points(phase: float, k: float, steps: int = 420):
    """螺旋光带上的采样点：(cx, cy, 切线tx, 切线ty, 高度比u)。"""
    for i in range(steps):
        u = i / (steps - 1)
        ang = phase + u * RIBBON_TURNS * 2 * math.pi
        cx = RIBBON_CX * k + math.sin(ang) * RIBBON_AMP * k
        cy = (RIBBON_BOTTOM + (RIBBON_TOP - RIBBON_BOTTOM) * u) * k
        dx = math.cos(ang) * RIBBON_AMP * k * RIBBON_TURNS * 2 * math.pi
        dy = (RIBBON_TOP - RIBBON_BOTTOM) * k
        norm = math.hypot(dx, dy) or 1.0
        yield cx, cy, dx / norm, dy / norm, u


def _ribbon_alpha(size: int, k: float, phase: float) -> np.ndarray:
    """光带的 alpha 场（形状）。"""
    S = size
    shape = Image.new("L", (S, S), 0)
    draw = ImageDraw.Draw(shape)
    half_len = RIBBON_THICK * 1.25 * k
    half_wid = RIBBON_THICK * k
    for cx, cy, tx, ty, _u in _ribbon_points(phase, k):
        draw.polygon(_rot_ellipse(cx, cy, half_len, half_wid, tx, ty), fill=255)
    shape = shape.filter(ImageFilter.GaussianBlur(max(1, int(RIBBON_SOFT * k * 0.5))))
    return np.asarray(shape, dtype=np.float32) / 255.0


def _ribbon_rgb(size: int, k: float) -> np.ndarray:
    """按高度着色的颜色场。"""
    S = size
    ys = np.arange(S, dtype=np.float32)[:, None]
    span = max(1e-6, (RIBBON_BOTTOM - RIBBON_TOP) * k)
    h = np.clip((RIBBON_BOTTOM * k - ys) / span, 0.0, 1.0)
    out = np.zeros((S, S, 3), dtype=np.float32)
    for j in range(3):
        out[..., j] = np.interp(h, [0.0, 0.5, 1.0], [CYAN[j], BLUE[j], VIOLET[j]])
    return out


def _ripple_layer(size: int, k: float) -> np.ndarray:
    """底部三圈青色涟漪的 alpha 场。"""
    S = size
    img = Image.new("L", (S, S), 0)
    draw = ImageDraw.Draw(img)
    for rx, ry in zip(RIPPLE_RX, RIPPLE_RY):
        draw.ellipse(
            [(RIBBON_CX - rx) * k, (RIPPLE_CY - ry) * k,
             (RIBBON_CX + rx) * k, (RIPPLE_CY + ry) * k],
            outline=255,
            width=max(1, int(RIPPLE_W * k)),
        )
    img = img.filter(ImageFilter.GaussianBlur(max(1, int(0.7 * k))))
    return np.asarray(img, dtype=np.float32) / 255.0


def render(size: int, ss: int = 4, round_mask: bool = False) -> Image.Image:
    """渲染一张 size×size 的全出血方形图标。"""
    S = size * ss
    k = S / CANVAS

    canvas = np.zeros((S, S, 4), dtype=np.float32)
    canvas[..., :3] = _diag_field(BG_STOPS, S)
    canvas[..., 3] = 255.0

    # 底部青色涟漪（在光带之下）
    ra = _ripple_layer(S, k)
    ring = np.broadcast_to(np.array(RING_COLOR, dtype=np.float32), (S, S, 3))
    canvas[..., :3] = canvas[..., :3] * (1 - ra[..., None] * 0.92) + ring * (ra[..., None] * 0.92)

    # 两束反向交织的螺旋光带
    base_rgb = _ribbon_rgb(S, k)
    total_rgb = np.zeros((S, S, 3), dtype=np.float32)
    total_a = np.zeros((S, S), dtype=np.float32)
    for phase, bright in ((0.0, 1.0), (math.pi, 0.62)):
        a = _ribbon_alpha(S, k, phase)
        total_rgb = total_rgb + base_rgb * bright * a[..., None]
        total_a = np.clip(total_a + a, 0.0, 1.0)
    total_rgb = np.clip(total_rgb, 0, 255)

    # 柔光外晕
    glow = Image.fromarray((np.clip(total_a, 0, 1) * 255).astype(np.uint8))
    glow = glow.filter(ImageFilter.GaussianBlur(max(1, int(4.2 * k))))
    ga = np.asarray(glow, dtype=np.float32) / 255.0
    canvas[..., :3] = canvas[..., :3] + total_rgb * 0.09 + ring * ga[..., None] * 0.10

    # 光带本体
    canvas[..., :3] = canvas[..., :3] * (1 - total_a[..., None]) + total_rgb * total_a[..., None]

    img = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8), "RGBA")
    if round_mask:
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, S - 1, S - 1], fill=255)
        img.putalpha(ImageChops.multiply(img.getchannel("A"), mask))
    return img.resize((size, size), Image.LANCZOS)


def render_mono(size: int, ss: int = 4) -> Image.Image:
    """Android 13 主题图标：纯白剪影，只有形状没有颜色。"""
    S = size * ss
    k = S / CANVAS

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for phase in (0.0, math.pi):
        a = _ribbon_alpha(S, k, phase)
        layer = Image.new("RGBA", (S, S), (255, 255, 255, 0))
        layer.putalpha(Image.fromarray((a * 255).astype(np.uint8)))
        img = Image.alpha_composite(img, layer)

    ra = _ripple_layer(S, k)
    layer = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    layer.putalpha(Image.fromarray((ra * 255).astype(np.uint8)))
    img = Image.alpha_composite(img, layer)
    return img.resize((size, size), Image.LANCZOS)


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def write_png(img: Image.Image, folder: str, name: str) -> None:
    path = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()

    for density, size in DENSITIES.items():
        folder = f"mipmap-{density}"
        write_png(render(size), folder, "ic_launcher_rgb.png")
        write_png(render(size, round_mask=True), folder, "ic_launcher_rgb_round.png")
        write_png(render(size), folder, "ic_launcher_rgb_foreground.png")
        write_png(render_mono(size), folder, "ic_launcher_monochrome.png")
        print(f"{folder:18s} {size}×{size}")

    if args.preview:
        sheet = Image.new("RGB", (192 * 4 + 60, 192 + 40), (18, 20, 26))
        x = 10
        for size in (48, 72, 96):
            sheet.paste(render(size).convert("RGB"), (x, 20))
            x += size + 10
        sheet.paste(render_mono(192).convert("RGB"), (x, 20))
        out = os.path.join(ROOT, "build", "icon-preview.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        sheet.save(out)
        print("预览:", out)


if __name__ == "__main__":
    main()
