# -*- coding: utf-8 -*-
"""PhoneKB v0.37.6 mockups: icon badges, bigger 设备 pill, collapsed 平板/电脑, 旋转 out of 更多, no 单手 tab."""
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
WARN    = (0xFF, 0xB0, 0x20)

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
    d.ellipse((int(cx - r), int(cy - r), int(cx + r), int(cy + r)), fill=color)

# ---------- icons (same stroke language as Icons.java) ----------
def icon_phone(d, cx, cy, s, color):
    w = max(2, int(s * 0.10))
    rr(d, (cx - s * 0.30, cy - s * 0.48, cx + s * 0.30, cy + s * 0.48), s * 0.12, outline=color, width=w)
    d.line((int(cx - s * 0.10), int(cy + s * 0.34), int(cx + s * 0.10), int(cy + s * 0.34)), fill=color, width=w)

def icon_pc(d, cx, cy, s, color):
    w = max(2, int(s * 0.10))
    rr(d, (cx - s * 0.46, cy - s * 0.46, cx + s * 0.46, cy + s * 0.20), s * 0.10, outline=color, width=w)
    d.line((int(cx - s * 0.20), int(cy + s * 0.48), int(cx + s * 0.20), int(cy + s * 0.48)), fill=color, width=w)
    d.line((int(cx), int(cy + s * 0.20), int(cx), int(cy + s * 0.48)), fill=color, width=w)

def icon_tv(d, cx, cy, s, color):
    w = max(2, int(s * 0.10))
    rr(d, (cx - s * 0.46, cy - s * 0.42, cx + s * 0.46, cy + s * 0.30), s * 0.12, outline=color, width=w)
    d.line((int(cx - s * 0.14), int(cy + s * 0.48), int(cx + s * 0.14), int(cy + s * 0.48)), fill=color, width=w)

def icon_power(d, cx, cy, s, color):
    r = s * 0.36
    d.arc((int(cx - r), int(cy - r), int(cx + r), int(cy + r)), start=300, end=240,
          fill=color, width=max(2, int(s * 0.13)))
    d.line((int(cx), int(cy - r * 1.30), int(cx), int(cy)), fill=color, width=max(2, int(s * 0.13)))

def chevron_down(d, cx, cy, s, color):
    w = max(3, int(s * 0.34))
    d.line((int(cx - s * 0.75), int(cy - s * 0.38), int(cx), int(cy + s * 0.38)), fill=color, width=w)
    d.line((int(cx), int(cy + s * 0.38), int(cx + s * 0.75), int(cy - s * 0.38)), fill=color, width=w)

# ---------- chrome pieces ----------
def power_square(d, x, y, s):
    rr(d, (x, y, x + s, y + s), s * 0.28, fill=SURF2)
    icon_power(d, x + s / 2.0, y + s / 2.0, s * 0.5, ACCENT)

def device_pill(d, x, y, w, h):
    """Enlarged 设备 entry: icon + label, accent outline so it reads as the primary action."""
    rr(d, (x, y, x + w, y + h), h * 0.34, fill=SURF2, outline=ACCENT, width=max(2, int(1.6 * K)))
    icon_tablet_icon = icon_phone
    icon_tablet_icon(d, x + h * 0.58, y + h / 2.0, h * 0.52, ACCENT)
    text(d, "设备", x + h * 1.06, y + h / 2.0, 12.5, ACCENT, bold=True, anchor="lm")

def mode_pill(d, x, y, w, h, label):
    rr(d, (x, y, x + w, y + h), h * 0.42, fill=SURF2)
    text(d, label, x + 12, y + h / 2.0, 11.5, TEXT, anchor="lm")
    chevron_down(d, x + w - 14, y + h / 2.0, 5.2, DIM)

def flat_pill(d, x, y, w, h, label, selected=False, accent=False):
    if selected:
        rr(d, (x, y, x + w, y + h), h / 2.0, fill=SURF2)
    elif accent:
        rr(d, (x, y, x + w, y + h), h / 2.0, outline=ACCENT, width=max(2, int(1.5 * K)))
    else:
        rr(d, (x, y, x + w, y + h), h / 2.0, fill=None)
    text(d, label, x + w / 2.0, y + h / 2.0, 11.5,
         ACCENT if accent else (TEXT if selected else DIM), bold=selected, anchor="mm")

def sheet(d, x0, y_top, width, rows, sub_text, footer_note):
    pad = 18 * K
    rh, gap = 52 * K, 8 * K
    body_h = 66 * K + len(rows) * (rh + gap) + 6 * K + 40 * K + 22 * K
    y0 = y_top
    y1 = y0 + body_h
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
        text(d, name, px + 60 * K, y + rh / 2.0 - 9 * K, 14.5, TEXT, bold=True)
        text(d, sub, px + 60 * K, y + rh / 2.0 + 9 * K, 10.5, DIM)
        if state == "current":
            dot(d, px + rw - 56 * K, cy, 3.4 * K, OK)
            text(d, "已连接", px + rw - 16 * K, cy, 11.5, OK, bold=True, anchor="rm")
        else:
            bw, bh = 52 * K, 26 * K
            bx, by = px + rw - bw - 14 * K, cy - bh / 2.0
            rr(d, (bx, by, bx + bw, by + bh), bh / 2.0, outline=ACCENT, width=max(2, int(1.4 * K)))
            text(d, "切换", bx + bw / 2.0, cy, 11.5, ACCENT, anchor="mm")
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

TABS = ["键盘", "触控板", "媒体", "手柄", "双手"]

def keyboard_body(d, dpw, y0, key_h, gap_y, label_size):
    rows = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
        ["z", "x", "c", "v", "b", "n", "m"],
    ]
    y = y0
    for row in rows:
        n = len(row)
        kw = (dpw * K - 24 * K - 6 * K * (n - 1)) / n
        for i, k in enumerate(row):
            kx = 12 * K + i * (kw + 6 * K)
            rr(d, (kx, y, kx + kw, y + key_h * K), 8 * K, fill=KEY)
            text(d, k, kx + kw / 2.0, y + key_h * K / 2.0, label_size, TEXT, anchor="mm")
        y += (key_h + gap_y) * K
    rr(d, (12 * K, y, dpw * K - 12 * K, y + key_h * K), 8 * K, fill=SURF2)
    text(d, "Space", dpw * K / 2.0, y + key_h * K / 2.0, label_size - 1, DIM, anchor="mm")

def portrait():
    W, H = 1080, 2400
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    dpw = W / K
    text(d, "16:23", 14 * K, 8 * K, 12, TEXT, bold=True)
    text(d, "5G   72", dpw * K - 46 * K, 8 * K, 11, TEXT, anchor="ra")

    # row 1: power | 设备(big) | status | 模式(折叠) | 更多
    power_square(d, 12 * K, 30 * K, 40 * K)
    device_pill(d, 58 * K, 30 * K, 86 * K, 40 * K)
    dot(d, 156 * K, 46 * K, 4 * K, OK)
    text(d, "已连接", 166 * K, 34 * K, 13.5, TEXT, bold=True)
    text(d, "L", 166 * K, 52 * K, 10, DIM)
    flat_pill(d, dpw * K - 12 * K - 40 * K, 34 * K, 40 * K, 24 * K, "更多")
    mode_pill(d, dpw * K - 12 * K - 40 * K - 6 * K - 66 * K, 30 * K, 66 * K, 32 * K, "电脑")

    # row 2: five tabs + 旋转 on the right
    tx = 12 * K
    for i, t in enumerate(TABS):
        tw = (46 if len(t) == 2 else 58) * K
        if i == 0:
            rr(d, (tx, 78 * K, tx + tw, 104 * K), 12 * K, fill=SURF2)
            text(d, t, tx + tw / 2.0, 91 * K, 12.5, TEXT, bold=True, anchor="mm")
        else:
            text(d, t, tx + tw / 2.0, 91 * K, 12.5, DIM, anchor="mm")
        tx += tw + 8 * K
    flat_pill(d, dpw * K - 12 * K - 46 * K, 78 * K, 46 * K, 26 * K, "旋转")

    # keyboard page
    rr(d, (12 * K, 112 * K, dpw * K - 12 * K, 124 * K), 8 * K, fill=SURF2)
    text(d, "切换布局", 24 * K, 118 * K, 10.5, TEXT, anchor="lm")
    keyboard_body(d, dpw, 134 * K, 34, 6, 13)

    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.rectangle((0, 0, W, H), fill=(4, 6, 9, 178))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(img)

    rows = [
        (icon_phone, "iQOO Pad2", "平板 / 手机 · 7 分钟前", "current"),
        (icon_pc, "L", "电脑 · 刚刚", "switch"),
        (icon_phone, "OPhone", "平板 / 手机 · 没连过", "switch"),
    ]
    sheet_top = H - 8 * K - (66 * K + len(rows) * (52 * K + 8 * K) + 6 * K + 40 * K + 22 * K)
    sheet(d, 8 * K, sheet_top, dpw * K - 16 * K, rows,
          "已保存 3 台 · 点一下就能换，不用重新配对",
          "蓝牙 HID 一次只能连一台主机；切换时会先松开所有按住的键")
    img.save(os.path.join(ROOT, "mock-dev2-portrait.png"))
    print("saved mock-dev2-portrait.png", img.size)

def landscape():
    W, H = 2400, 1080
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    dpw = W / K
    power_square(d, 12 * K, 10 * K, 32 * K)
    device_pill(d, 50 * K, 10 * K, 80 * K, 32 * K)
    dot(d, 142 * K, 26 * K, 3.4 * K, OK)
    text(d, "L", 150 * K, 26 * K, 13, TEXT, bold=True, anchor="lm")
    x = 176 * K
    mode_pill(d, x, 10 * K, 66 * K, 32 * K, "电脑")
    x += 66 * K + 14 * K
    for t in TABS:
        tw = (58 if len(t) == 3 else 46) * K
        flat_pill(d, x, 12 * K, tw, 26 * K, t, selected=(t == "键盘"))
        x += tw + 6 * K
    flat_pill(d, dpw * K - 178 * K, 12 * K, 46 * K, 26 * K, "旋转")
    flat_pill(d, dpw * K - 126 * K, 12 * K, 46 * K, 26 * K, "帮助")
    flat_pill(d, dpw * K - 72 * K, 12 * K, 46 * K, 26 * K, "更多")
    keyboard_body(d, dpw, 52 * K, 26, 5, 12)
    img.save(os.path.join(ROOT, "mock-dev2-landscape.png"))
    print("saved mock-dev2-landscape.png", img.size)

portrait()
landscape()
