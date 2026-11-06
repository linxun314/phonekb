
# -*- coding: utf-8 -*-
"""PhoneKB device-switch mockups (portrait bottom sheet + landscape dialog)."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
BG      = (0x0F, 0x11, 0x15)
SURFACE = (0x17, 0x1B, 0x22)
SURF2   = (0x1F, 0x25, 0x30)
KEY     = (0x26, 0x2D, 0x3A)
KEYP    = (0x3B, 0x49, 0x68)
ACCENT  = (0x4C, 0x8D, 0xFF)
TEXT    = (0xE9, 0xEC, 0xF2)
DIM     = (0x98, 0xA1, 0xB3)
OK      = (0x3D, 0xDC, 0x84)
ERR     = (0xFF, 0x5D, 0x6A)
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

# ---------- icons ----------
def icon_tablet(d, cx, cy, s, color):
    rr(d, (cx - s * 0.42, cy - s * 0.58, cx + s * 0.42, cy + s * 0.58), s * 0.14, outline=color, width=max(2, int(s * 0.11)))
    dot(d, cx, cy + s * 0.40, s * 0.055, color)

def icon_pc(d, cx, cy, s, color):
    w = max(2, int(s * 0.11))
    rr(d, (cx - s * 0.5, cy - s * 0.5, cx + s * 0.5, cy + s * 0.22), s * 0.10, outline=color, width=w)
    d.line((int(cx - s * 0.22), int(cy + s * 0.52), int(cx + s * 0.22), int(cy + s * 0.52)), fill=color, width=w)
    d.line((int(cx), int(cy + s * 0.22), int(cx), int(cy + s * 0.52)), fill=color, width=w)

def icon_tv(d, cx, cy, s, color):
    w = max(2, int(s * 0.11))
    rr(d, (cx - s * 0.5, cy - s * 0.46, cx + s * 0.5, cy + s * 0.34), s * 0.12, outline=color, width=w)
    d.line((int(cx - s * 0.16), int(cy + s * 0.52), int(cx + s * 0.16), int(cy + s * 0.52)), fill=color, width=w)

def pill(d, x, y, w, h, label, size_dp=11.5, selected=False, accent=False, radius=None):
    r = radius if radius is not None else h / 2.0
    if selected:
        rr(d, (x, y, x + w, y + h), r, fill=SURF2, outline=None)
    elif accent:
        rr(d, (x, y, x + w, y + h), r, fill=None, outline=ACCENT, width=max(2, int(1.6 * K)))
    else:
        rr(d, (x, y, x + w, y + h), r, fill=None)
    text(d, label, x + w / 2.0, y + h / 2.0, size_dp,
         ACCENT if accent else (TEXT if selected else DIM), bold=selected, anchor="mm")

def power_pill(d, x, y, s):
    rr(d, (x, y, x + s, y + s), s * 0.28, fill=SURF2)
    cx, cy, r = x + s / 2.0, y + s / 2.0, s * 0.17
    d.arc((int(cx - r), int(cy - r), int(cx + r), int(cy + r)), start=300, end=240,
          fill=ACCENT, width=max(2, int(2.0 * K)))
    d.line((int(cx), int(cy - r * 1.35), int(cx), int(cy)), fill=ACCENT, width=max(2, int(2.0 * K)))

# ---------- device row ----------
def device_row(d, x, y, w, h, icon, name, sub, state, state_color):
    if state == "current":
        rr(d, (x, y, x + w, y + h), 16 * K, fill=blend(SURF2, ACCENT, 0.20), outline=ACCENT, width=max(2, int(1.6 * K)))
    else:
        rr(d, (x, y, x + w, y + h), 16 * K, fill=SURF2)
    cx, cy = x + 30 * K, y + h / 2.0
    dot(d, cx, cy, 17 * K, blend(SURF2, BG, 0.45))
    icon(d, cx, cy, 16 * K, ACCENT if state == "current" else DIM)
    text(d, name, x + 56 * K, y + h / 2.0 - 9 * K, 14.5, TEXT, bold=True)
    text(d, sub, x + 56 * K, y + h / 2.0 + 8 * K, 10.5, DIM)
    if state == "current":
        dot(d, x + w - 54 * K, cy, 3.4 * K, OK)
        text(d, "已连接", x + w - 16 * K, cy, 11.5, OK, bold=True, anchor="rm")
    elif state == "switch":
        bw, bh = 52 * K, 26 * K
        bx, by = x + w - bw - 14 * K, cy - bh / 2.0
        rr(d, (bx, by, bx + bw, by + bh), bh / 2.0, outline=ACCENT, width=max(2, int(1.4 * K)))
        text(d, "切换", bx + bw / 2.0, cy, 11.5, ACCENT, anchor="mm")
    else:
        text(d, "未配对", x + w - 16 * K, cy, 11.5, WARN, anchor="rm")

def sheet(d, x0, y_top, width, rows, title_sub, footer_note):
    """Card sized from its content: header + rows + two buttons + note."""
    pad = 18 * K
    rh, gap = 46 * K, 8 * K
    body_h = 66 * K + len(rows) * (rh + gap) + 6 * K + 40 * K + 22 * K
    y0 = y_top
    y1 = y0 + body_h
    rr(d, (x0, y0, x0 + width, y1), 22 * K, fill=blend(SURFACE, SURF2, 0.35))
    hx = x0 + width / 2.0
    rr(d, (hx - 20 * K, y0 + 9 * K, hx + 20 * K, y0 + 13 * K), 2 * K, fill=blend(SURF2, TEXT, 0.25))
    px = x0 + pad
    text(d, "连接设备", px, y0 + 22 * K, 17.5, TEXT, bold=True)
    text(d, title_sub, px, y0 + 44 * K, 10.5, DIM)
    y = y0 + 62 * K
    rw = width - pad * 2
    for icon, name, sub, state, color in rows:
        device_row(d, px, y, rw, rh, icon, name, sub, state, color)
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

# ---------- portrait screen ----------
def portrait():
    W, H = int(round(1080)), int(round(2400))
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    dpw = W / K

    # status bar hint
    text(d, "16:23", 14 * K, 8 * K, 12, TEXT, bold=True)
    text(d, "5G   72", dpw * K - 46 * K, 8 * K, 11, TEXT, anchor="ra")

    power_pill(d, 12 * K, 30 * K, 44 * K)
    dot(d, 70 * K, 44 * K, 4 * K, OK)
    text(d, "已连接", 82 * K, 34 * K, 14, TEXT, bold=True)
    text(d, "iQOO Pad2", 82 * K, 52 * K, 10.5, DIM)

    # device pills (new: 设备)
    pw, ph, gap = 40 * K, 24 * K, 6 * K
    x = dpw * K - 12 * K - (pw * 3 + gap * 2)
    pill(d, x, 34 * K, pw, ph, "平板")
    pill(d, x + pw + gap, 34 * K, pw, ph, "电脑")
    pill(d, x + (pw + gap) * 2, 34 * K, pw, ph, "设备", selected=True)

    # tabs
    tabs = ["键盘", "触控板", "媒体", "手柄", "双手", "单体"]
    tabs[5] = "单手"
    tx = 12 * K
    for i, t in enumerate(tabs):
        tw = (46 if len(t) == 2 else 58) * K
        if i == 0:
            rr(d, (tx, 70 * K, tx + tw, 96 * K), 12 * K, fill=SURF2)
            text(d, t, tx + tw / 2.0, 83 * K, 12.5, TEXT, bold=True, anchor="mm")
        else:
            text(d, t, tx + tw / 2.0, 83 * K, 12.5, DIM, anchor="mm")
        tx += tw + 8 * K

    # keyboard page (simplified)
    rr(d, (12 * K, 106 * K, dpw * K - 12 * K, 118 * K), 8 * K, fill=SURF2)
    text(d, "切换布局", 24 * K, 112 * K, 10.5, TEXT, anchor="lm")
    key_rows = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
        ["z", "x", "c", "v", "b", "n", "m"],
    ]
    y = 130 * K
    for row in key_rows:
        n = len(row)
        kw = (dpw * K - 24 * K - 6 * K * (n - 1)) / n
        for i, k in enumerate(row):
            kx = 12 * K + i * (kw + 6 * K)
            rr(d, (kx, y, kx + kw, y + 34 * K), 8 * K, fill=KEY)
            text(d, k, kx + kw / 2.0, y + 17 * K, 13, TEXT, anchor="mm")
        y += 40 * K
    # space row
    rr(d, (12 * K, y, dpw * K - 12 * K, y + 34 * K), 8 * K, fill=SURF2)
    text(d, "Space", dpw * K / 2.0, y + 17 * K, 12, DIM, anchor="mm")

    # scrim
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.rectangle((0, 0, W, H), fill=(4, 6, 9, 178))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(img)

    rows = [
        (icon_tablet, "iQOO Pad2", "平板 · 现在连着", "current", OK),
        (icon_pc, "DESKTOP-7F2K", "电脑 · 昨天 21:40", "switch", ACCENT),
        (icon_tv, "客厅电视", "其他 · 3 天前", "switch", ACCENT),
    ]
    sheet_top = H - 8 * K - (66 * K + len(rows) * (46 * K + 8 * K) + 6 * K + 40 * K + 22 * K)
    sheet(d, 8 * K, sheet_top, dpw * K - 16 * K, rows,
          "已保存 3 台 · 点一下就能换，不用重新配对",
          "蓝牙 HID 一次只能连一台主机；切换时会先松开所有按住的键")
    img.save(os.path.join(ROOT, "mock-dev-portrait.png"))
    print("saved mock-dev-portrait.png", img.size)

# ---------- landscape dialog ----------
def landscape():
    W, H = 2400, 1080
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    dpw = W / K
    # compact chrome
    power_pill(d, 12 * K, 10 * K, 30 * K)
    dot(d, 56 * K, 25 * K, 3.4 * K, OK)
    text(d, "iQOO Pad2", 66 * K, 25 * K, 13, TEXT, bold=True, anchor="lm")
    x = 142 * K
    for t in ["平板", "电脑"]:
        pill(d, x, 12 * K, 44 * K, 24 * K, t, size_dp=11)
        x += 44 * K + 6 * K
    pill(d, x, 12 * K, 48 * K, 24 * K, "设备", size_dp=11, selected=True)
    x += 48 * K + 14 * K
    for t in ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]:
        tw = (58 if len(t) == 3 else 46) * K
        pill(d, x, 12 * K, tw, 24 * K, t, size_dp=11, selected=(t == "键盘"))
        x += tw + 6 * K
    pill(d, dpw * K - 120 * K, 12 * K, 46 * K, 24 * K, "旋转")
    pill(d, dpw * K - 66 * K, 12 * K, 46 * K, 24 * K, "帮助")

    # keyboard page body
    key_rows = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
        ["z", "x", "c", "v", "b", "n", "m"],
    ]
    y = 48 * K
    for row in key_rows:
        n = len(row)
        kw = (dpw * K - 24 * K - 6 * K * (n - 1)) / n
        for i, k in enumerate(row):
            kx = 12 * K + i * (kw + 6 * K)
            rr(d, (kx, y, kx + kw, y + 26 * K), 7 * K, fill=KEY)
            text(d, k, kx + kw / 2.0, y + 13 * K, 12, TEXT, anchor="mm")
        y += 31 * K

    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.rectangle((0, 0, W, H), fill=(4, 6, 9, 182))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(img)

    cw = 300 * K
    ch = 66 * K + 3 * (46 * K + 8 * K) + 6 * K + 40 * K + 22 * K
    cx0, cy0 = (dpw * K - cw) / 2.0, (H - ch) / 2.0
    rows = [
        (icon_tablet, "iQOO Pad2", "平板 · 现在连着", "current", OK),
        (icon_pc, "DESKTOP-7F2K", "电脑 · 昨天 21:40", "switch", ACCENT),
        (icon_tv, "客厅电视", "其他 · 3 天前", "switch", ACCENT),
    ]
    sheet(d, cx0, cy0, cw, rows,
          "已保存 3 台 · 点一下就能换，不用重新配对",
          "蓝牙 HID 一次只能连一台；切换时会先松开按住的键")
    img.save(os.path.join(ROOT, "mock-dev-landscape.png"))
    print("saved mock-dev-landscape.png", img.size)

portrait()
landscape()