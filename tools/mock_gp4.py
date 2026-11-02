# -*- coding: utf-8 -*-
"""Render gamepad layout mockups for v0.22 (Xbox-style layout + bigger, lower touch panel)."""
from PIL import Image, ImageDraw, ImageFont
import math

K = 2.75
FONT = "C:/Windows/Fonts/msyh.ttc"
FONTB = "C:/Windows/Fonts/msyhbd.ttc"

BG = (15, 17, 21)
SURF = (23, 27, 34)
SURF2 = (31, 37, 48)
KEY = (38, 45, 58)
TEXT = (233, 236, 242)
DIM = (152, 161, 179)
ACCENT = (76, 141, 255)
OK = (61, 220, 132)
X_C = (34, 64, 95)
Y_C = (90, 71, 22)
A_C = (31, 81, 54)
B_C = (90, 38, 43)
PANEL_LINE = (58, 68, 90)


def P(v):
    return int(round(v * K))


def font(size, bold=False):
    return ImageFont.truetype(FONTB if bold else FONT, P(size))


def rr(d, box, r, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = [P(v) for v in box]
    d.rounded_rectangle([x0, y0, x1, y1], radius=P(r), fill=fill, outline=outline, width=max(1, P(width)))


def txt(d, s, x, y, size=13, color=TEXT, bold=False, anchor="la"):
    d.text((P(x), P(y)), s, font=font(size, bold), fill=color, anchor=anchor)


def chev(d, cx, cy, ang, size, color=TEXT, w=5):
    base = [(-0.46, 0.30), (0.0, -0.36), (0.46, 0.30)]
    a = math.radians(ang)
    pts = []
    for bx, by in base:
        x = bx * math.cos(a) - by * math.sin(a)
        y = bx * math.sin(a) + by * math.cos(a)
        pts.append((P(cx + x * size), P(cy + y * size)))
    d.line(pts, fill=color, width=max(2, P(w)), joint="curve")


def power(d, cx, cy, r):
    d.arc([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], 300, 240, fill=TEXT, width=max(2, P(1.8)))
    d.line([P(cx), P(cy - r - 2), P(cx), P(cy - r * 0.15)], fill=TEXT, width=max(2, P(1.8)))


def status_bar(d, W):
    txt(d, "10:47", 14, 8, 12)
    rr(d, (W - 34, 9, W - 16, 21), 3, outline=DIM, width=1)
    rr(d, (W - 32, 11, W - 21, 19), 1, fill=OK)


def portrait_chrome(d, W):
    status_bar(d, W)
    rr(d, (14, 30, 62, 78), 14, fill=SURF2)
    power(d, 38, 54, 12)
    d.ellipse([P(66), P(50), P(74), P(58)], fill=OK)
    txt(d, "已连接", 80, 44, 14, bold=True)
    txt(d, "iQOO Pad2", 80, 62, 10, DIM)
    for label, x in (("旋转", W - 168), ("伴侣", W - 112), ("帮助", W - 56)):
        rr(d, (x, 36, x + 50, 70), 10, fill=SURF2)
        txt(d, label, x + 25, 53, 11, anchor="mm")
    names = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
    cell = (W - 28) / 6.0
    for i, n in enumerate(names):
        x = 14 + cell * i
        if i == 3:
            rr(d, (x, 86, x + cell - 6, 120), 10, fill=SURF2)
        txt(d, n, x + (cell - 6) / 2, 103, 12, TEXT if i == 3 else DIM, bold=(i == 3), anchor="mm")


def landscape_chrome(d, W):
    rr(d, (14, 6, 62, 54), 14, fill=SURF2)
    power(d, 38, 30, 12)
    d.ellipse([P(66), P(26), P(74), P(34)], fill=OK)
    txt(d, "已连接 · iQOO Pad2", 80, 30, 13, bold=True, anchor="lm")
    names = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
    x = 300
    for i, n in enumerate(names):
        if i == 3:
            rr(d, (x - 10, 16, x + 40, 46), 10, fill=SURF2)
        txt(d, n, x + 15, 31, 12, TEXT if i == 3 else DIM, bold=(i == 3), anchor="mm")
        x += 58
    for label, x2 in (("旋转", W - 168), ("伴侣", W - 112), ("帮助", W - 56)):
        rr(d, (x2, 14, x2 + 50, 48), 10, fill=SURF2)
        txt(d, label, x2 + 25, 31, 11, anchor="mm")


def chip(d, x, y, label, w=78, active=False):
    rr(d, (x, y, x + w, y + 27), 9, fill=SURF2, outline=ACCENT if active else None, width=1.2)
    txt(d, label, x + 12, y + 13, 11, ACCENT if active else TEXT, anchor="lm")
    chev(d, x + w - 14, y + 13, 180, 7, DIM, 1.6)


def pill(d, box, label, size=11):
    rr(d, box, 9, fill=SURF2)
    txt(d, label, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2, size, anchor="mm")


def dpad(d, cx, cy, s, arm):
    for (dx, dy, ang) in ((0, -arm, 0), (arm, 0, 90), (0, arm, 180), (-arm, 0, 270)):
        rr(d, (cx + dx - s / 2, cy + dy - s / 2, cx + dx + s / 2, cy + dy + s / 2), 12, fill=KEY)
        chev(d, cx + dx, cy + dy, ang, s * 0.30, TEXT, s * 0.075)


def abxy(d, cx, cy, r, off, size=None):
    s = size or r * 0.62
    for lab, c, dx, dy in (("Y", Y_C, 0, -off), ("X", X_C, -off, 0), ("B", B_C, off, 0), ("A", A_C, 0, off)):
        d.ellipse([P(cx + dx - r), P(cy + dy - r), P(cx + dx + r), P(cy + dy + r)], fill=c)
        txt(d, lab, cx + dx, cy + dy, s, TEXT, bold=True, anchor="mm")


def touch_panel(d, box, compact=False):
    rr(d, box, 16, fill=(19, 23, 30), outline=PANEL_LINE, width=1.5)
    x0, y0, x1, y1 = box
    rr(d, (x0 + 10, y0 + 8, x0 + 74, y0 + 30), 8, fill=(30, 36, 46))
    txt(d, "触控板", x0 + 42, y0 + 19, 9, DIM, anchor="mm")
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2
    d.ellipse([P(cx - 9), P(cy - 9), P(cx + 9), P(cy + 9)], outline=DIM, width=max(1, P(1.2)))
    d.ellipse([P(cx - 3), P(cy - 3), P(cx + 3), P(cy + 3)], fill=DIM)
    txt(d, "触控区", cx, cy + 16, 12, DIM, anchor="ma")
    if not compact:
        txt(d, "单指移动 · 轻点＝左键 · 双指点击＝右键 · 双指滑＝滚动", cx, cy + 32, 9, (110, 120, 138), anchor="ma")


def caption(d, W, page_h, lines):
    y = page_h + 12
    for i, s in enumerate(lines):
        txt(d, s, W / 2, y + 14 * i, 10, (124, 134, 152), anchor="ma")


def landscape(kind, path):
    W, H = 873, 393
    im = Image.new("RGB", (P(W), P(H + 44)), BG)
    d = ImageDraw.Draw(im)
    landscape_chrome(d, W)
    top = 56
    vh = H - top

    if kind == "cur":
        pill(d, (16, top + 10, 112, top + 48), "L1")
        pill(d, (W - 112, top + 10, W - 16, top + 48), "R1")
        pill(d, (W / 2 - 76, top + 14, W / 2 - 8, top + 41), "SELECT", 9)
        pill(d, (W / 2 + 8, top + 14, W / 2 + 76, top + 41), "START", 9)
        dpad(d, 99, top + 240, 58, 54)
        abxy(d, 785, top + 249, 29, 43)
        touch_panel(d, (128, top + 64, 745, top + 131), compact=True)
        chip(d, W / 2 - 85, H - 41, "移动盘")
        chip(d, W / 2 + 1, H - 41, "触控 中间", 84, True)
        caption(d, W, H, ["现状：触控板是 617×67dp 的扁带，还贴在顶部 —— 又细又长又够不到",
                          "两组按键挤在最外角，中间空档全浪费了"])
    elif kind == "a":
        pill(d, (16, top + 14, 112, top + 52), "L1")
        pill(d, (W - 112, top + 14, W - 16, top + 52), "R1")
        pill(d, (W / 2 - 76, top + 14, W / 2 - 8, top + 41), "SELECT", 9)
        pill(d, (W / 2 + 8, top + 14, W / 2 + 76, top + 41), "START", 9)
        dpad(d, 152, top + 209, 58, 54)
        abxy(d, 721, top + 209, 29, 43)
        touch_panel(d, (276, top + 69, 596, top + 249))
        chip(d, W / 2 - 85, H - 41, "移动盘")
        chip(d, W / 2 + 1, H - 41, "触控 中间", 84, True)
        caption(d, W, H, ["方案 A（Xbox 式）：方向盘、ABXY 内收到 x≈150 / 723，两者同高，像手柄面板",
                          "触控板移到正中偏下 320×180dp（原来 617×67），左右拇指都够得到；芯片挪到底部中央"])
    else:
        pill(d, (16, top + 10, 112, top + 48), "L1")
        pill(d, (W - 112, top + 10, W - 16, top + 48), "R1")
        pill(d, (W / 2 - 76, top + 14, W / 2 - 8, top + 41), "SELECT", 9)
        pill(d, (W / 2 + 8, top + 14, W / 2 + 76, top + 41), "START", 9)
        dpad(d, 99, top + 240, 58, 54)
        abxy(d, 785, top + 249, 29, 43)
        touch_panel(d, (276, top + 69, 596, top + 249))
        chip(d, W / 2 - 85, H - 41, "移动盘")
        chip(d, W / 2 + 1, H - 41, "触控 中间", 84, True)
        caption(d, W, H, ["方案 B（保守）：按键位置一点不动，只把触控板换成正中偏下的 320×180dp 方块",
                          "改动最小、最稳；但外角两组按键离拇指根远，握着有点「撑手」"])
    im.save(path)
    return path


def portrait(kind, path):
    W, H = 393, 873
    im = Image.new("RGB", (P(W), P(H + 44)), BG)
    d = ImageDraw.Draw(im)
    portrait_chrome(d, W)
    top = 128
    vh = H - top

    chip(d, 14, top + 10, "移动盘")
    chip(d, 100, top + 10, "触控 中间", 84, True)
    pill(d, (200, top + 10, 270, top + 37), "SELECT", 9)
    pill(d, (278, top + 10, 348, top + 37), "START", 9)

    if kind == "a":
        touch_panel(d, (26, top + 60, 367, top + 330))
        pill(d, (16, top + 350, 112, top + 388), "L1")
        pill(d, (W - 112, top + 350, W - 16, top + 388), "R1")
        dpad(d, 99, top + 500, 54, 50)
        abxy(d, 300, top + 500, 27, 40)
        caption(d, W, H, ["方案 A（竖屏）：触控板改成 341×270dp 的近方形，落在屏幕中段，离拇指更近",
                          "方向盘 / ABXY 往上收一档，L1 / R1 夹在两者之间，下缘留出握持区"])
    else:
        touch_panel(d, (26, top + 56, 367, top + 330))
        pill(d, (16, top + 501, 112, top + 539), "L1")
        pill(d, (W - 112, top + 501, W - 16, top + 539), "R1")
        dpad(d, 99, top + 620, 54, 50)
        abxy(d, 300, top + 628, 27, 40)
        caption(d, W, H, ["方案 B（竖屏）：按键位置不动，只把触控板加方、下移到中段",
                          "右侧 ABXY 位置完全照旧，风险最小"])
    im.save(path)
    return path


if __name__ == "__main__":
    base = "C:/Users/22453/Desktop/phoneKB/"
    print(landscape("cur", base + "mock-gp4-cur-l.png"))
    print(landscape("a", base + "mock-gp4-a-l.png"))
    print(landscape("b", base + "mock-gp4-b-l.png"))
    print(portrait("a", base + "mock-gp4-a-p.png"))
    print(portrait("b", base + "mock-gp4-b-p.png"))
