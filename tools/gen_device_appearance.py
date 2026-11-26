"""从用户提供的产品渲染图生成设备页外观资源（按型号分目录）。

原图：2026-11-24/app-dev-taskbook/assets/C01-render.jfif（1792×2400，白底）
    银灰金属底座 + 透明圆柱罩，罩内整段纯绿色区域即发光体。
    设备页把绿色区域替换成当前灯效的动态预览，因此需要：
      1. 去掉白底，只留灯体（含金属顶盖/底座与亚克力罩边缘）；
      2. 把绿色发光区掏空（alpha=0），让动态内容从背后透出来；
      3. 罩壁的绿边/暗边去饱和成中性玻璃灰，避免蓝色灯效配绿边。

产出（app/app/src/main/assets/device_models/<型号>/）：
  - lamp.webp   灯体位图（透明底，发光区已掏空）
  - meta.json   图片尺寸 + 发光区归一化坐标（APP 按此摆放动态内容）

APP 侧只按固件 /json/info 的 product 字段选目录，新增型号时放一套同名资源即可，
无需改 Kotlin 代码。

用法:
    python tools/gen_device_appearance.py                 # 生成 C01 资源
    python tools/gen_device_appearance.py --preview       # 额外输出合成预览到 build/
"""

from __future__ import annotations

import argparse
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import binary_erosion

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "2026-11-24", "app-dev-taskbook", "assets", "C01-render.jfif")
ASSET_ROOT = os.path.join(ROOT, "app", "app", "src", "main", "assets", "device_models")
BUILD_DIR = os.path.join(ROOT, "build")
PREVIEW_GIF = os.path.join(ROOT, "app", "app", "src", "main", "assets", "effect_previews", "8.gif")

MODEL = "c01"
PRODUCT = "C01"
OUTPUT_HEIGHT = 1200

# 灯体裁切框（原图像素，留 2px 余量；下方阴影不参与，避免深色背景上出现灰块）
CROP = (517, 271, 1274, 2166)  # left, top, right, bottom

# 圆柱罩区域（原图像素）：绿色发光区 + 罩壁高光都落在这里
GLASS = (522, 322, 1270, 1685)

# 纯绿色发光体的判定：绿色通道显著高于红蓝，且足够亮
GREEN_DOMINANCE = 50
GREEN_MIN = 100

# 背景白判定：亮度高且几乎无彩度
BG_LUM = 243
BG_SAT = 8


def load_source() -> Image.Image:
    return Image.open(SOURCE).convert("RGB")


def background_mask(rgb: np.ndarray) -> np.ndarray:
    """白底掩膜：亮度接近纯白、彩度极低。"""
    lum = rgb.mean(axis=2)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    return (lum >= BG_LUM) & (sat <= BG_SAT)


def build_alpha(rgb: np.ndarray) -> np.ndarray:
    """灯体 alpha：白底透明，其余不透明。

    产品图带白色投影与抗锯齿白边，直接按白底阈值抠会留下一圈发白光晕：
      - 投影（底座下方的那片浅灰）按列找底座实心下沿，把下沿以下全部清掉；
      - 抗锯齿白边靠 1px 腐蚀去掉，再羽化半像素。
    """
    bg = background_mask(rgb)
    solid = ~bg
    lum = rgb.mean(axis=2)

    # 底座实心下沿：自下而上找每列最后一处明显暗于投影的像素
    h, w = lum.shape
    bottom = np.full(w, h - 1)
    for x in range(w):
        column = np.where(lum[:, x] < 200)[0]
        if column.size:
            bottom[x] = column.max()
    for x in range(w):
        solid[bottom[x] + 2:, x] = False

    # 左右两侧的投影同样按行清掉：每行只保留明显暗于投影的最左/最右之间
    for y in range(h):
        row = np.where(lum[y, :] < 200)[0]
        if row.size:
            solid[y, : max(row.min() - 1, 0)] = False
            solid[y, row.max() + 2:] = False

    eroded = binary_erosion(solid, structure=np.ones((3, 3), dtype=bool))
    alpha = np.where(eroded, 255, 0).astype(np.uint8)
    soft = Image.fromarray(alpha, "L").filter(ImageFilter.GaussianBlur(0.6))
    return np.array(soft)


def glow_hole(rgb: np.ndarray) -> tuple[np.ndarray, tuple[int, int, int, int]]:
    """发光区掏空 + 罩壁去饱和，返回 alpha 与纯绿发光体的外接框。

    纯绿核心直接 alpha=0；罩壁的绿边与顶部暗绿带去饱和成中性灰，
    这样动态内容透出来时不会残留绿色描边，任何色系的灯效都自然。
    """
    alpha = build_alpha(rgb)
    h, w, _ = rgb.shape
    x0, y0, x1, y1 = GLASS

    green = (rgb[..., 1].astype(np.int16) - np.maximum(rgb[..., 0], rgb[..., 2]).astype(np.int16) >= GREEN_DOMINANCE) \
        & (rgb[..., 1] >= GREEN_MIN)
    hole = np.zeros((h, w), dtype=bool)
    hole[y0:y1, x0:x1] = green[y0:y1, x0:x1]
    alpha[hole] = 0

    # 罩内非绿色像素：压掉绿偏，变成玻璃灰
    region = np.zeros((h, w), dtype=bool)
    region[y0:y1, x0:x1] = True
    rim = region & ~hole
    grey = rgb.mean(axis=2).astype(np.uint8)
    for channel in range(3):
        rgb[..., channel] = np.where(rim, grey, rgb[..., channel])

    # 洞缘 1px 羽化，避免内容与灯体之间出现硬边
    hole_img = Image.fromarray((hole * 255).astype(np.uint8), "L")
    feather = hole_img.filter(ImageFilter.GaussianBlur(1.0))
    feathered = np.asarray(feather)
    alpha = np.minimum(alpha, 255 - feathered)

    ys, xs = np.where(hole)
    core = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    return np.array(alpha), core


def build_lamp() -> tuple[Image.Image, dict]:
    rgb = np.asarray(load_source()).copy()
    alpha, core = glow_hole(rgb)

    rgba = np.dstack([rgb, alpha])
    lamp = Image.fromarray(rgba, "RGBA").crop(CROP)

    # 缩放到目标高度（保持比例）
    scale = OUTPUT_HEIGHT / lamp.height
    size = (round(lamp.width * scale), OUTPUT_HEIGHT)
    lamp = lamp.resize(size, Image.LANCZOS)

    cw, ch = CROP[2] - CROP[0], CROP[3] - CROP[1]

    def rect(box: tuple[int, int, int, int]) -> dict:
        return {
            "left": round((box[0] - CROP[0]) / cw, 5),
            "top": round((box[1] - CROP[1]) / ch, 5),
            "right": round((box[2] - CROP[0]) / cw, 5),
            "bottom": round((box[3] - CROP[1]) / ch, 5),
        }

    meta = {
        "product": PRODUCT,
        "image": "lamp.webp",
        "width": size[0],
        "height": size[1],
        # glass: 整个圆柱罩范围（罩壁 + 发光体），用于铺亚克力底与磨砂罩
        # glow:  纯绿色发光体范围，灯效动态内容只画在这一块里，不会越出罩壁
        "glass": rect(GLASS),
        "glow": rect(core),
    }
    return lamp, meta


def write_outputs(lamp: Image.Image, meta: dict) -> str:
    out_dir = os.path.join(ASSET_ROOT, MODEL)
    os.makedirs(out_dir, exist_ok=True)
    lamp_path = os.path.join(out_dir, meta["image"])
    lamp.save(lamp_path, "WEBP", quality=90, method=6)
    with open(os.path.join(out_dir, "meta.json"), "w", encoding="utf-8") as fp:
        json.dump(meta, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    return lamp_path


def write_preview(lamp: Image.Image, meta: dict) -> str:
    """把灯体与一帧灯效预览合成到深色背景上，模拟 APP 里的模糊 + 磨砂罩叠层。"""
    os.makedirs(BUILD_DIR, exist_ok=True)
    canvas = Image.new("RGB", (720, 900), (11, 14, 20))
    preview = Image.open(PREVIEW_GIF)
    preview.seek(3)
    preview = preview.convert("RGB")

    scale = 0.62
    target_w, target_h = round(lamp.width * scale), round(lamp.height * scale)
    lamp_scaled = lamp.resize((target_w, target_h), Image.LANCZOS)
    ox, oy = (canvas.width - target_w) // 2, (canvas.height - target_h) // 2

    def to_px(rect: dict) -> tuple[int, int, int, int]:
        return (
            ox + round(rect["left"] * target_w),
            oy + round(rect["top"] * target_h),
            ox + round(rect["right"] * target_w),
            oy + round(rect["bottom"] * target_h),
        )

    glass = to_px(meta["glass"])
    glow = to_px(meta["glow"])

    # 亚克力底
    canvas.paste(Image.new("RGB", (glass[2] - glass[0], glass[3] - glass[1]), (12, 22, 18)), glass[:2])

    # 灯效内容：内缩到发光体范围并做高斯模糊，模拟磨砂扩散
    effect = preview.resize((glow[2] - glow[0], glow[3] - glow[1]), Image.LANCZOS)
    effect = effect.filter(ImageFilter.GaussianBlur(5))
    canvas.paste(effect, glow[:2])

    # 圆柱罩的横向明暗与竖向高光（模拟 APP 里的叠层）
    shade = Image.new("L", (glow[2] - glow[0], glow[3] - glow[1]), 0)
    draw = ImageDraw.Draw(shade)
    for x in range(glow[2] - glow[0]):
        t = x / max(glow[2] - glow[0] - 1, 1)
        edge = min(t, 1 - t) / 0.18
        draw.line([(x, 0), (x, glow[3] - glow[1])], fill=int(90 * max(0.0, 1 - edge)))
    black = Image.new("RGB", shade.size, (0, 0, 0))
    canvas.paste(Image.composite(black, canvas.crop(glow), shade), glow[:2])

    # 磨砂亚克力罩：整体泛白 + 竖向厚度差
    veil = Image.new("RGBA", (glass[2] - glass[0], glass[3] - glass[1]), (226, 242, 233, 34))
    top = Image.new("RGBA", (veil.width, veil.height // 6), (236, 248, 240, 26))
    veil.alpha_composite(top, (0, 0))
    veil.alpha_composite(top.transpose(Image.FLIP_TOP_BOTTOM), (0, veil.height - top.height))
    canvas.paste(veil, glass[:2], veil)

    canvas.paste(lamp_scaled, (ox, oy), lamp_scaled)

    path = os.path.join(BUILD_DIR, "device_appearance_preview.png")
    canvas.save(path)
    return path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", action="store_true", help="输出合成预览图到 build/")
    args = parser.parse_args()

    lamp, meta = build_lamp()
    path = write_outputs(lamp, meta)
    print(f"wrote {path} ({os.path.getsize(path) // 1024} KB, {meta['width']}x{meta['height']})")
    print("glow rect", meta["glow"])
    if args.preview:
        print("preview", write_preview(lamp, meta))


if __name__ == "__main__":
    main()
