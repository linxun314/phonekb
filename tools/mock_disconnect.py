# -*- coding: utf-8 -*-
"""PhoneKB v0.37.7 mockups: 断开连接 button on the currently-connected device row (two variants)."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
BG      = (0x0F, 0x11, 0x15)
SURFACE = (0x17, 0x1B, 0x22)
SURF2   = (0x1F, 0x25, 0x30)
KEY     = (0x26, 0x2D, 0x3A)
ACCENT  = (0x4C, 0x8D, 0xFF)
TEXT    = (0xE9, 0xEC, 0xF2)
DIM     = (0x98, 0xA1, 0xB3)
OK      = (0x3D, 0xDC, 0x84)
RED     = (0xFF, 0x6B, 0x6B)

FONT_R = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"
_cache = {}
def font(size_dp, bold=False):
    key = (round(size_dp, 2), bold)
    if key not in _cache:
        _cache[key] = ImageFont.truetype(FONT_B if bold else FONT_R, int(round(size_dp * K)))
    return _cache[key]

def blend(c1, c2, t):
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(c1, c2))

def rr(d, box, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(round(radius)), fill=fill,
                        outline=outline, width=int(width) if outline else 0)

def text(d, s, x, y, size_dp, color, bold=False, anchor="la"):
    d.text((int(round(x)), int(round(y))), s, font=font(size_dp, bold), fill=color, anchor=anchor)

def dot(d, cx, cy, r, color):
    d.ellipse((int(round(cx - r)), int(round(cy - r)), int(round(cx + r)), int(round(cy + r))), fill=color)

def icon_phone(d, cx, cy, s, color):
    w = max(2, int(s * 0.10))
    rr(d, (cx - s * 0.30, cy - s * 0.48, cx + s * 0.30, cy + s * 0.48), s * 0.12, outline=color, width=w)
    d.line((int(cx - s * 0.10), int(cy + s * 0.34), int(cx + s * 0.10), int(cy + s * 0.34)), fill=color, width=w)

def icon_pc(d, cx, cy, s, color):
    w = max(2, int(s * 0.10))
    rr(d, (cx - s * 0.46, cy - s * 0.46, cx + s * 0.46, cy + s * 0.20), s * 0.10, outline=color, width=w)
    d.line((int(cx - s * 0.20), int(cy + s * 0.48), int(cx + s * 0.20), int(cy + s * 0.48)), fill=color, width=w)
    d.line((int(cx), int(cy + s * 0.20), int(cx), int(cy + s * 0.48)), fill=color, width=w)

def sheet(d, x0, y_top, width, rows, sub_text, footer_note, variant, rh_dp):
    pad = 18 * K
    rh, gap = rh_dp * K, 8 * K
    body_h = 66 * K + len(rows) * (rh + gap) + 6 * K + 40 * K + 22 * K
    y0, y1 = y_top, y_top + body_h
    rr(d, (x0, y0, x0 + width, y1), 22 * K, fill=blend(SURFACE, SURF2, 0.35))
    hx = x0 + width / 2.0
    rr(d, (hx - 20 * K, y0 + 9 * K, hx + 20 * K, y0 + 13 * K), 2 * K, fill=blend(SURF2, TEXT, 0.25))
    px = x0 + pad
    text(d, "连接设备", px, y0 + 22 * K, 17.5, TEXT, bold=True)
    text(d, sub_text, px, y0 + 44 * K, 10.5, DIM)
    y = y0 + 62 * K
    rw = width - pad * 2
    for icon, name, sub, state in rows:
        if state == "current":
            rr(d, (px, y, px + rw, y + rh), 16 * K, fill=blend(SURF2, ACCENT, 0.20),
               outline=ACCENT, width=max(2, int(1.6 * K)))
        else:
            rr(d, (px, y, px + rw, y + rh), 16 * K, fill=SURF2)
        cx, cy = px + 32 * K, y + rh / 2.0
        dot(d, cx, cy, 18 * K, blend(SURF2, BG, 0.45))
        icon(d, cx, cy, 17 * K, ACCENT if state == "current" else DIM)
        tex = px + 60 * K
        if state == "current":
            bw, bh = 58 * K, 26 * K
            bx, by = px + rw - bw - 14 * K, cy - bh / 2.0
            if variant == "A":
                # dot + name on line 1, 断开 pill right, sub line under
                dot(d, tex + 3.2 * K, cy - 9 * K, 3.2 * K, OK)
                text(d, name, tex + 12 * K, cy - 9 * K, 14.5, TEXT, bold=True, anchor="lm")
                text(d, sub, tex, cy + 9 * K, 10.5, DIM, anchor="lm")
            else:
                text(d, name, tex, cy - 9 * K, 14.5, TEXT, bold=True, anchor="lm")
                text(d, sub, tex, cy + 9 * K, 10.5, DIM, anchor="lm")
                dot(d, bx - 8 * K, by - 0, 0.1, OK)
            if variant == "A":
                rr(d, (bx, by, bx + bw, by + bh), bh / 2.0, outline=RED, width=max(2, int(1.4 * K)))
                text(d, "断开", bx + bw / 2.0, by + bh / 2.0, 11.5, RED, anchor="mm")
            else:
                # stacked: 已连接 (green) over 断开 pill
                text(d, "已连接", bx + bw - 2 * K, cy - 13 * K, 10.5, OK, bold=True, anchor="rm")
                rr(d, (bx, by + 4 * K, bx + bw, by + 4 * K + bh), bh / 2.0, outline=RED, width=max(2, int(1.4 * K)))
                text(d, "断开", bx + bw / 2.0, by + 4 * K + bh / 2.0, 11.5, RED, anchor="mm")
        else:
            text(d, name, tex, cy - 9 * K, 14.5, TEXT, bold=True, anchor="lm")
            text(d, sub, tex, cy + 9 * K, 10.5, DIM, anchor="lm")
            bw, bh = 52 * K, 26 * K
            bx, by = px + rw - bw - 14 * K, cy - bh / 2.0
            rr(d, (bx, by, bx + bw, by + bh), bh / 2.0, outline=ACCENT, width=max(2, int(1.4 * K)))
            text(d, "切换", bx + bw / 2.0, by + bh / 2.0, 11.5, ACCENT, anchor="mm")
        y += rh + gap
    by = y + 6 * K
    bh = 40 * K
    half = (rw - 10 * K) / 2.0
    rr(d, (px, by, px + half, by + bh), 14 * K, fill=SURF2)
    text(d, "刷新列表", px + half / 2.0, by + bh / 2.0, 12, TEXT, anchor="mm")
    rr(d, (px + half + 10 * K, by, px + rw, by + bh), 14 * K, outline=ACCENT, width=max(2, int(1.5 * K)))
    text(d, "配对新设备…", px + half + 10 * K + half / 2.0, by + bh / 2.0, 12, ACCENT, anchor="mm")
    text(d, footer_note, px, by + bh + 8 * K, 9.5, DIM)
    return y1

ROWS = [
    (icon_pc, "L", "电脑 · 8 分钟前", "current"),
    (icon_phone, "iQOO Pad2", "平板 / 手机 · 8 分钟前", "switch"),
]

def sheet_only(fname, variant, note):
    W = 900
    H = 900
    img = Image.new("RGB", (W, H), (0x0A, 0x0C, 0x10))
    d = ImageDraw.Draw(img)
    width = W - 24 * K * 0.5
    sheet(d, 12 * K, 40 * K, W - 24 * K, ROWS, "已保存 2 台 · 点一下就能换，不用重新配对",
          "蓝牙 HID 一次只能连一台主机；切换时会先松开所有按住的键", variant, 52)
    text(d, note, W / 2.0, 14 * K, 11, DIM, anchor="ma")
    out = os.path.join(ROOT, fname)
    img.save(out)
    print("OK", out, img.size)

sheet_only("mock-dc-a.png", "A", "方案 A · 已连接行右侧放「断开」（绿点标在名字前）")
sheet_only("mock-dc-b.png", "B", "方案 B · 已连接行右侧上行「已连接」、下行「断开」")
