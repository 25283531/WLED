"""纯 stdlib 的 PNG 裁剪+放大（无 PIL 依赖）。

Android 模拟器截图常需局部放大核对细节，CI/本机都可能没有 Pillow。

用法: python png_crop.py <输入png> <输出png> <cx> <cy> <半宽> <半高> [放大倍数]
坐标以输入图原始像素为准（中心点 cx,cy）。
"""
import struct
import sys
import zlib


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a png"
    pos = 8
    idat = b""
    w = h = bitd = ctype = None
    plte = None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, bitd, ctype = struct.unpack(">IIBB", body[:10])
        elif typ == b"PLTE":
            plte = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
        pos += 12 + ln
    assert bitd == 8, f"only 8-bit supported, got {bitd}"
    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * nch
    out = bytearray(w * h * nch)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(nch, stride):
                line[i] = (line[i] + line[i - nch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                b = prev[i]
                c = prev[i - nch] if i >= nch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, nch, ctype, bytes(out), plte


def write_png(path, w, h, nch, ctype, pix):
    # nch -> PNG color type
    ctype_map = {1: 0, 2: 4, 3: 2, 4: 6}
    out_ctype = ctype_map[nch]
    raw = bytearray()
    stride = w * nch
    for y in range(h):
        raw.append(0)
        raw += pix[y * stride:(y + 1) * stride]

    def chunk(t, d):
        return (struct.pack(">I", len(d)) + t + d
                + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", w, h, 8, out_ctype, 0, 0, 0)
    open(path, "wb").write(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b"")
    )


def main():
    src, dst = sys.argv[1], sys.argv[2]
    cx, cy, half_w, half_h = map(int, sys.argv[3:7])
    scale = int(sys.argv[7]) if len(sys.argv) > 7 else 1

    w, h, nch, ctype, pix, _ = read_png(src)
    x0 = max(0, min(w - 1, cx - half_w))
    x1 = max(1, min(w, cx + half_w))
    y0 = max(0, min(h - 1, cy - half_h))
    y1 = max(1, min(h, cy + half_h))
    cw, ch = x1 - x0, y1 - y0

    ow, oh = cw * scale, ch * scale
    out = bytearray(ow * oh * nch)
    for y in range(oh):
        sy = y0 + y // scale
        srow = sy * w * nch
        drow = y * ow * nch
        for x in range(ow):
            sx = x0 + x // scale
            si = srow + sx * nch
            di = drow + x * nch
            out[di:di + nch] = pix[si:si + nch]

    write_png(dst, ow, oh, nch, ctype, bytes(out))
    print(f"{src} ({w}x{h}) -> {dst} ({ow}x{oh}) from box=({x0},{y0})-({x1},{y1})")


if __name__ == "__main__":
    main()
