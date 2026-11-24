"""滑块 tap → 设备通道值 的端到端校验脚本。

背景（见 APP_REDESIGN_SPEC.md 第 16 节）：
修复前，灯效速度滑块把「比例 → 通道值」换算做了两遍（×255/100 各一次），
可输出集合只剩 {0,2,5,7}；同时 IsleSlider 的 pointerInput(Unit) 捕获陈旧回调，
设备回写后写进已废弃的 state，表现为圆头不动但后台数值在跳。

本脚本在模拟器上按等分位置 tap 速度滑块，并从 mock WLED 读回 seg.sx，
校验三件事：
  1. sx 覆盖 0..255 全量程（修复前最大只有 7）
  2. sx 随 tap 位置单调递增
  3. 圆头像素位置与 sx 一致（证明 UI 不再卡死）

用法:
    python tools/verify_slider_tap.py [--serial emulator-5554] [--host 10.0.1.193]
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from png_crop import read_png  # noqa: E402

ADB = r"C:\Users\Administrator\.workbuddy-ai\binaries\adb\platform-tools\adb.exe"
# 参数滑块（速度/强度）的 Box 边界：由「圆头位置 ↔ 通道值」线性反解得出
#   thumb_x = 199 + clamp(ch/255 * 1040, 32, 1040-32)
# 亮度滑块多一层 padding(horizontal = 12.dp) 与 40.dp 百分比文字，Box 略窄（952）。
TRACK_LEFT = 199
TRACK_WIDTH = 1040
TRACK_RIGHT = TRACK_LEFT + TRACK_WIDTH
THUMB_RADIUS = 32           # IsleSlider 的 thumbRadius = 8.dp，密度 4.0 → 32px


def locate_thumb(path: Path, search_y: int, left: int, right: int, window: int = 60) -> int | None:
    """在 search_y 附近找滑块圆头（最亮的青白色团块）的中心 x。"""
    w, h, nch, _ctype, pix, _plte = read_png(str(path))
    best = None
    best_score = 0
    for y in range(search_y - window, search_y + window, 2):
        xs = [
            x
            for x in range(left - 40, right + 40)
            if sum(pix[(y * w + x) * nch: (y * w + x) * nch + 3]) > 620
        ]
        if xs:
            score = len(xs)
            if score > best_score:
                best_score = score
                best = (min(xs) + max(xs)) // 2
    return best


def adb(serial: str, *args: str, binary: bool = False):
    cmd = [ADB, "-s", serial, *args]
    if binary:
        return subprocess.run(cmd, capture_output=True, check=True).stdout
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout


def read_state(host: str) -> dict:
    with urllib.request.urlopen(f"http://{host}:8080/json/state", timeout=8) as resp:
        return json.loads(resp.read().decode())


def screenshot(serial: str, out: Path) -> Path:
    remote = "/sdcard/_verify_slider.png"
    adb(serial, "shell", "screencap", "-p", remote)
    adb(serial, "pull", remote, str(out))
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--host", default="10.0.1.193")
    ap.add_argument("--steps", type=int, default=11)
    ap.add_argument("--settle", type=float, default=1.2)
    ap.add_argument("--y", type=int, required=True, help="滑块轨道所在的屏幕 y 坐标（1440x2560）")
    ap.add_argument("--field", default="sx", help="mock 设备里的字段：seg.sx / seg.ix / bri")
    ap.add_argument("--label", default="slider")
    ap.add_argument("--left", type=int, default=TRACK_LEFT, help="轨道左端 x")
    ap.add_argument("--width", type=int, default=TRACK_WIDTH, help="轨道宽度")
    args = ap.parse_args()

    shots = ROOT / "dist"
    shots.mkdir(exist_ok=True)

    track_left = args.left
    track_width = args.width
    track_right = track_left + track_width

    def read_channel() -> int:
        state = read_state(args.host)
        if args.field == "bri":
            return state["bri"]
        return state["seg"][0][args.field]

    # 取点必须落在轨道像素范围内：右端是开区间（x=1240 已在 Box 之外），
    # 左端 x=199 是边界像素，tap 会被父容器吃掉 —— 两端各内缩 1px。
    tap_min = track_left + 1
    tap_max = track_right - 1

    print(f"== {args.label}（{args.field}）==")
    print(f"{'tap x':>7} {'期望通道':>8} {'实测值':>8} {'圆头 x':>8}  判定")
    rows = []
    for i in range(args.steps):
        ratio = i / (args.steps - 1)
        tap_x = int(round(tap_min + ratio * (tap_max - tap_min)))
        # 与 App 内部算法一致：offset.x / size.width 再乘 255
        expect = int(round((tap_x - track_left) / track_width * 255))

        adb(args.serial, "shell", "input", "tap", str(tap_x), str(args.y))
        time.sleep(args.settle)

        sx = read_channel()
        shot = screenshot(args.serial, shots / f"verify_{args.label}_{i:02d}.png")
        thumb = locate_thumb(shot, args.y, track_left, track_right)

        ok = abs(sx - expect) <= 2
        rows.append((tap_x, expect, sx, thumb))
        print(f"{tap_x:>7} {expect:>8} {sx:>8} {str(thumb):>8}  {'OK' if ok else 'MISMATCH'}")

    print()
    sxs = [r[2] for r in rows]
    mono = all(sxs[i] >= sxs[i - 1] for i in range(1, len(sxs)))
    full_range = min(sxs) <= 2 and max(sxs) >= 253
    print(f"单调递增: {'PASS' if mono else 'FAIL'}")
    print(f"覆盖全量程 (min={min(sxs)}, max={max(sxs)}): {'PASS' if full_range else 'FAIL'}")

    # 圆头位置应与通道值一致：thumb_x = 200 + clamp(ch/255 * 1040, 32, 1040-32)
    # 钳位是 IsleSlider 的防溢出处理（thumb 不画出轨道），不是缺陷。
    print("\n圆头位置一致性（UI 是否跟随设备值）:")
    ui_ok = True
    for tap_x, expect, sx, thumb in rows:
        if thumb is None:
            print(f"  值={sx:>3} 圆头未检出")
            ui_ok = False
            continue
        filled = sx / 255.0 * track_width
        theory = track_left + min(max(filled, THUMB_RADIUS), track_width - THUMB_RADIUS)
        delta = abs(thumb - theory)
        flag = "OK" if delta <= 2 else "MISMATCH"
        if delta > 2:
            ui_ok = False
        print(f"  值={sx:>3}  理论圆头 x={theory:7.1f}  实测 x={thumb:>4}  Δ={delta:5.1f}  {flag}")
    print(f"\n圆头跟随: {'PASS' if ui_ok else 'FAIL'}")
    return 0 if (mono and full_range and ui_ok) else 1


if __name__ == "__main__":
    raise SystemExit(main())
