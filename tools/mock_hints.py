# -*- coding: utf-8 -*-
"""v0.39.3 mockups: gesture hint block inside the touch area + top-right 提示 toggle."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
BG      = (0x0F, 0x11, 0x15)
SURFACE = (0x17, 0x1B, 0x22)
SURF    = (0x1A, 0x1F, 0x28)
SURF2   = (0x1F, 0x25, 0x30)
KEY     = (0x26, 0x2D, 0x3A)
ACCENT  = (0x4C, 0x8D, 0xFF)
TEXT    = (0xE9, 0xEC, 0xF2)
DIM     = (0x98, 0xA1, 0xB3)
FAINT   = (0x5C, 0x64, 0x74)
OK      = (0x3D, 0xDC, 0x84)

FONT_R = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"
_cache = {}
def font(size_dp, bold=False):
    key = (round(size_dp, 2), bold)
    if key not in _cache:
        _cache[key] = ImageFont.truetype(FONT_B if bold else FONT_R, int(round(size_dp * K)))
    return _cache[key]

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
    d.ellipse((int(round(W(cx - r))), int(round(W(cy - r))),
               int(round(W(cx + r))), int(round(W(cy + r)))), fill=color)

def icon_power(d, cx, cy, s, color):
    r = s * 0.36
    d.arc((int(W(cx - r)), int(W(cy - r)), int(W(cx + r)), int(W(cy + r))), start=300, end=240,
          fill=color, width=max(2, int(W(s * 0.13))))
    d.line((int(W(cx)), int(W(cy - r * 1.30)), int(W(cx)), int(W(cy))), fill=color, width=max(2, int(W(s * 0.13))))

def icon_phone(d, cx, cy, s, color):
    w = max(2, int(W(s * 0.10)))
    rr(d, (cx - s * 0.30, cy - s * 0.48, cx + s * 0.30, cy + s * 0.48), s * 0.12, outline=color, width=w)
    d.line((int(W(cx - s * 0.10)), int(W(cy + s * 0.34)), int(W(cx + s * 0.10)), int(W(cy + s * 0.34))),
           fill=color, width=w)

def chevron_down(d, cx, cy, s, color):
    w = max(3, int(W(s * 0.34)))
    d.line((int(W(cx - s * 0.75)), int(W(cy - s * 0.38)), int(W(cx)), int(W(cy + s * 0.38))), fill=color, width=w)
    d.line((int(W(cx)), int(W(cy + s * 0.38)), int(W(cx + s * 0.75)), int(W(cy - s * 0.38))), fill=color, width=w)

def arrow(d, cx, cy, s, color, up=True):
    w = max(3, int(W(s * 0.13)))
    tip = cy - s * 0.55 if up else cy + s * 0.55
    tail = cy + s * 0.55 if up else cy - s * 0.55
    d.line(((int(W(cx)), int(W(tail))), (int(W(cx)), int(W(tip)))), fill=color, width=w)
    d.line(((int(W(cx)), int(W(tip))), (int(W(cx - s * 0.30)), int(W(tip + (s * 0.30 if up else -s * 0.30))))), fill=color, width=w)
    d.line(((int(W(cx)), int(W(tip))), (int(W(cx + s * 0.30)), int(W(tip + (s * 0.30 if up else -s * 0.30))))), fill=color, width=w)

# ---------------- page ----------------
def draw_page(land, mode, hints_on, out):
    wdp, hdp = (873, 393) if land else (393, 873)
    img = Image.new("RGB", (int(W(wdp)), int(W(hdp))), BG)
    d = ImageDraw.Draw(img)

    # --- top chrome (portrait two rows / landscape one row) ---
    cy = 30 if not land else 14
    rr(d, (14, cy, 68, cy + 36), 12, fill=SURF2)
    icon_power(d, 41, cy + 18, 18, ACCENT)
    rr(d, (74, cy, 152, cy + 36), 13, fill=SURF2, outline=ACCENT, width=1.6)
    icon_phone(d, 90, cy + 18, 17, ACCENT)
    text(d, "设备", 100, cy + 18, 12.5, ACCENT, bold=True, anchor="lm")
    dot(d, 164, cy + 18, 4, OK)
    text(d, "已连接", 173, cy + 9, 12, TEXT, bold=True, anchor="lm")
    text(d, "iQOO Pad2", 173, cy + 25, 9.5, DIM, anchor="lm")
    mx = 250
    rr(d, (mx, cy + 4, mx + 62, cy + 32), 14, fill=SURF2)
    text(d, "平板" if mode == "pad" else "电脑", mx + 11, cy + 18, 11.5, TEXT, anchor="lm")
    chevron_down(d, mx + 50, cy + 18, 5.2, DIM)
    rr(d, (mx + 68, cy + 4, mx + 120, cy + 32), 14, fill=SURF2)
    text(d, "帮助", mx + 94, cy + 18, 11.5, DIM, anchor="mm")

    tab_y = cy + 44
    tabs = ["键盘", "触控板", "媒体", "手柄", "双手"]
    tx = 18
    for i, t in enumerate(tabs):
        tw = 44
        if i == 1:
            rr(d, (tx - 8, tab_y, tx + tw - 8, tab_y + 26), 13, fill=SURF2)
            text(d, t, tx + tw / 2 - 8, tab_y + 13, 11.5, TEXT, anchor="mm")
        else:
            text(d, t, tx + tw / 2 - 8, tab_y + 13, 11.5, DIM, anchor="mm")
        tx += tw
    rr(d, (wdp - 66, tab_y, wdp - 14, tab_y + 26), 13, fill=SURF2)
    text(d, "旋转", wdp - 40, tab_y + 13, 11.5, TEXT, anchor="mm")

    # --- splitter ---
    sp_y = 292 if not land else 96
    text(d, "拖动调整功能区", 14, sp_y, 10, DIM, anchor="lm")
    rr(d, (wdp / 2 - 46, sp_y - 2, wdp / 2 + 46, sp_y + 2), 2, fill=FAINT)
    text(d, "功能区 58% / 留空 42%", wdp - 14, sp_y, 10, DIM, anchor="rm")

    # --- pad area ---
    if land:
        pad_y0, pad_y1 = 110, 250
        bar_x0, bar_x1 = 14, 74
        pad_x0, pad_x1 = 86, 596
        col_x0, col_x1 = 604, 720
        nav_y0, nav_y1 = 262, 316
    else:
        pad_y0, pad_y1 = 316, 566
        bar_x0, bar_x1 = 14, 74
        pad_x0, pad_x1 = 86, 322
        col_x0, col_x1 = 330, 379
        nav_y0, nav_y1 = 580, 636

    # left vertical bar: 数字 / 键盘 / 菜单
    labels = ["数字", "键盘", "菜单"]
    cell_h = (pad_y1 - pad_y0) / 3.0 - 6
    for i, lb in enumerate(labels):
        y = pad_y0 + i * (cell_h + 9)
        rr(d, (bar_x0, y, bar_x1, y + cell_h), 12, fill=SURF)
        for r in range(3):
            for c in range(3):
                dot(d, (bar_x0 + bar_x1) / 2 + (c - 1) * 6, y + 18 + (r - 1) * 6, 1.4, DIM)
        text(d, lb, (bar_x0 + bar_x1) / 2, y + cell_h - 14, 10, DIM, anchor="mm")

    # pad card
    rr(d, (pad_x0, pad_y0, pad_x1, pad_y1), 16, fill=SURFACE)
    # right scroll column (rail + two arrow cells)
    col_w = (col_x1 - col_x0)
    half = (pad_y1 - pad_y0 - 8) / 2
    rr(d, (col_x0, pad_y0, col_x1, pad_y0 + half), 14, fill=SURF)
    rr(d, (col_x0, pad_y0 + half + 8, col_x1, pad_y1), 14, fill=SURF)
    arrow(d, (col_x0 + col_x1) / 2, pad_y0 + half / 2, 26, TEXT)
    arrow(d, (col_x0 + col_x1) / 2, pad_y0 + half + 8 + half / 2, 26, TEXT, up=False)
    rail_x = (col_x0 + col_x1) / 2
    rr(d, (rail_x - 1.5, pad_y0 + 6, rail_x + 1.5, pad_y1 - 6), 1.5, fill=FAINT)

    # --- hint toggle pill (top-right inside pad) ---
    pw = 54
    px1 = pad_x1 - 12
    pill_y0, pill_y1 = pad_y0 + 12, pad_y0 + 32
    if hints_on:
        rr(d, (px1 - pw, pill_y0, px1, pill_y1), 10, fill=SURF2, outline=ACCENT, width=1.2)
        # small "eye" icon
        ecx, ecy = px1 - pw + 12, (pill_y0 + pill_y1) / 2
        d.ellipse((int(W(ecx - 4.5)), int(W(ecy - 3)), int(W(ecx + 4.5)), int(W(ecy + 3))),
                  outline=ACCENT, width=max(2, int(W(0.9))))
        dot(d, ecx, ecy, 1.4, ACCENT)
        text(d, "提示 开", px1 - 8, (pill_y0 + pill_y1) / 2, 9.5, ACCENT, anchor="rm")
    else:
        rr(d, (px1 - pw, pill_y0, px1, pill_y1), 10, fill=SURF2)
        ecx, ecy = px1 - pw + 12, (pill_y0 + pill_y1) / 2
        d.ellipse((int(W(ecx - 4.5)), int(W(ecy - 3)), int(W(ecx + 4.5)), int(W(ecy + 3))),
                  outline=DIM, width=max(2, int(W(0.9))))
        text(d, "提示 关", px1 - 8, (pill_y0 + pill_y1) / 2, 9.5, DIM, anchor="rm")

    # --- hint block ---
    if hints_on:
        if mode == "pad":
            title = "平板模式 · 手势"
            lines = [
                "单指移动 · 点=左键 · 双指点=右键",
                "双指滑=滚动 · 双指右滑=返回",
                "三指下滑=主页 · 三指上/左右=多任务",
                "最右箭头条：按住不动连滚 / 上下拖=滚轮",
            ]
        else:
            title = "电脑模式 · 手势"
            lines = [
                "单指移动 · 点=左键 · 双指滑=滚动",
                "双指横滑=横向滚动 · 双指右滑=后退",
                "三指上=任务视图 · 下=显示桌面 · 左右=切窗口",
                "四指左右=切虚拟桌面 · 箭头条=滚轮",
            ]
        lh = 15
        block_h = 14 + lh * len(lines)
        by = pad_y1 - 14 - block_h
        text(d, title, pad_x0 + 14, by, 9, ACCENT, bold=True)
        for i, line_txt in enumerate(lines):
            text(d, line_txt, pad_x0 + 14, by + 14 + i * lh, 9.5, DIM)
    else:
        text(d, "触控区", (pad_x0 + pad_x1) / 2 - 14, (pad_y0 + pad_y1) / 2 - 6, 11, FAINT, anchor="mm")

    # --- bottom nav 4 cells ---
    nav = ["返回", "主页", "后台", "中键"]
    gap = 8
    cw = (wdp - 28 - gap * 3) / 4
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

    # caption
    text(d, "提示块在触控区左下角 · 右上角「提示 开/关」", 14, hdp - 26, 11, FAINT)
    img.save(os.path.join(ROOT, out))
    print("saved", out, img.size)

draw_page(False, "pad", True,  "mock-hint-pad.png")
draw_page(False, "pc",  True,  "mock-hint-pc.png")
draw_page(False, "pad", False, "mock-hint-off.png")
draw_page(True,  "pc",  True,  "mock-hint-pc-land.png")
draw_page(True,  "pad", True,  "mock-hint-pad-land.png")
