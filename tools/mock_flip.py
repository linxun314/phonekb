# -*- coding: utf-8 -*-
"""Red-fruit short-drama fix: A = send-mode switch on the strip, B = dedicated page-flip keys."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
BG      = (0x0F, 0x11, 0x15)
SURFACE = (0x17, 0x1B, 0x22)
SURF2   = (0x1F, 0x25, 0x30)
SURF3   = (0x26, 0x2D, 0x39)
ACCENT  = (0x4C, 0x8D, 0xFF)
TEXT    = (0xE9, 0xEC, 0xF2)
DIM     = (0x98, 0xA1, 0xB3)
FAINT   = (0x5C, 0x64, 0x74)
OK      = (0x3D, 0xDC, 0x84)
WHITE   = (255, 255, 255)

def mix(fg, a, bg):
    return tuple(int(round(fg[i] * a + bg[i] * (1 - a))) for i in range(3))

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
                        outline=outline, width=max(1, int(round(W(width)))) if outline else 0)
def text(d, s, x, y, size_dp, color, bold=False, anchor="la"):
    d.text((int(round(W(x))), int(round(W(y)))), s, font=font(size_dp, bold), fill=color, anchor=anchor)
def ln(d, x1, y1, x2, y2, color, width=1.8):
    d.line(((int(round(W(x1))), int(round(W(y1)))), (int(round(W(x2))), int(round(W(y2))))),
           fill=color, width=max(2, int(round(W(width)))))
def dot(d, cx, cy, r, color):
    d.ellipse((int(round(W(cx - r))), int(round(W(cy - r))), int(round(W(cx + r))), int(round(W(cy + r)))), fill=color)
def chevron(d, cx, cy, half_w, half_h, color, width=1.6, up=True):
    sgn = -1 if up else 1
    ln(d, cx - half_w, cy - sgn * half_h * 0.5, cx, cy + sgn * half_h, color, width)
    ln(d, cx, cy + sgn * half_h, cx + half_w, cy - sgn * half_h * 0.5, color, width)
def icon_power(d, cx, cy, s, color):
    r = s * 0.36
    d.arc((int(W(cx - r)), int(round(W(cy - r))), int(round(W(cx + r))), int(round(W(cy + r)))), 300, 240,
          fill=color, width=max(2, int(round(W(s * 0.13)))))
    ln(d, cx, cy - r * 1.3, cx, cy, color, s * 0.13)
def icon_phone(d, cx, cy, s, color):
    w = max(2, int(W(s * 0.10)))
    rr(d, (cx - s * 0.30, cy - s * 0.48, cx + s * 0.30, cy + s * 0.48), s * 0.12, outline=color, width=w)
    ln(d, cx - s * 0.10, cy + s * 0.34, cx + s * 0.10, cy + s * 0.34, color, w / K)
def chev(d, cx, cy, s, color):
    ln(d, cx - s * 0.75, cy - s * 0.38, cx, cy + s * 0.38, color, 1.8)
    ln(d, cx, cy + s * 0.38, cx + s * 0.75, cy - s * 0.38, color, 1.8)

WDP, HDP = 393, 873

def page(active_tab=1):
    img = Image.new("RGB", (int(W(WDP)), int(W(HDP))), BG)
    d = ImageDraw.Draw(img)
    cy = 30
    rr(d, (14, cy, 68, cy + 36), 12, fill=SURF2); icon_power(d, 41, cy + 18, 18, ACCENT)
    rr(d, (74, cy, 152, cy + 36), 13, fill=SURF2, outline=ACCENT, width=1.6)
    icon_phone(d, 90, cy + 18, 17, ACCENT)
    text(d, "设备", 100, cy + 18, 12.5, ACCENT, bold=True, anchor="lm")
    dot(d, 164, cy + 18, 4, OK)
    text(d, "已连接", 173, cy + 9, 12, TEXT, bold=True, anchor="lm")
    text(d, "iQOO Pad2", 173, cy + 25, 9.5, DIM, anchor="lm")
    rr(d, (250, cy + 4, 312, cy + 32), 14, fill=SURF2)
    text(d, "平板", 261, cy + 18, 11.5, TEXT, anchor="lm"); chev(d, 300, cy + 18, 5.2, DIM)
    rr(d, (318, cy + 4, 370, cy + 32), 14, fill=SURF2)
    text(d, "帮助", 344, cy + 18, 11.5, DIM, anchor="mm")

    tab_y = cy + 44
    tx = 18
    for i, t in enumerate(["键盘", "触控板", "媒体", "手柄", "双手"]):
        if i == active_tab:
            rr(d, (tx - 8, tab_y, tx + 36, tab_y + 26), 13, fill=SURF2)
            text(d, t, tx + 14, tab_y + 13, 11.5, TEXT, anchor="mm")
        else:
            text(d, t, tx + 14, tab_y + 13, 11.5, DIM, anchor="mm")
        tx += 44
    rr(d, (WDP - 66, tab_y, WDP - 14, tab_y + 26), 13, fill=SURF2)
    text(d, "旋转", WDP - 40, tab_y + 13, 11.5, TEXT, anchor="mm")

    sp_y = 292
    text(d, "拖动调整功能区", 14, sp_y, 10, DIM, anchor="lm")
    rr(d, (WDP / 2 - 46, sp_y - 2, WDP / 2 + 46, sp_y + 2), 2, fill=FAINT)
    text(d, "功能区 58% / 留空 42%", WDP - 14, sp_y, 10, DIM, anchor="rm")

    pad_y0, pad_y1 = 316, 566
    bar_x0, bar_x1 = 14, 74
    pad_x0, pad_x1 = 86, 322
    col_x0, col_x1 = 330, 379
    cell_h = (pad_y1 - pad_y0) / 3.0 - 6
    for i, lb in enumerate(["数字", "键盘", "菜单"]):
        y = pad_y0 + i * (cell_h + 9)
        rr(d, (bar_x0, y, bar_x1, y + cell_h), 12, fill=SURFACE)
        for r in range(3):
            for c in range(3):
                dot(d, (bar_x0 + bar_x1) / 2 + (c - 1) * 6, y + 18 + (r - 1) * 6, 1.4, DIM)
        text(d, lb, (bar_x0 + bar_x1) / 2, y + cell_h - 14, 10, DIM, anchor="mm")
    rr(d, (pad_x0, pad_y0, pad_x1, pad_y1), 16, fill=SURFACE)
    text(d, "触控区", (pad_x0 + pad_x1) / 2, (pad_y0 + pad_y1) / 2 - 8, 11, FAINT, anchor="mm")
    text(d, "单指移动 · 点=左键 · 双指=滚动", (pad_x0 + pad_x1) / 2, (pad_y0 + pad_y1) / 2 + 12, 9.5, FAINT, anchor="mm")
    # macOS strip (same as shipped v0.40.x)
    cx = (col_x0 + col_x1) / 2
    rr(d, (col_x0, pad_y0, col_x1, pad_y1), 16, fill=mix(WHITE, 0.07, SURFACE), outline=mix(WHITE, 0.13, SURFACE), width=0.9)
    rr(d, (col_x0 + 1.2, pad_y0 + 1.2, col_x1 - 1.2, pad_y0 + 9), 8, fill=mix(WHITE, 0.04, SURFACE))
    chevron(d, cx, pad_y0 + 16, 3.6, 2.6, mix(WHITE, 0.55, SURFACE), 1.4, True)
    chevron(d, cx, pad_y1 - 16, 3.6, 2.6, mix(WHITE, 0.55, SURFACE), 1.4, False)
    ty = (pad_y0 + pad_y1) / 2
    rr(d, (cx - 15, ty - 37, cx + 15, ty + 37), 15, fill=mix(WHITE, 0.50, SURFACE), outline=mix(WHITE, 0.20, SURFACE), width=0.8)
    for k in (-6, 0, 6):
        ln(d, cx - 7, ty + k, cx + 7, ty + k, mix((40, 44, 52), 0.55, SURFACE), 1.2)
    return img, d

def nav4(d, pad_y0=580):
    gap = 8
    cw = (WDP - 28 - gap * 3) / 4
    for i, lb in enumerate(["返回", "主页", "后台", "中键"]):
        x = 14 + i * (cw + gap)
        cx2, cy2 = x + cw / 2, pad_y0 + 28
        rr(d, (x, pad_y0, x + cw, pad_y0 + 56), 14, fill=SURFACE)
        navglyph(d, lb, cx2, cy2)

def navglyph(d, lb, cx2, cy2):
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
    elif lb == "中键":
        text(d, lb, cx2, cy2, 11.5, TEXT, anchor="mm")

def chip(d, x, y, w, h, label, on=False, size=11):
    rr(d, (x, y, x + w, y + h), 11, fill=(SURF3 if on else SURFACE), outline=(ACCENT if on else None), width=1.4)
    text(d, label, x + w / 2, y + h / 2, size, (ACCENT if on else DIM), bold=on, anchor="mm")

# ---------------- 方案 A：滚动条/上下键「发送什么」可切 ----------------
def draw_a():
    img, d = page(1)
    nav4(d)
    # dim everything behind the sheet
    ov = Image.new("RGBA", img.size, (0, 0, 0, 150))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(img)
    sx0, sx1 = 14, WDP - 14
    sy0, sy1 = 268, 664
    rr(d, (sx0, sy0, sx1, sy1), 18, fill=SURFACE)
    text(d, "指针灵敏度", sx0 + 16, sy0 + 18, 15, TEXT, bold=True)
    text(d, "1.8x", sx1 - 16, sy0 + 20, 14, ACCENT, bold=True, anchor="ra")
    y = sy0 + 52
    text(d, "指针速度", sx0 + 16, y, 12, TEXT)
    rr(d, (sx0 + 16, y + 16, sx1 - 16, y + 18), 2, fill=SURF3)
    rr(d, (sx0 + 80, y + 12, sx0 + 100, y + 22), 5, fill=ACCENT)
    y += 44
    text(d, "上下键 / 滚动条发送什么", sx0 + 16, y, 12, TEXT, bold=True)
    y += 24
    gap = 8
    cw = (sx1 - sx0 - 32 - gap * 1) / 2
    chip(d, sx0 + 16, y, cw, 34, "滚轮（默认）", True)
    chip(d, sx0 + 16 + cw + gap, y, cw, 34, "方向键 ↑↓")
    y += 40
    chip(d, sx0 + 16, y, cw, 34, "PageUp·PageDown")
    chip(d, sx0 + 16 + cw + gap, y, cw, 34, "媒体 上一首·下一首")
    y += 46
    rr(d, (sx0 + 16, y, sx1 - 16, y + 62), 12, fill=SURF2)
    text(d, "红果短剧 / 抖音这类「上下翻整页」的 App 只认方向键或", sx0 + 28, y + 16, 10, DIM)
    text(d, "媒体键，滚轮只会滚一点点 → 切到「方向键」就一按一个视频", sx0 + 28, y + 34, 10, DIM)
    y += 76
    chip(d, sx0 + 16, y, (sx1 - sx0 - 32 - gap) / 2, 38, "取消")
    chip(d, sx1 - 16 - (sx1 - sx0 - 32 - gap) / 2, y, (sx1 - sx0 - 32 - gap) / 2, 38, "保存", True)
    text(d, "方案 A：给上下键 / 滚动条加「发送什么」开关", 14, HDP - 30, 10.5, FAINT)
    img.save(os.path.join(ROOT, "mock-flip-a.png")); print("saved mock-flip-a.png")

# ---------------- 方案 B：底部导航行加两颗翻页键 ----------------
def draw_b():
    img, d = page(1)
    gap = 8
    n = 6
    cw = (WDP - 28 - gap * (n - 1)) / n
    y0, y1 = 580, 636
    items = ["返回", "主页", "后台", "中键", "PAGEUP", "PAGEDN"]
    for i, lb in enumerate(items):
        x = 14 + i * (cw + gap)
        cx2, cy2 = x + cw / 2, (y0 + y1) / 2
        new = lb in ("PAGEUP", "PAGEDN")
        rr(d, (x, y0, x + cw, y1), 14, fill=(SURF2 if new else SURFACE),
           outline=(ACCENT if new else None), width=1.4)
        if lb == "PAGEUP":
            chevron(d, cx2, cy2 - 6, 7, 2.6, ACCENT, 1.8, True)
            ln(d, cx2 - 7, cy2 + 12, cx2 + 7, cy2 + 12, ACCENT, 1.8)
            text(d, "上一集", cx2, y1 + 12, 9, ACCENT, bold=True, anchor="mm")
        elif lb == "PAGEDN":
            chevron(d, cx2, cy2 + 6, 7, 2.6, ACCENT, 1.8, False)
            ln(d, cx2 - 7, cy2 - 12, cx2 + 7, cy2 - 12, ACCENT, 1.8)
            text(d, "下一集", cx2, y1 + 12, 9, ACCENT, bold=True, anchor="mm")
        else:
            navglyph(d, lb, cx2, cy2)
    text(d, "方案 B：底部导航行从 4 格变 6 格，多两颗「上一集 / 下一集」", 14, HDP - 52, 10.5, FAINT)
    text(d, "固定发 方向键 ↑↓（或媒体键），按住连续翻；滚动条保持「滚轮」不变", 14, HDP - 34, 10.5, FAINT)
    img.save(os.path.join(ROOT, "mock-flip-b.png")); print("saved mock-flip-b.png")

draw_a()
draw_b()