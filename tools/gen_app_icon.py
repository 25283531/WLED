"""生成 APP 图标 PNG（mipmap-* 各密度）。

构图对应品牌寓意「光影小岛 / LumiIsle」：
    夜色渐变背景上，一座发光的半圆小岛浮在水面，
    岛顶射出一道向上的光柱，水面有几缕微光与星点。

自适应图标（API 26+）走 res/drawable/ic_launcher_{background,foreground}.xml 矢量；
API 24–25 没有自适应图标，需要位图兜底，这里用 Pillow 按同样的构图渲染。

用法:
    python tools/gen_app_icon.py            # 写入 res/mipmap-*/
    python tools/gen_app_icon.py --preview  # 额外输出预览到 build/icon-preview.png
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "app" / "src" / "main" / "res"

CANVAS = 108.0

# 小岛：一枚扁圆丘（圆心 54,78，横半径 25、纵半径 16），截在水平线 y=78 以上。
# 刻意做成"宽而扁"，宽高比接近 3:1 —— 正圆的半圆会被读成气球或蘑菇。
ISLAND_CX, ISLAND_CY = 54.0, 78.0
ISLAND_RX, ISLAND_RY = 25.0, 16.0
WATER_Y = 78.0

# 光柱
BEAM_X0, BEAM_X1 = 50.5, 57.5
BEAM_Y0, BEAM_Y1 = 29.0, 66.0

# 小岛竖向渐变：顶部暖粉 → 紫 → 深靛
ISLAND_STOPS = [
    (0.00, (255, 138, 180)),
    (0.38, (208, 128, 255)),
    (0.72, (142, 122, 250)),
    (1.00, (82, 76, 182)),
]

# 背景对角渐变
BG_STOPS = [
    (0.00, (16, 22, 56)),
    (0.50, (25, 17, 60)),
    (1.00, (44, 14, 58)),
]

# 光柱竖向渐变（带 alpha：顶端透明 → 底部实心）
BEAM_STOPS = [
    (0.00, (255, 255, 255, 0)),
    (0.45, (170, 226, 255, 110)),
    (0.78, (220, 245, 255, 210)),
    (1.00, (255, 255, 255, 235)),
]

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def _lerp_stops(stops, t: float) -> tuple[float, ...]:
    """在停靠点之间线性插值（支持 3 或 4 通道）。"""
    t = min(max(t, 0.0), 1.0)
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(c0[k] + (c1[k] - c0[k]) * f for k in range(len(c0)))
    return stops[-1][1]


def _diag_field(stops, size: int) -> np.ndarray:
    """左上→右下对角渐变，返回 [size,size,3] 的 0–1 浮点数组。"""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    t = ((xx + yy) / (2.0 * (size - 1))).ravel()
    lut = np.array([_lerp_stops(stops, float(v)) for v in t], dtype=np.float32) / 255.0
    return lut.reshape(size, size, 3)


def _vgrad(size: int, stops, y0: float, y1: float, channels: int = 3) -> np.ndarray:
    """竖向渐变（支持带 alpha），把 [y0,y1] 像素区间映射到 [0,1]。"""
    yy = np.arange(size, dtype=np.float32)[:, None]
    t = np.clip((yy - y0) / max(1e-6, (y1 - y0)), 0.0, 1.0).ravel()
    lut = np.array([_lerp_stops(stops, float(v)) for v in t], dtype=np.float32)
    lut = lut[:, :channels] / 255.0
    return np.repeat(lut[:, None, :], size, axis=1)


def _radial(size: int, cx: float, cy: float, r: float, peak: float, mid_at: float, mid: float) -> np.ndarray:
    """径向柔光：中心 peak → r*mid_at 处 mid → 边缘 0。返回 [size,size] 的 0–1。"""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    a = np.zeros_like(d)
    inner = d <= mid_at
    a[inner] = peak + (mid - peak) * (d[inner] / mid_at)
    outer = (d > mid_at) & (d <= 1.0)
    a[outer] = mid * (1.0 - (d[outer] - mid_at) / (1.0 - mid_at))
    return np.clip(a, 0.0, 1.0)


def _round_rect_mask(size: int, x0: float, y0: float, x1: float, y1: float, r: float) -> np.ndarray:
    """抗锯齿圆角矩形遮罩，返回 [size,size] 的 0–1。"""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) + 0.5
    dx = np.abs(xx - (x0 + x1) / 2) - ((x1 - x0) / 2 - r)
    dy = np.abs(yy - (y0 + y1) / 2) - ((y1 - y0) / 2 - r)
    outside = np.sqrt(np.clip(dx, 0, None) ** 2 + np.clip(dy, 0, None) ** 2)
    dist = outside + np.minimum(np.maximum(dx, dy), 0) - r
    return np.clip(0.5 - dist, 0.0, 1.0)


def _ellipse_mask(size: int, cx: float, cy: float, rx: float, ry: float) -> np.ndarray:
    """抗锯齿椭圆遮罩。"""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) + 0.5
    d = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
    return np.clip((1.0 - d) * min(rx, ry) + 0.5, 0.0, 1.0)


def _over(base: np.ndarray, rgb: np.ndarray, alpha: np.ndarray) -> None:
    """把 rgb/alpha 叠到 base（H,W,4，0–1）上，原地修改。"""
    a = alpha[..., None]
    base[..., :3] = base[..., :3] * (1 - a) + rgb * a
    base[..., 3] = np.clip(base[..., 3] + alpha * (1 - base[..., 3]), 0.0, 1.0)


def render(size: int, ss: int = 4) -> Image.Image:
    """渲染一张 size×size 的全出血方形图标。"""
    S = size * ss
    k = S / CANVAS  # 设计稿 → 像素

    canvas = np.ones((S, S, 4), dtype=np.float32)
    canvas[..., :3] = _diag_field(BG_STOPS, S)

    # 左上角冷光，避免背景发闷
    cool = _radial(S, 26 * k, 20 * k, 74 * k, 0.20, 0.35, 0.08)
    _over(canvas, np.broadcast_to(np.array([0.62, 0.76, 1.0], dtype=np.float32), (S, S, 3)), cool)

    # 岛体光晕
    glow = _radial(S, 54 * k, 66 * k, 33 * k, 0.34, 0.42, 0.11)
    _over(canvas, np.broadcast_to(np.array([1.0, 0.86, 0.95], dtype=np.float32), (S, S, 3)), glow)

    # 水面：岛底一道横向微光
    water = Image.new("L", (S, S), 0)
    ImageDraw.Draw(water).ellipse(
        [(54 - 26) * k, (WATER_Y - 2.2) * k, (54 + 26) * k, (WATER_Y + 2.8) * k],
        fill=int(255 * 0.45),
    )
    water = water.filter(ImageFilter.GaussianBlur(max(1, int(1.2 * k))))
    _over(canvas, np.broadcast_to(np.array([0.55, 0.85, 1.0], dtype=np.float32), (S, S, 3)),
          np.asarray(water, dtype=np.float32) / 255.0)

    # 光柱：分两层画 —— 外层柔光晕负责"发光"，内层亮芯负责"光束"。
    # 只画一层会是均匀的灰雾，看着像烟而不像光。
    beam_grad = _vgrad(S, BEAM_STOPS, BEAM_Y0 * k, BEAM_Y1 * k, channels=4)

    def _taper(half_base: float, half_top: float, blur: float) -> np.ndarray:
        layer = Image.new("L", (S, S), 0)
        ImageDraw.Draw(layer).polygon(
            [
                (54 * k - half_base * k, BEAM_Y1 * k),
                (54 * k + half_base * k, BEAM_Y1 * k),
                (54 * k + half_top * k, BEAM_Y0 * k),
                (54 * k - half_top * k, BEAM_Y0 * k),
            ],
            fill=255,
        )
        layer = layer.filter(ImageFilter.GaussianBlur(max(1, int(blur * k))))
        return np.asarray(layer, dtype=np.float32) / 255.0

    halo = _taper(6.4, 3.2, 1.7)
    _over(canvas, beam_grad[..., :3], halo * beam_grad[..., 3] * 0.5)

    core = _taper(2.6, 1.1, 0.35)
    _over(canvas, np.broadcast_to(np.array([1.0, 1.0, 1.0], dtype=np.float32), (S, S, 3)),
          core * beam_grad[..., 3] * 0.95)

    # 小岛：上半圆
    yy = np.arange(S, dtype=np.float32)[:, None]
    dome = _ellipse_mask(S, ISLAND_CX * k, ISLAND_CY * k, ISLAND_RX * k, ISLAND_RY * k)
    dome = dome * np.clip((WATER_Y * k - yy) + 0.5, 0.0, 1.0)
    island_rgb = _vgrad(S, ISLAND_STOPS, (ISLAND_CY - ISLAND_RY) * k, ISLAND_CY * k, channels=3)

    # 水中倒影：水面以下压一个压扁的半椭圆，让「岛」的读法成立
    refl = Image.new("L", (S, S), 0)
    ImageDraw.Draw(refl).ellipse(
        [(ISLAND_CX - ISLAND_RX) * k, (WATER_Y - 1.0) * k, (ISLAND_CX + ISLAND_RX) * k, (WATER_Y + 9) * k],
        fill=int(255 * 0.26),
    )
    refl = refl.filter(ImageFilter.GaussianBlur(max(1, int(1.6 * k))))
    refl_arr = np.asarray(refl, dtype=np.float32) / 255.0
    below = np.clip(yy - WATER_Y * k + 0.5, 0.0, 1.0)
    refl_rgb = _vgrad(S, ISLAND_STOPS[::-1], WATER_Y * k, (WATER_Y + 12) * k, channels=3)
    _over(canvas, refl_rgb, refl_arr * below)

    _over(canvas, island_rgb, dome)

    # 岛底压一道窄暗边，岛与水面才有分层
    base = dome * np.clip(1.0 - np.abs(yy - (WATER_Y - 2.4) * k) / (2.6 * k), 0.0, 1.0)
    _over(canvas, np.broadcast_to(np.array([0.16, 0.10, 0.30], dtype=np.float32), (S, S, 3)), base * 0.32)

    # 岛的顶部轮廓高光
    rim = _ellipse_mask(S, ISLAND_CX * k, ISLAND_CY * k, ISLAND_RX * k, ISLAND_RY * k) - _ellipse_mask(
        S, ISLAND_CX * k, ISLAND_CY * k, (ISLAND_RX - 1.1) * k, (ISLAND_RY - 1.1) * k
    )
    rim = np.clip(rim, 0.0, 1.0) * np.clip((WATER_Y * k - yy) + 0.5, 0.0, 1.0)
    rim = rim * np.clip(1.0 - (yy - (ISLAND_CY - ISLAND_RY) * k) / (7.0 * k), 0.0, 1.0) * 0.9
    _over(canvas, np.broadcast_to(np.array([1.0, 0.93, 0.98], dtype=np.float32), (S, S, 3)), rim)

    img = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), mode="RGBA")
    return img.resize((size, size), Image.LANCZOS)


def render_round(size: int) -> Image.Image:
    """圆形图标：老系统兜底，裁成圆。"""
    img = render(size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    img.putalpha(mask)
    return img


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", action="store_true", help="额外输出预览图")
    args = parser.parse_args()

    for density, px in DENSITIES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        # 沿用旧资源名 ic_launcher_rgb / ic_launcher_rgb_round，避免改动清单里的引用
        render(px).save(out_dir / "ic_launcher_rgb.png")
        render_round(px).save(out_dir / "ic_launcher_rgb_round.png")
        print(f"  mipmap-{density}: {px}px")

    if args.preview:
        preview_dir = ROOT / "build"
        preview_dir.mkdir(exist_ok=True)
        big = render(384, ss=3)
        round_big = render_round(384).resize((384, 384), Image.LANCZOS)
        small = render(48, ss=8)
        mock = Image.new("RGBA", (384 * 2 + 60 + 48 + 80, 384), (22, 22, 26, 255))
        mock.paste(big, (0, 0), big)
        mock.paste(round_big, (384 + 60, 0), round_big)
        mock.paste(small, (384 * 2 + 60 + 40, 40), small)
        mock.paste(render(96, ss=6), (384 * 2 + 60 + 40, 140), render(96, ss=6))
        mock.save(preview_dir / "icon-preview.png")
        print(f"  preview: {preview_dir / 'icon-preview.png'}")


if __name__ == "__main__":
    main()
