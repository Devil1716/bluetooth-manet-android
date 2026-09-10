#!/usr/bin/env python3
"""Generate Mesh launcher PNGs without third-party libraries."""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
TEAL = (0x9E, 0xCF, 0xC4, 255)
BLACK = (0, 0, 0, 255)
TRANSPARENT = (0, 0, 0, 0)


def png_bytes(width, height, rgba):
    raw = bytearray()
    row = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(rgba[y * row : (y + 1) * row])
    def chunk(tag, data):
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def blend(dst, src):
    sa = src[3] / 255.0
    if sa <= 0:
        return dst
    out_a = src[3] + dst[3] * (1 - sa)
    if out_a <= 0:
        return TRANSPARENT
    def ch(i):
        return int((src[i] * src[3] + dst[i] * dst[3] * (1 - sa)) / out_a)
    return (ch(0), ch(1), ch(2), int(out_a))


def set_px(buf, size, x, y, color):
    if 0 <= x < size and 0 <= y < size:
        i = (y * size + x) * 4
        cur = (buf[i], buf[i + 1], buf[i + 2], buf[i + 3])
        r, g, b, a = blend(cur, color)
        buf[i], buf[i + 1], buf[i + 2], buf[i + 3] = r, g, b, a


def fill_circle(buf, size, cx, cy, radius, color):
    r2 = radius * radius
    x0, x1 = int(cx - radius - 1), int(cx + radius + 2)
    y0, y1 = int(cy - radius - 1), int(cy + radius + 2)
    for y in range(y0, y1):
        for x in range(x0, x1):
            d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
            if d2 <= r2:
                set_px(buf, size, x, y, color)
            elif d2 <= (radius + 0.65) ** 2:
                t = 1 - (math.sqrt(d2) - radius)
                if t > 0:
                    set_px(buf, size, x, y, (color[0], color[1], color[2], int(color[3] * t)))


def fill_round_rect(buf, size, x0, y0, x1, y1, radius, color):
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            dx = 0
            dy = 0
            if x < x0 + radius:
                dx = x0 + radius - x
            elif x > x1 - radius:
                dx = x - (x1 - radius)
            if y < y0 + radius:
                dy = y0 + radius - y
            elif y > y1 - radius:
                dy = y - (y1 - radius)
            if dx * dx + dy * dy <= radius * radius or (dx == 0 or dy == 0):
                set_px(buf, size, x, y, color)


def draw_icon(size, round_mask=False):
    buf = bytearray(size * size * 4)
    bg = TEAL if round_mask else BLACK
    if round_mask:
        fill_circle(buf, size, size / 2, size / 2, size / 2 - 0.5, TEAL)
    else:
        for i in range(0, len(buf), 4):
            buf[i:i + 4] = bytes(BLACK)
    cx = cy = size / 2
    bubble_r = size * 0.38
    fill_circle(buf, size, cx, cy - size * 0.02, bubble_r, TEAL if not round_mask else BLACK)
    # tail
    tail_x = cx - bubble_r * 0.55
    tail_y = cy + bubble_r * 0.55
    fill_circle(buf, size, tail_x, tail_y, size * 0.09, TEAL if not round_mask else BLACK)
    # inner bubble cut for round (teal bg) already black; for square add black inner? 
    # Square icon: teal bubble on black. Round: black bubble on teal.
    inner = BLACK if not round_mask else None
    if inner is None:
        # punch a smaller teal? already black on teal — add a small teal highlight hole? skip
        pass
    else:
        # carve message lines as black already is the bubble; add two black? wait bubble is teal
        # draw two line "text" in black inside teal bubble
        line_color = BLACK
        w = size * 0.28
        h = max(1, int(size * 0.045))
        y1 = cy - size * 0.06
        y2 = cy + size * 0.04
        x0 = cx - w / 2
        for y in range(int(y1), int(y1) + h + 1):
            for x in range(int(x0), int(x0 + w)):
                set_px(buf, size, x, y, line_color)
        for y in range(int(y2), int(y2) + h + 1):
            for x in range(int(x0), int(x0 + w * 0.65)):
                set_px(buf, size, x, y, line_color)
    if round_mask:
        w = size * 0.26
        h = max(1, int(size * 0.045))
        y1 = cy - size * 0.06
        y2 = cy + size * 0.04
        x0 = cx - w / 2
        for y in range(int(y1), int(y1) + h + 1):
            for x in range(int(x0), int(x0 + w)):
                set_px(buf, size, x, y, TEAL)
        for y in range(int(y2), int(y2) + h + 1):
            for x in range(int(x0), int(x0 + w * 0.65)):
                set_px(buf, size, x, y, TEAL)
    return buf


def write(path, size, round_mask):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png_bytes(size, size, draw_icon(size, round_mask)))


def main():
    sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    root = os.path.abspath(ROOT)
    for density, size in sizes.items():
        write(os.path.join(root, f"mipmap-{density}", "ic_launcher.png"), size, False)
        write(os.path.join(root, f"mipmap-{density}", "ic_launcher_round.png"), size, True)
        print("wrote", density, size)


if __name__ == "__main__":
    main()
