# -*- coding: utf-8 -*-
"""Render gamepad touch-area mockups (v0.21 proposal)."""
from PIL import Image, ImageDraw, ImageFont

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
    import math
    base = [(-0.46, 0.30), (0.0, -0.36), (0.46, 0.30)]
    a = math.radians(ang)
    pts = []
    for bx, by in base:
        x = bx * math.cos(a) - by * math.sin(a)
        y = bx * math.sin(a) + by * math.cos(a)
        pts.append((P(cx + x * size), P(cy + y * size)))
    d.line(pts, fill=color, width=max(2, P(w)), joint="curve")


def status_bar(d, W):
    txt(d, "10:47", 14, 8, 12)
    rr(d, (W - 34, 9, W - 16, 21), 3, outline=DIM, width=1)
    rr(d, (W - 32, 11, W - 21, 19), 1, fill=OK)


def header(d, W, h=44):
    rr(d, (14, 6, 62, 54), 14, fill=SURF2)
    d.ellipse([P(32), P(24), P(44), P(36)], outline=TEXT, width=P(2))
    d.line([P(38), P(30), P(38), P(21)], fill=TEXT, width=P(2))
    d.ellipse([P(66), P(26), P(74), P(34)], fill=OK)
    txt(d, "已连接", 80, 20, 14, bold=True)
    txt(d, "iQOO Pad2", 80, 38, 10, DIM)
    for label, x in (("旋转", W - 168), ("伴侣", W - 112), ("帮助", W - 56)):
        rr(d, (x, 12, x + 50, 46), 10, fill=SURF2)
        txt(d, label, x + 25, 29, 11, anchor="mm")


def tabs(d, W, top, active=3):
    names = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
    cell = (W - 28) / 6.0
    for i, n in enumerate(names):
        x = 14 + cell * i
        if i == active:
            rr(d, (x, top, x + cell - 6, top + 34), 10, fill=SURF2)
        txt(d, n, x + (cell - 6) / 2, top + 17, 12, TEXT if i == active else DIM, bold=(i == active), anchor="mm")


def power_chip(d, x, y, label="移动盘"):
    rr(d, (x, y, x + 78, y + 27), 9, fill=SURF2)
    txt(d, label, x + 12, y + 13, 11, anchor="lm")
    chev(d, x + 64, y + 13, 180, 7, DIM, 1.6)


def small_chip(d, x, y, label, active=False, w=64):
    rr(d, (x, y, x + w, y + 27), 9, fill=SURF2 if not active else KEY, outline=ACCENT if active else None, width=1)
    txt(d, label, x + w / 2, y + 13, 11, ACCENT if active else TEXT, anchor="mm")


def pill(d, box, label, size=11, fill=SURF2, color=TEXT):
    rr(d, box, 9, fill=fill)
    txt(d, label, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2, size, color, anchor="mm")


def dpad(d, cx, cy, s, arm):
    for (dx, dy, ang) in ((0, -arm, 0), (arm, 0, 90), (0, arm, 180), (-arm, 0, 270)):
        rr(d, (cx + dx - s / 2, cy + dy - s / 2, cx + dx + s / 2, cy + dy + s / 2), 12, fill=KEY)
        chev(d, cx + dx, cy + dy, ang, s * 0.30, TEXT, s * 0.075)


def abxy(d, cx, cy, r, off, keys):
    for lab, c, dx, dy in keys:
        d.ellipse([P(cx + dx - r), P(cy + dy - r), P(cx + dx + r), P(cy + dy + r)], fill=c)
        txt(d, lab, cx + dx, cy + dy, r * 0.62, TEXT, bold=True, anchor="mm")


def touch_area(d, box, label="触控区", hint="单指移动 · 点击=左键 · 双指滑=滚动", strong=True):
    if strong:
        rr(d, box, 16, fill=None, outline=(58, 68, 90), width=1.5)
        rr(d, (box[0] + 10, box[1] + 8, box[0] + 74, box[1] + 30), 8, fill=(30, 36, 46))
        txt(d, "触控板", box[0] + 42, box[1] + 19, 9, DIM, anchor="mm")
        cx = (box[0] + box[2]) / 2
        cy = (box[1] + box[3]) / 2
        d.ellipse([P(cx - 9), P(cy - 9), P(cx + 9), P(cy + 9)], outline=DIM, width=max(1, P(1.2)))
        d.ellipse([P(cx - 3), P(cy - 3), P(cx + 3), P(cy + 3)], fill=DIM)
        txt(d, label, cx, cy + 20, 12, DIM, anchor="ma")
        txt(d, hint, cx, cy + 36, 9, (110, 120, 138), anchor="ma")
    else:
        cx = (box[0] + box[2]) / 2
        cy = (box[1] + box[3]) / 2
        txt(d, "空白处＝触控区", cx, cy - 22, 13, (100, 110, 128), anchor="ma")
        txt(d, "单指移动 · 点击=左键 · 双指滑=滚动 · 双指点击=右键", cx, cy + 2, 9, (84, 94, 112), anchor="ma")
        txt(d, "（游戏时可一键关掉，避免误触）", cx, cy + 18, 9, (72, 82, 98), anchor="ma")


def caption(d, W, page_h, lines):
    y = page_h + 12
    for i, s in enumerate(lines):
        txt(d, s, W / 2, y + 14 * i, 10, (124, 134, 152), anchor="ma")


def portrait(mode, path):
    W, H = 393, 873
    im = Image.new("RGB", (P(W), P(H + 48)), BG)
    d = ImageDraw.Draw(im)
    status_bar(d, W)
    header(d, W)
    tabs(d, W, 62)
    top = 118

    power_chip(d, 14, top + 10)
    small_chip(d, 100, top + 10, "触控", True, 64)
    pill(d, (200, top + 10, 270, top + 37), "SELECT", 9)
    pill(d, (278, top + 10, 348, top + 37), "START", 9)

    L1 = (16, 629, 112, 667)
    R1 = (W - 112, 629, W - 16, 667)
    pill(d, L1, "L1")
    pill(d, R1, "R1")

    dpad(d, 99, 748, 54, 50)
    abxy(d, 310, 756, 27, 40, (("Y", Y_C, 0, -40), ("X", X_C, -40, 0), ("B", B_C, 40, 0), ("A", A_C, 0, 40)))

    if mode == "pad":
        touch_area(d, (26, 168, 367, 590), strong=True)
        caption(d, W, H, ["方案 A：中间固定一块「触控板」，边界看得见，其余留白不响应",
                          "左上「触控」小键可一键关掉，全屏就还是原来那张干净的按键图"])
    else:
        touch_area(d, (26, 150, 367, 600), strong=False)
        caption(d, W, H, ["方案 B：除按键外整片空白都能当触控板（无边界）",
                          "误触风险最高，建议配「只移动 / 完整」两档，默认只移动"])
    im.save(path)
    return path


def landscape(mode, path):
    W, H = 873, 393
    im = Image.new("RGB", (P(W), P(H + 44)), BG)
    d = ImageDraw.Draw(im)
    rr(d, (14, 6, 62, 54), 14, fill=SURF2)
    d.ellipse([P(32), P(24), P(44), P(36)], outline=TEXT, width=P(2))
    d.line([P(38), P(30), P(38), P(21)], fill=TEXT, width=P(2))
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

    power_chip(d, 16, 64)
    small_chip(d, 122, 64, "触控", True, 64)
    pill(d, (W / 2 - 130, 64, W / 2 - 54, 91), "SELECT", 9)
    pill(d, (W / 2 - 44, 64, W / 2 + 32, 91), "START", 9)

    pill(d, (16, 66, 112, 104), "L1")
    pill(d, (W - 112, 66, W - 16, 104), "R1")

    dpad(d, 99, 296, 58, 54)
    abxy(d, 785, 300, 29, 43, (("Y", Y_C, 0, -43), ("X", X_C, -43, 0), ("B", B_C, 43, 0), ("A", A_C, 0, 43)))

    if mode == "pad":
        touch_area(d, (210, 120, 660, 300), strong=True)
        caption(d, W, H, ["方案 A（横屏）：中间一块可见边界的触控板，两边留白不响应"])
    else:
        touch_area(d, (200, 110, 670, 310), strong=False)
        caption(d, W, H, ["方案 B（横屏）：除按键外整片空白都是触控板"])
    im.save(path)
    return path


if __name__ == "__main__":
    base = "C:/Users/22453/Desktop/phoneKB/"
    print(portrait("pad", base + "mock-gp3-a-p.png"))
    print(portrait("full", base + "mock-gp3-b-p.png"))
    print(landscape("pad", base + "mock-gp3-a-l.png"))
    print(landscape("full", base + "mock-gp3-b-l.png"))
