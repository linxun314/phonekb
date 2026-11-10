# -*- coding: utf-8 -*-
"""v0.39.3 mockups: three redesigns for the touchpad scroll column (right side)."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
BG      = (0x0F, 0x11, 0x15)
SURF    = (0x1A, 0x1F, 0x28)
SURFACE = (0x17, 0x1B, 0x22)
SURF2   = (0x1F, 0x25, 0x30)
ACCENT  = (0x4C, 0x8D, 0xFF)
TEXT    = (0xE9, 0xEC, 0xF2)
DIM     = (0x98, 0xA1, 0xB3)
FAINT   = (0x5C, 0x64, 0x74)
OK      = (0x3D, 0xDC, 0x84)

FONT_R = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"
_c = {}
def font(s, b=False):
    k = (round(s, 2), b)
    if k not in _c:
        _c[k] = ImageFont.truetype(FONT_B if b else FONT_R, int(round(s * K)))
    return _c[k]

def W(v): return v * K
def rr(d, box, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = [int(round(W(v))) for v in box]
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(round(W(radius))), fill=fill,
                        outline=outline, width=int(round(W(width))) if outline else 0)
def text(d, s, x, y, size_dp, color, bold=False, anchor="la"):
    d.text((int(round(W(x))), int(round(W(y)))), s, font=font(size_dp, bold), fill=color, anchor=anchor)
def ln(d, x1, y1, x2, y2, color, width=1.8):
    d.line(((int(round(W(x1))), int(round(W(y1)))), (int(round(W(x2))), int(round(W(y2))))),
           fill=color, width=max(2, int(round(W(width)))))
def dot(d, cx, cy, r, color):
    d.ellipse((int(round(W(cx - r))), int(round(W(cy - r))), int(round(W(cx + r))), int(round(W(cy + r)))), fill=color)
def tri(d, cx, cy, s, color, up=True):
    sgn = -1 if up else 1
    d.polygon([(int(W(cx)), int(W(cy + sgn * s * 0.55))),
               (int(W(cx - s * 0.6)), int(W(cy - sgn * s * 0.45))),
               (int(W(cx + s * 0.6)), int(W(cy - sgn * s * 0.45)))], fill=color)
def icon_power(d, cx, cy, s, color):
    r = s * 0.36
    d.arc((int(W(cx - r)), int(W(cy - r)), int(W(cx + r)), int(W(cy + r))), 300, 240, fill=color, width=max(2, int(W(s * 0.13))))
    ln(d, cx, cy - r * 1.3, cx, cy, color, s * 0.13)
def icon_phone(d, cx, cy, s, color):
    w = max(2, int(W(s * 0.10)))
    rr(d, (cx - s * 0.30, cy - s * 0.48, cx + s * 0.30, cy + s * 0.48), s * 0.12, outline=color, width=w)
    ln(d, cx - s * 0.10, cy + s * 0.34, cx + s * 0.10, cy + s * 0.34, color, w / K)
def chev(d, cx, cy, s, color):
    ln(d, cx - s * 0.75, cy - s * 0.38, cx, cy + s * 0.38, color, 1.8)
    ln(d, cx, cy + s * 0.38, cx + s * 0.75, cy - s * 0.38, color, 1.8)

# ---------- three scroll-column designs ----------
def col_current(d, x0, y0, x1, y1):
    """A 之前那版：两颗独立键 + 中间一条细线。"""
    half = (y1 - y0 - 8) / 2
    rr(d, (x0, y0, x1, y0 + half), 14, fill=SURF)
    rr(d, (x0, y0 + half + 8, x1, y1), 14, fill=SURF)
    tri(d, (x0 + x1) / 2, y0 + half / 2, 11, TEXT, True)
    tri(d, (x0 + x1) / 2, y0 + half + 8 + half / 2, 11, TEXT, False)
    cx = (x0 + x1) / 2
    rr(d, (cx - 1.5, y0 + 6, cx + 1.5, y1 - 6), 1.5, fill=FAINT)

def col_wheel(d, x0, y0, x1, y1):
    """B 一体式滚轮：整条一个容器，中间是真的「滚轮」——粗胶囊 + 横纹刻线，上下小箭头。"""
    rr(d, (x0, y0, x1, y1), 16, fill=SURF, outline=FAINT, width=0.8)
    cx = (x0 + x1) / 2
    top = y0 + 26
    bot = y1 - 26
    tri(d, cx, y0 + 14, 7, TEXT, True)
    tri(d, cx, y1 - 14, 7, TEXT, False)
    # wheel body
    ww = 22
    rr(d, (cx - ww / 2, top, cx + ww / 2, bot), ww / 2, fill=SURF2, outline=DIM, width=0.8)
    # ridges inside the wheel
    n = 9
    for i in range(n):
        yy = top + (bot - top) * (i + 0.5) / n
        halfw = ww * 0.34
        ln(d, cx - halfw, yy, cx + halfw, yy, FAINT, 1.0)
    # thumb position
    dot(d, cx, (top + bot) / 2, 3.0, ACCENT)

def col_rail(d, x0, y0, x1, y1):
    """C 刻度滑块：整条容器 + 居中刻度线 + 实心滑块（拇指位置），上下箭头。"""
    rr(d, (x0, y0, x1, y1), 16, fill=SURF)
    cx = (x0 + x1) / 2
    tri(d, cx, y0 + 15, 7.5, TEXT, True)
    tri(d, cx, y1 - 15, 7.5, TEXT, False)
    top = y0 + 30
    bot = y1 - 30
    n = 14
    for i in range(n):
        yy = top + (bot - top) * i / (n - 1)
        ww = 7 if i % 2 else 5
        ln(d, cx - ww / 2, yy, cx + ww / 2, yy, FAINT, 1.2)
    # thumb
    th = 34
    ty = (top + bot) / 2
    rr(d, (cx - 11, ty - th / 2, cx + 11, ty + th / 2), 11, fill=ACCENT)
    ln(d, cx - 5, ty - 4, cx + 5, ty - 4, BG, 1.4)
    ln(d, cx - 5, ty + 4, cx + 5, ty + 4, BG, 1.4)

def col_wheel_text(d, x0, y0, x1, y1):
    """D 滚轮 + 文字标签：一体容器，滚轮偏上，下方竖排「滚轮」，最上和最下箭头。"""
    rr(d, (x0, y0, x1, y1), 16, fill=SURF, outline=ACCENT, width=0.9)
    cx = (x0 + x1) / 2
    tri(d, cx, y0 + 14, 7, TEXT, True)
    tri(d, cx, y1 - 14, 7, TEXT, False)
    top = y0 + 28
    bot = y1 - 66
    ww = 22
    rr(d, (cx - ww / 2, top, cx + ww / 2, bot), ww / 2, fill=SURF2, outline=DIM, width=0.8)
    for i in range(7):
        yy = top + (bot - top) * (i + 0.5) / 7
        ln(d, cx - 7, yy, cx + 7, yy, FAINT, 1.0)
    text(d, "滚", cx, y1 - 42, 10, ACCENT, bold=True, anchor="mm")
    text(d, "轮", cx, y1 - 26, 10, ACCENT, bold=True, anchor="mm")

def draw(style, out, note):
    wdp, hdp = 393, 873
    img = Image.new("RGB", (int(W(wdp)), int(W(hdp))), BG)
    d = ImageDraw.Draw(img)
    cy = 30
    rr(d, (14, cy, 68, cy + 36), 12, fill=SURF2)
    icon_power(d, 41, cy + 18, 18, ACCENT)
    rr(d, (74, cy, 152, cy + 36), 13, fill=SURF2, outline=ACCENT, width=1.6)
    icon_phone(d, 90, cy + 18, 17, ACCENT)
    text(d, "设备", 100, cy + 18, 12.5, ACCENT, bold=True, anchor="lm")
    dot(d, 164, cy + 18, 4, OK)
    text(d, "已连接", 173, cy + 9, 12, TEXT, bold=True, anchor="lm")
    text(d, "iQOO Pad2", 173, cy + 25, 9.5, DIM, anchor="lm")
    rr(d, (250, cy + 4, 312, cy + 32), 14, fill=SURF2)
    text(d, "平板", 261, cy + 18, 11.5, TEXT, anchor="lm")
    chev(d, 300, cy + 18, 5.2, DIM)
    rr(d, (318, cy + 4, 370, cy + 32), 14, fill=SURF2)
    text(d, "帮助", 344, cy + 18, 11.5, DIM, anchor="mm")

    tab_y = cy + 44
    tabs = ["键盘", "触控板", "媒体", "手柄", "双手"]
    tx = 18
    for i, t in enumerate(tabs):
        if i == 1:
            rr(d, (tx - 8, tab_y, tx + 36, tab_y + 26), 13, fill=SURF2)
            text(d, t, tx + 14, tab_y + 13, 11.5, TEXT, anchor="mm")
        else:
            text(d, t, tx + 14, tab_y + 13, 11.5, DIM, anchor="mm")
        tx += 44
    rr(d, (wdp - 66, tab_y, wdp - 14, tab_y + 26), 13, fill=SURF2)
    text(d, "旋转", wdp - 40, tab_y + 13, 11.5, TEXT, anchor="mm")

    sp_y = 292
    text(d, "拖动调整功能区", 14, sp_y, 10, DIM, anchor="lm")
    rr(d, (wdp / 2 - 46, sp_y - 2, wdp / 2 + 46, sp_y + 2), 2, fill=FAINT)
    text(d, "功能区 58% / 留空 42%", wdp - 14, sp_y, 10, DIM, anchor="rm")

    pad_y0, pad_y1 = 316, 566
    bar_x0, bar_x1 = 14, 74
    pad_x0, pad_x1 = 86, 322
    col_x0, col_x1 = 330, 379
    labels = ["数字", "键盘", "菜单"]
    cell_h = (pad_y1 - pad_y0) / 3.0 - 6
    for i, lb in enumerate(labels):
        y = pad_y0 + i * (cell_h + 9)
        rr(d, (bar_x0, y, bar_x1, y + cell_h), 12, fill=SURF)
        for r in range(3):
            for c in range(3):
                dot(d, (bar_x0 + bar_x1) / 2 + (c - 1) * 6, y + 18 + (r - 1) * 6, 1.4, DIM)
        text(d, lb, (bar_x0 + bar_x1) / 2, y + cell_h - 14, 10, DIM, anchor="mm")

    rr(d, (pad_x0, pad_y0, pad_x1, pad_y1), 16, fill=SURFACE)
    text(d, "触控区", (pad_x0 + pad_x1) / 2, (pad_y0 + pad_y1) / 2 - 8, 11, FAINT, anchor="mm")
    text(d, "单指移动 · 点=左键 · 双指=滚动", (pad_x0 + pad_x1) / 2, (pad_y0 + pad_y1) / 2 + 12, 9.5, FAINT, anchor="mm")

    if style == "A":
        col_current(d, col_x0, pad_y0, col_x1, pad_y1)
    elif style == "B":
        col_wheel(d, col_x0, pad_y0, col_x1, pad_y1)
    elif style == "C":
        col_rail(d, col_x0, pad_y0, col_x1, pad_y1)
    else:
        col_wheel_text(d, col_x0, pad_y0, col_x1, pad_y1)

    nav_y0, nav_y1 = 580, 636
    gap = 8
    cw = (wdp - 28 - gap * 3) / 4
    nav = ["返回", "主页", "后台", "中键"]
    for i, lb in enumerate(nav):
        x = 14 + i * (cw + gap)
        cx2, cy2 = x + cw / 2, (nav_y0 + nav_y1) / 2
        rr(d, (x, nav_y0, x + cw, nav_y1), 14, fill=SURF)
        if lb == "返回":
            ln(d, cx2 + 11, cy2, cx2 - 11, cy2, TEXT)
            ln(d, cx2 - 11, cy2, cx2 - 3, cy2 - 7, TEXT)
            ln(d, cx2 - 11, cy2, cx2 - 3, cy2 + 7, TEXT)
        elif lb == "主页":
            ln(d, cx2 - 11, cy2, cx2, cy2 - 10, TEXT)
            ln(d, cx2, cy2 - 10, cx2 + 11, cy2, TEXT)
            ln(d, cx2 - 8, cy2, cx2 - 8, cy2 + 9, TEXT)
            ln(d, cx2 + 8, cy2, cx2 + 8, cy2 + 9, TEXT)
            ln(d, cx2 - 8, cy2 + 9, cx2 + 8, cy2 + 9, TEXT)
        elif lb == "后台":
            rr(d, (cx2 - 10, cy2 - 8, cx2 + 5, cy2 + 7), 3, outline=TEXT, width=1.8)
            rr(d, (cx2 - 4, cy2 - 2, cx2 + 11, cy2 + 13), 3, outline=TEXT, width=1.8)
        else:
            text(d, lb, cx2, cy2, 11.5, TEXT, anchor="mm")

    text(d, note, 14, hdp - 30, 11, FAINT)
    img.save(os.path.join(ROOT, out))
    print("saved", out)

draw("A", "mock-col-a.png", "方案 A（现在）两颗独立键 + 中间一条细线")
draw("B", "mock-col-b.png", "方案 B 一体式「滚轮」：粗胶囊 + 横纹刻线 + 上下小箭头")
draw("C", "mock-col-c.png", "方案 C 刻度滑块：居中刻度 + 蓝色滑块（拇指位置）")
draw("D", "mock-col-d.png", "方案 D 滚轮 + 「滚轮」两字竖排，外框描蓝 = 一眼看出是滚轮区")
