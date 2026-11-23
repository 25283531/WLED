"""本地假 WLED 设备，用于在没有实物灯的情况下验证 App。

模拟固件的 HTTP + WebSocket 接口，够 App 走完「发现 → 添加 → 控制 → 下发」全链路：

    GET  /json/info     设备信息（含 leds.count 与 leds.matrix，模拟 9×23 圆管）
    GET  /json/state    当前状态
    GET  /json/effects  灯效名列表（取自 firmware/effects_enabled.json）
    GET  /json          info + state + effects
    POST /json/state    应用状态（合并 seg 补丁，并广播给所有 WS 客户端）
    WS   /ws            连上推一次全量状态，之后双向同步

用法:
    python tools/mock_wled_server.py --port 8080
    # 安卓模拟器里添加设备时填：10.0.2.2:8080

注意：只实现 App 用到的字段，不是完整的 WLED 实现。
"""

from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

from aiohttp import WSMsgType, web

ROOT = Path(__file__).resolve().parents[1]
EFFECTS_FILE = ROOT / "firmware" / "effects_enabled.json"

MAC = "A0:B1:C2:D3:E4:F5"
MATRIX_W, MATRIX_H = 9, 23
LED_COUNT = MATRIX_W * MATRIX_H


def load_effect_names() -> list[str]:
    try:
        data = json.loads(EFFECTS_FILE.read_text(encoding="utf-8"))
        return [e["name"] for e in data]
    except Exception:
        return ["Solid", "Blink", "Breathe"]


EFFECTS = load_effect_names()

INFO = {
    "ver": "0.15.0",
    "vid": 2412100,
    "cn": "Mock Build",
    "release": "光屿 Mock",
    "name": "光屿 Mock 灯",
    "mac": MAC,
    "brand": "WLED",
    "product": "FOSS",
    "arch": "esp32",
    "leds": {
        "count": LED_COUNT,
        "pwr": 132,
        "fps": 42,
        "maxpwr": 850,
        "maxseg": 16,
        # bootps: 开机加载的预设编号，0 = 不加载预设（用固件出厂默认灯效）
        "bootps": 0,
        # bootfx: 固件编译期的出厂默认灯效 id（与 firmware/wled00/FX.h 的 DEFAULT_MODE 一致）
        "bootfx": 221,
        "matrix": {"w": MATRIX_W, "h": MATRIX_H},
    },
    "wifi": {"rssi": -52, "signal": 78, "channel": 6, "bssid": "AA:BB:CC:DD:EE:FF"},
    "fxcount": len(EFFECTS),
    "palcount": 72,
    "ndc": 1,
    "fs": {"u": 200000, "t": 1048576, "pm": 1048576},
    "str": False,
    "udpport": 21324,
    "live": False,
    "lm": "",
}

STATE: dict = {
    "on": True,
    "bri": 128,
    "transition": 7,
    "ps": -1,
    "pl": -1,
    "lor": 0,
    "mainseg": 0,
    "nl": {"on": False, "dur": 60, "fade": True, "mode": 1, "tbri": 0},
    "seg": [
        {
            "id": 0,
            "start": 0,
            "stop": LED_COUNT,
            "len": LED_COUNT,
            "grp": 1,
            "spc": 0,
            "on": True,
            "bri": 255,
            "col": [[255, 160, 0], [0, 0, 0], [0, 0, 0]],
            "fx": 2,
            "sx": 128,
            "ix": 128,
            "pal": 0,
            "sel": True,
            "rev": False,
            "mi": False,
            "n": "",
        }
    ],
}

CLIENTS: set[web.WebSocketResponse] = set()


def snapshot() -> dict:
    return {"state": copy.deepcopy(STATE), "info": INFO}


def apply_patch(payload: dict) -> None:
    """把 WLED 风格的补丁合并进 STATE。seg 按 id 逐段合并。"""
    if "seg" in payload and isinstance(payload["seg"], list):
        existing = {s.get("id", 0): s for s in STATE.get("seg", [])}
        for patch in payload["seg"]:
            sid = patch.get("id", 0)
            base = existing.get(sid, {"id": sid})
            base.update(patch)
            existing[sid] = base
        STATE["seg"] = [existing[k] for k in sorted(existing)]
    for key in ("on", "bri", "transition", "ps", "pl", "lor", "mainseg"):
        if key in payload:
            STATE[key] = payload[key]
    if "nl" in payload and isinstance(payload["nl"], dict):
        STATE["nl"].update(payload["nl"])


async def broadcast() -> None:
    msg = json.dumps(snapshot())
    for ws in list(CLIENTS):
        if not ws.closed:
            await ws.send_str(msg)


async def json_info(_: web.Request) -> web.Response:
    return web.json_response(INFO)


async def json_state(_: web.Request) -> web.Response:
    return web.json_response(STATE)


async def json_effects(_: web.Request) -> web.Response:
    return web.json_response(EFFECTS)


async def json_all(_: web.Request) -> web.Response:
    return web.json_response({**snapshot(), "effects": EFFECTS, "palettes": []})


async def post_state(request: web.Request) -> web.Response:
    try:
        payload = await request.json()
    except Exception:
        return web.json_response({"success": False, "error": "bad json"}, status=400)
    print("[mock] POST /json/state ->", json.dumps(payload, ensure_ascii=False)[:300])
    apply_patch(payload)
    await broadcast()
    return web.json_response({"success": True})


async def ws_handler(request: web.Request) -> web.WebSocketResponse:
    ws = web.WebSocketResponse(heartbeat=30)
    await ws.prepare(request)
    CLIENTS.add(ws)
    print(f"[mock] WS 连接，当前 {len(CLIENTS)} 个")
    await ws.send_str(json.dumps(snapshot()))
    try:
        async for msg in ws:
            if msg.type != WSMsgType.TEXT:
                continue
            try:
                payload = json.loads(msg.data)
            except Exception:
                continue
            print("[mock] WS <-", json.dumps(payload, ensure_ascii=False)[:300])
            if payload.get("v"):
                await ws.send_str(json.dumps(snapshot()))
                continue
            apply_patch(payload)
            await broadcast()
    finally:
        CLIENTS.discard(ws)
        print(f"[mock] WS 断开，剩余 {len(CLIENTS)} 个")
    return ws


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--host", default="0.0.0.0")
    args = parser.parse_args()

    app = web.Application()
    app.router.add_get("/json/info", json_info)
    app.router.add_get("/json/state", json_state)
    app.router.add_get("/json/effects", json_effects)
    app.router.add_get("/json", json_all)
    app.router.add_post("/json/state", post_state)
    app.router.add_post("/json", post_state)
    app.router.add_get("/ws", ws_handler)

    print(f"假 WLED 已启动：http://127.0.0.1:{args.port}")
    print(f"安卓模拟器里添加设备填：10.0.2.2:{args.port}")
    print(f"灯珠 {LED_COUNT} 颗（{MATRIX_W}×{MATRIX_H}），MAC {MAC}，灯效 {len(EFFECTS)} 个")
    web.run_app(app, host=args.host, port=args.port, print=None)


if __name__ == "__main__":
    main()
