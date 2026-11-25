#!/usr/bin/env python3
"""WLED Audio Sync v2（UDP 11988）协议验证脚本。

用途
----
在实现 Android 端采集/FFT 之前，先用 PC 侧按固件 `audioSyncPacket` 结构
（`firmware/usermods/audioreactive_v1/audio_reactive.cpp` 第 802 行，44 字节、
`__attribute__((packed))`）发送合成音频包，验证：

- 协议头、字节序、字段偏移是否被固件正确解析（`/json/info.u` 出现 `v2`、峰值变化）；
- 设备处于网络接收模式（`um.AudioReactive.digitalmic.type = 254`，或 `sync.mode` 含接收位）时，
  音律类灯效是否随包内容律动。

设备侧前置条件
--------------
1. `cfg.um.AudioReactive.enabled = true`；
2. 接收位打开：`sync.mode` 的 bit1（值 2 = 仅接收，3 = 收发）；
   或直接把 `digitalmic.type` 设为 254（`SR_DMTYPE_NETWORK_ONLY`，强制只收网络音频）。
   `sync.mode` 在 setup()/connected() 里才 join 组播，改完需重启设备。

用法
----
    python tools/audio_sync_sender.py                      # 组播 239.0.0.1:11988，100 BPM，20 秒
    python tools/audio_sync_sender.py --pattern sweep      # 频谱横扫
    python tools/audio_sync_sender.py --iface 10.0.1.5     # 指定组播出口网卡
"""

from __future__ import annotations

import argparse
import math
import random
import socket
import struct
import time

HEADER = b"00002\x00"
# 与固件 audioSyncPacket 一一对应：header[6] reserved1[2] sampleRaw sampleSmth
# samplePeak reserved2 fftResult[16] reserved3[2] FFT_Magnitude FFT_MajorPeak
PACKET = struct.Struct("<6s2sffBB16sHff")
assert PACKET.size == 44

GEQ_CHANNELS = 16


def build_packet(sample_raw: float, sample_smth: float, peak: bool, geq: list[int],
                 magnitude: float, major_peak: float) -> bytes:
    return PACKET.pack(
        HEADER,
        b"\x00\x00",
        float(sample_raw),
        float(sample_smth),
        1 if peak else 0,
        0,
        bytes(max(0, min(255, int(v))) for v in geq),
        0,
        float(magnitude),
        float(major_peak),
    )


def beat_envelope(t: float, bpm: float) -> float:
    """每拍一个指数衰减包络，模拟底鼓。"""
    beat_period = 60.0 / bpm
    phase = (t % beat_period) / beat_period
    return math.exp(-phase * 6.0)


def synth_levels(t: float, bpm: float, pattern: str) -> tuple[float, float, bool, list[int], float, float]:
    env = beat_envelope(t, bpm)
    if pattern == "flat":
        level = 120.0
        geq = [140] * GEQ_CHANNELS
    elif pattern == "sweep":
        level = 110.0 + 40.0 * math.sin(t * 2.0)
        geq = []
        for i in range(GEQ_CHANNELS):
            # 一个随时间的三角峰在 16 段上移动
            center = (t * 4.0) % 2.0
            center = center if center < 1.0 else 2.0 - center
            dist = abs(i / (GEQ_CHANNELS - 1) - center)
            geq.append(int(220 * max(0.0, 1.0 - dist * 2.2)))
    else:  # beat
        level = 30.0 + 190.0 * env
        geq = [
            int(min(255, 40 + 215 * env * (1.0 - i * 0.05) + random.uniform(0, 12)))
            for i in range(GEQ_CHANNELS)
        ]
    sample_peak = env > 0.55
    magnitude = 200.0 + 2400.0 * env
    major_peak = 55.0 + 120.0 * env
    return level, level * 0.85, sample_peak, geq, magnitude, major_peak


def main() -> int:
    parser = argparse.ArgumentParser(description="WLED Audio Sync v2 protocol sender")
    parser.add_argument("--host", default="239.0.0.1", help="目标地址（默认组播 239.0.0.1）")
    parser.add_argument("--port", type=int, default=11988)
    parser.add_argument("--bpm", type=float, default=100.0)
    parser.add_argument("--duration", type=float, default=20.0, help="发送时长（秒）")
    parser.add_argument("--pattern", choices=["beat", "sweep", "flat"], default="beat")
    parser.add_argument("--iface", default=None, help="组播出口网卡 IP（多网卡时需要）")
    args = parser.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 0)
    if args.iface:
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(args.iface))

    interval = 1.0 / 50.0  # 20 Hz，与固件 UDP 轮询节奏匹配
    start = time.monotonic()
    sent = 0
    print(f"send {args.pattern} -> {args.host}:{args.port} for {args.duration:.0f}s "
          f"({GEQ_CHANNELS} GEQ, {args.bpm:.0f} BPM)")
    next_tick = start
    while time.monotonic() - start < args.duration:
        now = time.monotonic()
        t = now - start
        if now < next_tick:
            time.sleep(min(next_tick - now, 0.02))
            continue
        next_tick += interval
        raw, smth, peak, geq, mag, maj = synth_levels(t, args.bpm, args.pattern)
        sock.sendto(build_packet(raw, smth, peak, geq, mag, maj), (args.host, args.port))
        sent += 1
        if sent % 50 == 0:
            print(f"  t={t:5.1f}s sent={sent:4d} level={raw:5.1f} peak={int(peak)}")
    sock.close()
    print(f"done: {sent} packets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
