"""从用户提供的原图生成 APP 图标资源（Android 各密度 + iOS 1024）。

原图：docs/assets/lumi_aurora_icon_source.jpg（1920×1920）
    深空蓝灰夜色底上，两股自下而上旋转交织的半透明丝绸光带，
    颜色由底部亮青 (#00F5FF) -> 中部蔚蓝 (#1E78FF) -> 顶部紫罗兰 (#AF28FF)，
    脚下三圈同心青色椭圆涟漪。
    与固件招牌灯效 221「极光之舞」(mode_lumi_aurora) 同源同色 —— 图标与灯效互相呼应。

产出：
  - 自适应图标（API 26+）：前景层放原图位图，背景层放同色深空渐变矢量，
    单色层放主体剪影。系统按圆形/方形 mask 裁切。
  - legacy 位图（API 24–25 / 小组件预览）：原图方形与圆形两个版本。
  - 启动闪屏图标：原图方形（windowSplashScreenAnimatedIcon 引用）。
  - iOS AppIcon：1024×1024（iOS 不接受带 alpha 的图标）。

主体在原图中的位置（实测掩膜）：x 24.4%–75.6%、y 15.6%–85.9%，
正好落在自适应图标的中央 72/108 安全区内，因此原图可以整幅映射到 108dp 画布，
圆形 mask 下丝带尖端与底部涟漪都不会被削掉 —— 无需缩放，100% 保真。

用法:
    python tools/gen_app_icon.py             # 写入 res/mipmap-*/ 与 iOS AppIcon
    python tools/gen_app_icon.py --preview   # 额外输出预览到 build/icon-preview.png
"""

from __future__ import annotations

import argparse
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "app", "src", "main", "res")
SOURCE = os.path.join(ROOT, "docs", "assets", "lumi_aurora_icon_source.jpg")
IOS_ICON_DIR = os.path.join(ROOT, "app", "iosApp", "wled", "Assets.xcassets", "AppIcon.appiconset")

# 自适应图标画布：前景/背景/单色层各 108dp，系统 mask 保证中央 72dp 可见
ADAPTIVE_CANVAS_DP = 108.0

# legacy 图标各密度边长（dp == px）
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 与原图背景一致的深空色（自适应图标的背景层矢量用；采样自原图四角 rgb≈(33,41,54)）
BG_STOPS = [
    (0.00, (31, 39, 53)),
    (0.55, (28, 35, 48)),
    (1.00, (23, 30, 42)),
]


def load_source() -> Image.Image:
    """载入原图（RGB，去掉 JPEG 附带的 profile 与 EXIF 方向）。"""
    img = Image.open(SOURCE)
    if img.mode != "RGB":
        img = img.convert("RGB")
    return img


def _silhouette_alpha(src: Image.Image) -> np.ndarray:
    """主体剪影 alpha：用大半径模糊估计背景场，取超出背景的部分并软化边缘。

    丝带本体与底部涟漪的亮度都远高于背景，辉光则平滑衰减到背景色，
    因此「与背景场的差」比固定亮度阈值更贴合这张图的实际结构。
    """
    a = np.asarray(src, dtype=np.float32)
    bg = np.asarray(src.filter(ImageFilter.GaussianBlur(120)), dtype=np.float32)
    d = np.abs(a - bg).max(axis=2)
    return np.clip((d - 10.0) / 22.0, 0.0, 1.0)


def render_legacy(src: Image.Image, size: int, round_mask: bool = False) -> Image.Image:
    """legacy 图标：原图铺满 size×size。

    圆形版保留 alpha（角透明），**不能**再 convert("RGB") —— 那会把 alpha 丢掉，
    四角又变回方形原图（上一轮脚本就有这个 bug，圆标与方标字节数完全一样）。
    """
    img = src.resize((size, size), Image.LANCZOS)
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        img = img.convert("RGBA")
        img.putalpha(mask)
    return img


def render_adaptive_foreground(src: Image.Image, size: int) -> Image.Image:
    """自适应图标前景层：原图铺满 108dp 画布（size 已按密度换算）。"""
    return src.resize((size, size), Image.LANCZOS).convert("RGBA")


def render_monochrome(src: Image.Image, size: int) -> Image.Image:
    """Android 13+ 主题图标：只取主体剪影，由系统上色。"""
    alpha = _silhouette_alpha(src)
    mask = Image.fromarray((alpha * 255).astype(np.uint8), "L").resize((size, size), Image.LANCZOS)
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., 0:3] = 255
    rgba[..., 3] = np.asarray(mask, dtype=np.uint8)
    return Image.fromarray(rgba, "RGBA")


def render_splash(src: Image.Image, size: int) -> Image.Image:
    """启动闪屏图标：去背景的彩色丝带与涟漪（透明底）。

    闪屏会把图标居中画在圆形底上，若沿用带深空底的整图会露出一个方块。
    这里保留主体颜色、把背景底色按 alpha 权重扣掉：
    alpha=1 处颜色不变，alpha→0 处褪回背景色，与闪屏底色自然融合。
    """
    alpha = _silhouette_alpha(src)
    a = np.asarray(src, dtype=np.float32)
    bg = np.asarray(src.filter(ImageFilter.GaussianBlur(120)), dtype=np.float32)
    # 物理反解：观测色 = 主体色×α + 背景色×(1-α)  ⇒  主体色 = (观测色 − 背景×(1−α)) / α
    # 直接用「观测色 − 背景×(1−α)」会在半透明边缘留下一圈发暗的描边，
    # 除以 α 把辉光提亮回它该有的亮度，闪屏底上才是柔光而不是暗边。
    safe_a = np.maximum(alpha, 0.08)[..., None]
    rgb = np.clip((a - bg * (1.0 - alpha[..., None])) / safe_a, 0, 255)

    # 先缩到目标尺寸再拼 RGBA（numpy 不能广播不同尺寸）
    rgb = np.asarray(Image.fromarray(rgb.astype(np.uint8), "RGB").resize((size, size), Image.LANCZOS), dtype=np.float32)
    alpha = np.asarray(
        Image.fromarray((alpha * 255).astype(np.uint8), "L").resize((size, size), Image.LANCZOS),
        dtype=np.float32,
    ) / 255.0

    rgba = np.zeros((size, size, 4), dtype=np.float32)
    rgba[..., 0:3] = rgb
    rgba[..., 3] = alpha * 255.0
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def write_png(img: Image.Image, folder: str, name: str) -> None:
    path = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)


def write_ios_icon(src: Image.Image) -> None:
    """iOS AppIcon：1024×1024，且不能带 alpha 通道。"""
    os.makedirs(IOS_ICON_DIR, exist_ok=True)
    out = os.path.join(IOS_ICON_DIR, "wled-icon.png")
    src.resize((1024, 1024), Image.LANCZOS).convert("RGB").save(out, "PNG", optimize=True)
    print(f"{'ios AppIcon':18s} 1024×1024 -> {os.path.relpath(out, ROOT)}")

    # asset catalog 只按 Contents.json 里的 filename 取图，pbxproj 不引用具体文件名，
    # 所以把旧的 wled-icon-gray.png 换成语义贴切的新名是安全的。
    manifest = os.path.join(IOS_ICON_DIR, "Contents.json")
    if os.path.exists(manifest):
        data = json.load(open(manifest, encoding="utf-8"))
        changed = False
        for entry in data.get("images", []):
            if entry.get("filename") == "wled-icon-gray.png":
                entry["filename"] = "wled-icon.png"
                changed = True
        if changed:
            with open(manifest, "w", encoding="utf-8") as fh:
                json.dump(data, fh, indent=2, ensure_ascii=False)
                fh.write("\n")
            print("  更新 Contents.json filename -> wled-icon.png")

    stale = os.path.join(IOS_ICON_DIR, "wled-icon-gray.png")
    if os.path.exists(stale):
        os.remove(stale)
        print("  删除旧图标 wled-icon-gray.png")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()

    src = load_source()
    print(f"原图: {os.path.relpath(SOURCE, ROOT)}  {src.size[0]}×{src.size[1]}")

    for density, legacy_size in DENSITIES.items():
        folder = f"mipmap-{density}"
        # 自适应图标的层是 108dp 画布，按密度换算成像素
        layer_size = int(round(legacy_size * ADAPTIVE_CANVAS_DP / 48.0))

        write_png(render_legacy(src, legacy_size), folder, "ic_launcher_rgb.png")
        write_png(render_legacy(src, legacy_size, round_mask=True), folder, "ic_launcher_rgb_round.png")
        # 闪屏图标按 108dp 层出图：SplashScreen 会把它放大到图标槽位，
        # 用 legacy 尺寸会放大 4 倍糊掉
        write_png(render_splash(src, layer_size), folder, "ic_launcher_rgb_foreground.png")
        write_png(render_adaptive_foreground(src, layer_size), folder, "ic_launcher_foreground_adaptive.png")
        write_png(render_monochrome(src, layer_size), folder, "ic_launcher_monochrome.png")
        print(f"{folder:18s} legacy {legacy_size}×{legacy_size}   adaptive/闪屏 层 {layer_size}×{layer_size}")

    write_ios_icon(src)

    if args.preview:
        sheet = Image.new("RGB", (192 * 5 + 80, 192 + 60), (16, 18, 24))
        x = 12
        for size in (48, 72, 96):
            sheet.paste(render_legacy(src, size), (x, 24))
            x += size + 12
        sheet.paste(render_legacy(src, 192, round_mask=True), (x, 24))
        x += 204
        splash = render_splash(src, 192)
        sheet.paste(splash, (x, 24), splash)
        x += 204
        mono = render_monochrome(src, 192)
        sheet.paste(mono, (x, 24), mono)  # 用 alpha 合成，否则 convert("RGB") 会丢掉剪影
        out = os.path.join(ROOT, "build", "icon-preview.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        sheet.save(out)
        print("预览:", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
