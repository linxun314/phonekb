# -*- coding: utf-8 -*-
"""v0.30 方案效果图：把摇杆/移动盘做成「转盘」形态的几种选择。"""
from PIL import Image, ImageDraw, ImageFont
import math, os

K = 2.75
BG = (15, 17, 21)
BODY = (44, 48, 66)
BODY_EDGE = (54, 58, 78)
SURF = (23, 27, 34)
SURF2 = (31, 37, 48)
KEY = (38, 45, 58)
ACCENT = (76, 141, 255)
TEXT = (233, 236, 242)
DIM = (152, 161, 179)
OK = (61, 220, 132)
WARN = (255, 176, 32)
WHITE = (211, 218, 231)

F = "C:/Windows/Fonts/msyh.ttc"
FB = "C:/Windows/Fonts/msyhbd.ttc"
def f(sz, bold=False):
    return ImageFont.truetype(FB if bold else F, int(sz * K))

def P(v):
    return int(round(v * K))

def txt(d, xy, s, sz, color=TEXT, bold=False, anchor="mm"):
    d.text((P(xy[0]), P(xy[1])), s, font=f(sz, bold), fill=color, anchor=anchor)

def rr(d, box, r, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = [P(v) for v in box]
    d.rounded_rectangle([x0, y0, x1, y1], radius=P(r), fill=fill, outline=outline, width=int(max(1, width * K)))

def ring(d, cx, cy, r, color=WHITE, width=1.2):
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], outline=color, width=int(max(1, width * K)))

def chev(d, cx, cy, size, ang_deg, color=TEXT, w=3.2):
    """指向 ang 方向（0=上）的箭头"""
    a = math.radians(ang_deg - 90)
    pts = [(-0.46, 0.30), (0.0, -0.36), (0.46, 0.30)]
    out = []
    for px, py in pts:
        x, y = px * size, py * size
        rx = x * math.cos(a) - y * math.sin(a)
        ry = x * math.sin(a) + y * math.cos(a)
        out.append((P(cx + rx), P(cy + ry)))
    d.line(out, fill=color, width=int(max(1, w * K)), joint="curve")

def status_bar(d, W, portrait):
    txt(d, (14, 14), "10:47", 11, TEXT, anchor="lm")
    rr(d, (W - 46, 8, W - 18, 22), 5, fill=KEY)
    rr(d, (W - 43, 11, W - 33, 19), 2, fill=OK)

def header(d, W, portrait):
    rr(d, (14, 30, 62, 78), 12, fill=SURF2)
    ring(d, 38, 54, 9, TEXT, 2.2)
    d.line([P(38), P(46), P(38), P(54)], fill=TEXT, width=int(2.2 * K))
    txt(d, (72, 44), "已连接", 15, TEXT, True, "lm")
    txt(d, (72, 64), "iQOO Pad2", 11, DIM, anchor="lm")
    pills = ["旋转", "伴侣", "帮助"]
    x = W - 16
    for name in reversed(pills):
        w = 54 if name != "帮助" else 50
        rr(d, (x - w, 34, x, 74), 10, fill=SURF2)
        txt(d, (x - w / 2, 54), name, 12)
        x -= (w + 8)
    return 88

def tabs(d, y, items, active):
    if len(items) == 6:
        step = (1140 - 60) / 6
        for i, name in enumerate(items):
            cx = 56 + step * (i + 0.5)
            if name == active:
                rr(d, (cx - step / 2 + 4, y, cx + step / 2 - 4, y + 38), 9, fill=SURF2)
            txt(d, (cx, y + 19), name, 12, TEXT if name == active else DIM)
    return y + 48

def body(d, box):
    rr(d, box, 18, fill=BODY, outline=BODY_EDGE, width=1.4)

def pill(d, box, label, sz=12, color=TEXT, r=None):
    rr(d, box, r if r else min(10, (box[3] - box[1]) / 2), fill=KEY)
    txt(d, ((box[0] + box[1]) * 0 + (box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, sz, color)

def disc(d, cx, cy, r, label, sub, pressed_ang=None, badge=None):
    # 杯底
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], fill=(24, 29, 41), outline=WHITE)
    if pressed_ang is not None:
        # 高亮扇区
        d.pieslice([P(cx - r), P(cy - r), P(cx + r), P(cy + r)],
                   start=90 - pressed_ang - 22.5, end=90 - pressed_ang + 22.5,
                   fill=(46, 74, 124))
        d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], outline=WHITE)
    # 四向箭头
    for ang in (0, 180, 90, 270):
        chev(d, cx + math.sin(math.radians(ang)) * r * 0.62,
             cy - math.cos(math.radians(ang)) * r * 0.62,
             r * 0.36, ang, TEXT, 3.4)
    # 中心点
    d.ellipse([P(cx - r * 0.12), P(cy - r * 0.12), P(cx + r * 0.12), P(cy + r * 0.12)], fill=DIM)
    txt(d, (cx, cy + r + 12), label, 12, TEXT, True)
    if sub:
        txt(d, (cx, cy + r + 28), sub, 9.5, DIM)
    if badge:
        txt(d, (cx, cy - r - 12), badge, 10, ACCENT, False)

def stick(d, cx, cy, r, label, sub):
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], fill=(24, 29, 41), outline=WHITE)
    for a in (0, 90, 180, 270):
        x0 = cx + math.sin(math.radians(a)) * r * 0.86
        y0 = cy - math.cos(math.radians(a)) * r * 0.86
        x1 = cx + math.sin(math.radians(a)) * r * 0.72
        y1 = cy - math.cos(math.radians(a)) * r * 0.72
        d.line([P(x0), P(y0), P(x1), P(y1)], fill=WHITE, width=int(1.2 * K))
    d.ellipse([P(cx - r * 0.54), P(cy - r * 0.54), P(cx + r * 0.54), P(cy + r * 0.54)], fill=WHITE)
    txt(d, (cx, cy + r + 12), label, 12, TEXT, True)
    if sub:
        txt(d, (cx, cy + r + 28), sub, 9.5, DIM)

def caption(d, W, H, lines):
    y = H - 8 - 16 * len(lines)
    for i, line in enumerate(lines):
        txt(d, (W / 2, y + i * 16), line, 10, DIM)

def land(plan):
    W, H = 873, 393
    im = Image.new("RGB", (P(W), P(H)), BG)
    d = ImageDraw.Draw(im)
    status_bar(d, W, False)
    y = header(d, W, False)
    bodyTop = y
    body(d, (16, bodyTop, W - 16, H - 8))
    # 肩键带
    by = bodyTop + 8
    pill(d, (30, by, 126, by + 30), "L1")
    pill(d, (132, by, 228, by + 30), "LT")
    pill(d, (W / 2 - 86, by, W / 2 - 8, by + 30), "SELECT", 11)
    pill(d, (W / 2 + 2, by, W / 2 + 80, by + 30), "START", 11)
    pill(d, (W - 228, by, W - 132, by + 30), "RT")
    pill(d, (W - 126, by, W - 30, by + 30), "R1")
    cy = bodyTop + 168
    if plan == "A":
        disc(d, 120, cy, 74, "移动盘 · 转盘", "按住滑动选方向 · 发方向键 8 向", badge=None)
        disc(d, W - 120, cy, 74, "右转盘", "按住滑动选方向 · 发 ←↑↓→", badge="替代右摇杆")
    elif plan == "B":
        disc(d, 120, cy, 74, "移动盘 · 转盘", "按住滑动选方向")
        stick(d, W - 120, cy, 74, "右摇杆（保留）", "模拟轴 RX/RY · 需主机认轴")
    elif plan == "C":
        disc(d, 120, cy, 74, "移动盘 · 转盘", "hat 8 向")
        disc(d, W - 120, cy, 74, "右转盘 · 按钮位", "4 向 = 剩余按钮位（需重配）", badge="需重新配对")
    caption(d, W, H, caption_lines[plan])
    return im

def port(plan):
    W, H = 393, 873
    im = Image.new("RGB", (P(W), P(H)), BG)
    d = ImageDraw.Draw(im)
    status_bar(d, W, True)
    y = header(d, W, True) - 4
    # tabs 两行
    txt(d, (W / 2, y + 12), "键盘   触控板   媒体   手柄   双手   单手", 11, DIM)
    y += 34
    body(d, (16, y, W - 16, H - 10))
    by = y + 10
    pill(d, (30, by, 118, by + 26), "移动盘 ∨", 10)
    pill(d, (124, by, 200, by + 26), "触控 关", 10)
    cy = y + 118
    disc(d, 108, cy, 66, "移动盘 · 转盘", "按住滑动选方向")
    disc(d, W - 108, cy, 66, "右转盘", "发 ←↑↓→（或模拟轴）")
    # 肩键
    yy = cy + 148
    pill(d, (30, yy, 148, yy + 30), "L1", 11)
    pill(d, (30, yy + 38, 148, yy + 68), "LT", 11)
    pill(d, (W - 148, yy, W - 30, yy + 30), "R1", 11)
    pill(d, (W - 148, yy + 38, W - 30, yy + 68), "RT", 11)
    # 底部菜单键
    pill(d, (W / 2 - 96, H - 56, W / 2 - 8, H - 26), "SELECT", 10)
    pill(d, (W / 2 + 4, H - 56, W / 2 + 92, H - 26), "START", 10)
    caption(d, W, H, ["竖屏同样能放：两个转盘并排，肩键竖排两侧，菜单键底部"])
    return im

caption_lines = {
    "A": ["方案 A（推荐）：右摇杆 → 转盘，按住滑动选 8 方向，发送方向键 ←↑↓→（不需要重新配对，任何认方向键的游戏/模拟器都能用）"],
    "B": ["方案 B（保守）：只把右摇杆外观换成转盘，仍发模拟轴 RX/RY（主机不认轴的话依然没反应）"],
    "C": ["方案 C：右转盘映射到 4 个剩余按钮位（BTN_C/Z/MODE/THUMB），适合能自定义映射的模拟器；要重新配对一次"],
}

out = "C:/Users/22453/Desktop/phoneKB"
land("A").save(os.path.join(out, "mock-v30-A-land.png"))
land("B").save(os.path.join(out, "mock-v30-B-land.png"))
land("C").save(os.path.join(out, "mock-v30-C-land.png"))
port("A").save(os.path.join(out, "mock-v30-A-port.png"))
print("ok")