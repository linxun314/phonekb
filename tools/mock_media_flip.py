# -*- coding: utf-8 -*-
"""Media page variants for the page-flip feature.
A = merge into the existing prev / next keys (send-mode segmented row).
B = add two dedicated flip keys under the -10s / +10s row.
"""
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
def chev(d, cx, cy, s, color, width=1.8):
    ln(d, cx - s * 0.75, cy - s * 0.38, cx, cy + s * 0.38, color, width)
    ln(d, cx, cy + s * 0.38, cx + s * 0.75, cy - s * 0.38, color, width)
def chev_updown(d, cx, cy, half_w, half_h, color, width=1.9, up=True):
    sgn = -1 if up else 1
    ln(d, cx - half_w, cy - sgn * half_h * 0.5, cx, cy + sgn * half_h, color, width)
    ln(d, cx, cy + sgn * half_h, cx + half_w, cy - sgn * half_h * 0.5, color, width)

def poly(d, pts, color):
    d.polygon([(W(p[0]), W(p[1])) for p in pts], fill=color)
def tri_right(d, cx, cy, s, color):
    poly(d, [(cx + s, cy), (cx - s * 0.72, cy - s * 0.9), (cx - s * 0.72, cy + s * 0.9)], color)
def tri_left(d, cx, cy, s, color):
    poly(d, [(cx - s, cy), (cx + s * 0.72, cy - s * 0.9), (cx + s * 0.72, cy + s * 0.9)], color)
def vbar(d, cx, cy, s, color, w=None):
    ww = w if w is not None else s * 0.36
    rr(d, (cx - ww / 2, cy - s * 0.9, cx + ww / 2, cy + s * 0.9), ww / 2, fill=color)

def g_prev(d, cx, cy, s, c):   # |<
    vbar(d, cx - s * 0.85, cy, s, c); tri_left(d, cx + s * 0.18, cy, s, c)
def g_next(d, cx, cy, s, c):   # >|
    tri_right(d, cx - s * 0.18, cy, s, c); vbar(d, cx + s * 0.85, cy, s, c)
def g_play(d, cx, cy, s, c):   # >||
    tri_right(d, cx - s * 0.42, cy, s * 0.9, c)
    vbar(d, cx + s * 0.30, cy, s * 0.8, c, s * 0.26); vbar(d, cx + s * 0.72, cy, s * 0.8, c, s * 0.26)
def g_seek(d, cx, cy, s, c, left=True):
    t = tri_left if left else tri_right
    t(d, cx + (-s * 0.42 if left else s * 0.42), cy, s * 0.62, c)
    t(d, cx + (-s * 0.95 if left else s * 0.95), cy, s * 0.62, c)

def icon_power(d, cx, cy, s, color):
    r = s * 0.36
    d.arc((int(W(cx - r)), int(W(cy - r)), int(W(cx + r)), int(W(cy + r))), 300, 240,
          fill=color, width=max(2, int(round(W(s * 0.13)))))
    ln(d, cx, cy - r * 1.3, cx, cy, color, s * 0.13)
def icon_phone(d, cx, cy, s, color):
    w = max(2, int(W(s * 0.10)))
    rr(d, (cx - s * 0.30, cy - s * 0.48, cx + s * 0.30, cy + s * 0.48), s * 0.12, outline=color, width=w)

# --- function-card glyphs (approx. of the shipped icons) ---
def g_speaker(d, cx, cy, s, c, cross=False, arc=True):
    poly(d, [(cx - s * 0.9, cy - s * 0.32), (cx - s * 0.42, cy - s * 0.32), (cx + s * 0.05, cy - s * 0.85),
             (cx + s * 0.05, cy + s * 0.85), (cx - s * 0.42, cy + s * 0.32), (cx - s * 0.9, cy + s * 0.32)], c)
    if arc:
        d.arc((int(W(cx - s * 0.15)), int(W(cy - s * 0.55)), int(W(cx + s * 0.85)), int(W(cy + s * 0.55))),
              -60, 60, fill=c, width=max(2, int(round(W(s * 0.16)))))
    if cross:
        ln(d, cx + s * 0.35, cy - s * 0.35, cx + s * 1.05, cy + s * 0.35, c, s * 0.16)
        ln(d, cx + s * 0.35, cy + s * 0.35, cx + s * 1.05, cy - s * 0.35, c, s * 0.16)
def g_camera(d, cx, cy, s, c):
    rr(d, (cx - s, cy - s * 0.62, cx + s, cy + s * 0.62), s * 0.22, outline=c, width=s * 0.16)
    poly(d, [(cx - s * 0.34, cy - s * 0.62), (cx - s * 0.18, cy - s * 0.95),
             (cx + s * 0.18, cy - s * 0.95), (cx + s * 0.34, cy - s * 0.62)], c)
    d.ellipse((int(W(cx - s * 0.32)), int(W(cy - s * 0.32)), int(W(cx + s * 0.32)), int(W(cy + s * 0.32))),
              outline=c, width=max(2, int(round(W(s * 0.15)))))
def g_recents(d, cx, cy, s, c):
    rr(d, (cx - s * 0.95, cy - s * 0.7, cx + s * 0.25, cy + s * 0.6), s * 0.22, outline=c, width=s * 0.15)
    rr(d, (cx - s * 0.25, cy - s * 0.6, cx + s * 0.95, cy + s * 0.7), s * 0.22, outline=c, width=s * 0.15)
def g_sun(d, cx, cy, s, c, plus=False, minus=False):
    for i in range(8):
        import math
        a = math.pi / 4 * i
        x0, y0 = cx + math.cos(a) * s * 0.62, cy + math.sin(a) * s * 0.62
        x1, y1 = cx + math.cos(a) * s * 1.0, cy + math.sin(a) * s * 1.0
        ln(d, x0, y0, x1, y1, c, s * 0.14)
    d.ellipse((int(W(cx - s * 0.42)), int(W(cy - s * 0.42)), int(W(cx + s * 0.42)), int(W(cy + s * 0.42))),
              outline=c, width=max(2, int(round(W(s * 0.16)))))
    if plus:
        ln(d, cx - s * 0.25, cy + s * 0.25, cx + s * 0.25, cy + s * 0.25, c, s * 0.14)
        ln(d, cx, cy, cx, cy + s * 0.5, c, s * 0.14)
    if minus:
        ln(d, cx - s * 0.25, cy + s * 0.25, cx + s * 0.25, cy + s * 0.25, c, s * 0.14)
def g_back(d, cx, cy, s, c):
    ln(d, cx + s * 0.85, cy, cx - s * 0.7, cy, c, s * 0.18)
    ln(d, cx - s * 0.7, cy, cx - s * 0.1, cy - s * 0.55, c, s * 0.18)
    ln(d, cx - s * 0.7, cy, cx - s * 0.1, cy + s * 0.55, c, s * 0.18)
def g_home(d, cx, cy, s, c):
    ln(d, cx - s * 0.9, cy, cx, cy - s * 0.85, c, s * 0.18)
    ln(d, cx, cy - s * 0.85, cx + s * 0.9, cy, c, s * 0.18)
    ln(d, cx - s * 0.62, cy - s * 0.05, cx - s * 0.62, cy + s * 0.8, c, s * 0.18)
    ln(d, cx + s * 0.62, cy - s * 0.05, cx + s * 0.62, cy + s * 0.8, c, s * 0.18)
    ln(d, cx - s * 0.62, cy + s * 0.8, cx + s * 0.62, cy + s * 0.8, c, s * 0.18)
def g_menu(d, cx, cy, s, c):
    for k in (-0.55, 0, 0.55):
        ln(d, cx - s * 0.85, cy + s * k, cx + s * 0.85, cy + s * k, c, s * 0.18)

FCN = [("音量+", "volup"), ("音量−", "voldown"), ("静音", "mute"), ("截屏", "shot"), ("多任务", "multi"),
       ("亮度−", "sunmin"), ("亮度+", "sunplus"), ("返回", "back"), ("主页", "home"), ("菜单", "menu")]

def fc_glyph(d, kind, cx, cy, s, c):
    if kind == "volup": g_speaker(d, cx, cy, s, c)
    elif kind == "voldown": g_speaker(d, cx, cy, s, c, arc=False)
    elif kind == "mute": g_speaker(d, cx, cy, s, c, cross=True, arc=True)
    elif kind == "shot": g_camera(d, cx, cy, s, c)
    elif kind == "multi": g_recents(d, cx, cy, s, c)
    elif kind == "sunmin": g_sun(d, cx, cy, s, c, minus=True)
    elif kind == "sunplus": g_sun(d, cx, cy, s, c, plus=True)
    elif kind == "back": g_back(d, cx, cy, s, c)
    elif kind == "home": g_home(d, cx, cy, s, c)
    elif kind == "menu": g_menu(d, cx, cy, s, c)

def chrome(d, WDP, active_tab=2):
    cy = 28
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
    tx = 14
    for i, t in enumerate(["键盘", "触控板", "媒体", "手柄", "双手"]):
        if i == active_tab:
            rr(d, (tx - 6, tab_y, tx + 40, tab_y + 26), 13, fill=SURF2)
            text(d, t, tx + 17, tab_y + 13, 11.5, TEXT, anchor="mm")
        else:
            text(d, t, tx + 17, tab_y + 13, 11.5, DIM, anchor="mm")
        tx += 46
    rr(d, (WDP - 66, tab_y, WDP - 14, tab_y + 26), 13, fill=SURF2)
    text(d, "旋转", WDP - 40, tab_y + 13, 11.5, TEXT, anchor="mm")

def split_row(d, WDP, y, pct="67"):
    text(d, "拖动调整功能区", 14, y, 10, DIM, anchor="lm")
    rr(d, (WDP / 2 - 46, y - 2, WDP / 2 + 46, y + 2), 2, fill=FAINT)
    text(d, "功能区 " + pct + "% / 留空 " + str(100 - int(pct)) + "%", WDP - 14, y, 10, DIM, anchor="rm")

def toolpad(d, WDP, y0, h):
    rr(d, (14, y0, WDP - 14, y0 + h), 13, fill=SURF2, outline=ACCENT, width=1.5)
    text(d, "触控板", WDP / 2, y0 + h / 2, 12.5, ACCENT, bold=True, anchor="mm")

def wide_key(d, box, label, left_arrow):
    rr(d, box, 12, fill=SURF2)
    x0, y0, x1, y1 = box
    cx, cy = x0 + 24, (y0 + y1) / 2
    g_seek(d, cx, cy, 8.2, TEXT, left=left_arrow)
    text(d, label, (x0 + x1) / 2 + 10, cy, 12.5, TEXT, anchor="mm")

def chips(d, x, y, w, h, items, active_idx, size=9.0):
    n = len(items)
    gap = 6
    cw = (w - gap * (n - 1)) / n
    for i, lb in enumerate(items):
        on = (i == active_idx)
        cx = x + i * (cw + gap)
        rr(d, (cx, y, cx + cw, y + h), 9, fill=(SURF3 if on else SURFACE), outline=(ACCENT if on else None), width=1.3)
        text(d, lb, cx + cw / 2, y + h / 2, size, (ACCENT if on else DIM), bold=on, anchor="mm")

def function_card(d, x0, x1, y0, y1):
    rr(d, (x0, y0, x1, y1), 16, fill=SURFACE)
    rh = (y1 - y0) / 2.0
    for ri in range(2):
        for ci in range(5):
            lb, kind = FCN[ri * 5 + ci]
            cx = x0 + (x1 - x0) * (ci + 0.5) / 5.0
            cy = y0 + rh * (ri + 0.5) - 4
            dot(d, cx, cy, 19, SURF2)
            fc_glyph(d, kind, cx, cy, 8.2, TEXT)
            text(d, lb, cx, cy + 27, 8.8, DIM, anchor="mm")

# ---------- A: merge into the existing prev / next keys ----------
def draw_a(land=False, out="mock-media-a.png"):
    WDP, HDP = (873, 393) if land else (393, 873)
    img = Image.new("RGB", (int(W(WDP)), int(W(HDP))), BG); d = ImageDraw.Draw(img)
    chrome(d, WDP, 2)
    if land:
        split_row(d, WDP, 108, "62")
        toolpad(d, WDP, 120, 32)
        mx0, mx1 = 14, 512
        my0, my1 = 164, 372
        fx0, fx1 = 522, WDP - 14
    else:
        text(d, "上方留空 · 拖动下方分隔条调整功能区高度", WDP / 2, 176, 10.5, FAINT, anchor="mm")
        text(d, "（功能区贴着屏幕底部，方便拇指操作）", WDP / 2, 196, 9.5, FAINT, anchor="mm")
        split_row(d, WDP, 250, "67")
        toolpad(d, WDP, 268, 44)
        mx0, mx1 = 14, WDP - 14
        my0, my1 = 322, 492
        fx0, fx1 = 14, WDP - 14
    rr(d, (mx0, my0, mx1, my1), 16, fill=SURFACE)
    if land:
        text(d, "上一段 / 下一段发送", mx0 + 18, my0 + 20, 9.5, DIM, anchor="lm")
        chips(d, mx0 + 160, my0 + 10, mx1 - mx0 - 178, 22, ["媒体键", "方向键 ↑↓", "PageUp·Dn", "空格"], 1, size=8.4)
        icons_y = my0 + 78
        wy = my0 + 112
    else:
        text(d, "上一段 / 下一段发送", mx0 + 18, my0 + 18, 10, DIM, anchor="lm")
        chips(d, mx0 + 18, my0 + 30, mx1 - mx0 - 36, 26, ["媒体键（默认）", "方向键 ↑↓", "PageUp·Dn", "空格"], 1, size=8.4)
        icons_y = my0 + 92
        wy = my0 + 132
    s = 12 if land else 14
    cx_prev = mx0 + (mx1 - mx0) * 0.17
    cx_play = (mx0 + mx1) / 2
    cx_next = mx0 + (mx1 - mx0) * 0.83
    chev_updown(d, cx_prev, icons_y, s * 0.62, s * 0.5, TEXT, 2.2, up=True)
    text(d, "上一集", cx_prev, icons_y + 24, 8.6, DIM, anchor="mm")
    g_play(d, cx_play, icons_y, s * 1.05, TEXT)
    text(d, "播放 / 暂停", cx_play, icons_y + 24, 8.6, DIM, anchor="mm")
    chev_updown(d, cx_next, icons_y, s * 0.62, s * 0.5, TEXT, 2.2, up=False)
    text(d, "下一集", cx_next, icons_y + 24, 8.6, DIM, anchor="mm")
    gap = 10
    w = (mx1 - mx0 - 36 - gap) / 2
    wide_key(d, (mx0 + 18, wy, mx0 + 18 + w, wy + 42), "−10s", True)
    wide_key(d, (mx0 + 18 + w + gap, wy, mx1 - 18, wy + 42), "+10s", False)
    if land:
        function_card(d, fx0, fx1, my0, my1)
    else:
        function_card(d, fx0, fx1, my1 + 12, HDP - 22)
    note = "方案 A（推荐）：媒体卡顶部一行「上一段 / 下一段发送什么」，选「方向键」时 ⏮ ⏭ 就变成「上一集 / 下一集」"
    text(d, note, 14, HDP - 8, 8.2, FAINT, anchor="ls")
    img.save(os.path.join(ROOT, out)); print("saved", out)

# ---------- B: two dedicated flip keys on the media card ----------
def draw_b():
    WDP, HDP = 393, 873
    img = Image.new("RGB", (int(W(WDP)), int(W(HDP))), BG); d = ImageDraw.Draw(img)
    chrome(d, WDP, 2)
    text(d, "上方留空 · 拖动下方分隔条调整功能区高度", WDP / 2, 176, 10.5, FAINT, anchor="mm")
    text(d, "（功能区贴着屏幕底部，方便拇指操作）", WDP / 2, 196, 9.5, FAINT, anchor="mm")
    split_row(d, WDP, 238, "72")
    toolpad(d, WDP, 254, 42)
    mx0, mx1 = 14, WDP - 14
    my0, my1 = 306, 528
    rr(d, (mx0, my0, mx1, my1), 16, fill=SURFACE)
    icons_y = my0 + 42
    cx_prev = mx0 + (mx1 - mx0) * 0.17
    cx_play = (mx0 + mx1) / 2
    cx_next = mx0 + (mx1 - mx0) * 0.83
    g_prev(d, cx_prev, icons_y, 13, TEXT); text(d, "上一首", cx_prev, icons_y + 26, 8.6, DIM, anchor="mm")
    g_play(d, cx_play, icons_y, 14.5, TEXT); text(d, "播放 / 暂停", cx_play, icons_y + 26, 8.6, DIM, anchor="mm")
    g_next(d, cx_next, icons_y, 13, TEXT); text(d, "下一首", cx_next, icons_y + 26, 8.6, DIM, anchor="mm")
    gap = 10
    w = (mx1 - mx0 - 36 - gap) / 2
    wy = my0 + 78
    wide_key(d, (mx0 + 18, wy, mx0 + 18 + w, wy + 42), "−10s", True)
    wide_key(d, (mx0 + 18 + w + gap, wy, mx1 - 18, wy + 42), "+10s", False)
    fy = wy + 56
    fw = (mx1 - mx0 - 36 - gap * 2) / 2
    for i, (up, lb) in enumerate([(True, "上一集"), (False, "下一集")]):
        x = mx0 + 18 + i * (fw + gap)
        rr(d, (x, fy, x + fw, fy + 46), 12, fill=SURF2, outline=ACCENT, width=1.4)
        chev_updown(d, x + 26, fy + 23, 7.5, 2.6, ACCENT, 2.0, up)
        text(d, lb, x + fw / 2 + 13, fy + 23, 12.5, ACCENT, bold=True, anchor="mm")
    text(d, "按住连续切", mx1 - 20, fy + 60, 8.4, FAINT, anchor="rm")
    function_card(d, mx0, mx1, my1 + 10, HDP - 22)
    note = "方案 B：媒体卡里再插一行两颗专用翻页键（固定发方向键 ↑ / ↓），⏮ ⏭ 仍发媒体键"
    text(d, note, 14, HDP - 8, 8.2, FAINT, anchor="ls")
    img.save(os.path.join(ROOT, "mock-media-b.png")); print("saved mock-media-b.png")

draw_a(False, "mock-media-a.png")
draw_a(True, "mock-media-a-land.png")
draw_b()
