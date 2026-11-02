
# -*- coding: utf-8 -*-
"""v0.23 mockups v4: panel drag-handle at top-left + vertical 返回/主页/后台 stack at bottom-right."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from mock_gp4 import (P, BG, SURF, SURF2, KEY, TEXT, DIM, ACCENT, PANEL_LINE,
                      landscape_chrome, portrait_chrome, pill, dpad, abxy, touch_panel,
                      caption, rr, txt, chev)

W_L, H_L = 873, 393
W_P, H_P = 393, 873
CARD = (21, 25, 33)


def arrow_handle(d, x, y, s=20, col=ACCENT):
    """small ⇔ grip"""
    rr(d, (x, y, x + s, y + s), 7, fill=SURF2, outline=col, width=1.4)
    cy = y + s / 2
    chev(d, x + s * 0.32, cy, 270, s * 0.28, col, 1.4)
    chev(d, x + s * 0.68, cy, 90, s * 0.28, col, 1.4)


def action_chip(d, x, y, label, w, h=27):
    rr(d, (x, y, x + w, y + h), 9, fill=SURF2, outline=ACCENT, width=1.4)
    txt(d, label, x + w / 2, y + h / 2, 11, ACCENT, anchor="mm")


def dropdown_chip(d, x, y, label, w, h=27):
    rr(d, (x, y, x + w, y + h), 9, fill=SURF2)
    txt(d, label, x + 11, y + h / 2, 11, TEXT, anchor="lm")
    chev(d, x + w - 13, y + h / 2, 180, 7, DIM, 1.6)


def panel(d, box, land, dragging=False):
    x0, y0, x1, y1 = box
    rr(d, box, 16, fill=CARD, outline=ACCENT if dragging else PANEL_LINE, width=2 if dragging else 1.6)
    # top-left: drag grip + title
    arrow_handle(d, x0 + 10, y0 + 10, 22)
    txt(d, "触控板", x0 + 40, y0 + 21, 10, DIM, anchor="lm")
    if dragging:
        txt(d, "左右拖动 · 松手保存", x0 + 108, y0 + 21, 10, ACCENT, anchor="lm")

    col_w = 52
    col_top, col_bot = y0 + 44, y0 + (206 if land else 268)
    rt = (x1 - 10 - col_w, col_top, x1 - 10, col_bot)
    rr(d, rt, 12, fill=SURF2)
    h2 = (rt[1] + rt[3]) / 2
    chev(d, (rt[0] + rt[2]) / 2, rt[1] + (h2 - rt[1]) / 2, 0, 15, TEXT, 3.4)
    chev(d, (rt[0] + rt[2]) / 2, h2 + (rt[3] - h2) / 2, 180, 15, TEXT, 3.4)

    # vertical nav stack, bottom-right
    nw, nh, ng = (78, 26, 6) if land else (74, 28, 7)
    stack_h = nh * 3 + ng * 2
    nx1 = x1 - 10
    ny1 = y1 - 10
    for i, lab in enumerate(("返回", "主页", "后台")):
        y = ny1 - stack_h + i * (nh + ng)
        rr(d, (nx1 - nw, y, nx1, y + nh), 8, fill=SURF2)
        txt(d, lab, nx1 - nw / 2, y + nh / 2, 10, anchor="mm")

    # touch area label sits left of the scroll column, above the nav stack
    cx = (x0 + 20 + rt[0] - 14) / 2
    cy = (y0 + 44 + (ny1 - stack_h) - 8) / 2
    d.ellipse([P(cx - 8), P(cy - 8), P(cx + 8), P(cy + 8)], outline=DIM, width=max(1, P(1.2)))
    d.ellipse([P(cx - 3), P(cy - 3), P(cx + 3), P(cy + 3)], fill=DIM)
    txt(d, "触控区", cx, cy + 15, 12, DIM, anchor="ma")
    if land:
        txt(d, "单指移动 · 轻点＝左键 · 双指点击＝右键", cx, cy + 31, 9, (110, 120, 138), anchor="ma")
    else:
        txt(d, "单指移动 · 轻点＝左键", cx, cy + 31, 9, (110, 120, 138), anchor="ma")
        txt(d, "双指点击＝右键 · 双指滑＝滚动", cx, cy + 44, 9, (110, 120, 138), anchor="ma")
    if dragging:
        # ghost of the alternative position
        rr(d, (x0 - 62, y0, x1 - 62, y1), 16, outline=(70, 84, 108), width=1.2)


def base_landscape():
    im = Image.new("RGB", (P(W_L), P(H_L + 48)), BG)
    d = ImageDraw.Draw(im)
    landscape_chrome(d, W_L)
    top = 56
    pill(d, (16, top + 14, 112, top + 52), "L1")
    pill(d, (W_L - 112, top + 14, W_L - 16, top + 52), "R1")
    pill(d, (W_L / 2 - 76, top + 14, W_L / 2 - 8, top + 41), "SELECT", 9)
    pill(d, (W_L / 2 + 8, top + 14, W_L / 2 + 76, top + 41), "START", 9)
    dpad(d, 152, top + 209, 58, 54)
    abxy(d, 721, top + 209, 29, 43)
    touch_panel(d, (276, top + 69, 596, top + 249))
    dropdown_chip(d, W_L / 2 - 128, H_L - 41, "移动盘", 78)
    dropdown_chip(d, W_L / 2 - 42, H_L - 41, "触控 中间", 84)
    action_chip(d, W_L - 16 - 82, top + 64, "触控板", 82)
    return im, d


def land(path, dragging=False, offset=0, note=""):
    im, d = base_landscape()
    dim = Image.new("RGBA", im.size, (0, 0, 0, 120))
    im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
    d = ImageDraw.Draw(im)
    x1 = W_L - 56 - offset
    panel(d, (x1 - 392, 64, x1, H_L - 10), True, dragging)
    lines = ["浮层左上角有个「⇔」把手：长按后左右拖，松手就把位置存下来（下次打开还在那儿）",
             note]
    caption(d, W_L, H_L, lines)
    im.save(path)
    return path


def base_portrait():
    im = Image.new("RGB", (P(W_P), P(H_P + 48)), BG)
    d = ImageDraw.Draw(im)
    portrait_chrome(d, W_P)
    top = 128
    dropdown_chip(d, 14, top + 10, "移动盘", 76)
    dropdown_chip(d, 98, top + 10, "触控 中间", 82)
    action_chip(d, W_P - 14 - 80, top + 10, "触控板", 80)
    touch_panel(d, (26, top + 60, 367, top + 330))
    pill(d, (16, top + 350, 112, top + 388), "L1")
    pill(d, (124, top + 355, 192, top + 382), "SELECT", 9)
    pill(d, (200, top + 355, 268, top + 382), "START", 9)
    pill(d, (W_P - 112, top + 350, W_P - 16, top + 388), "R1")
    dpad(d, 99, top + 480, 54, 50)
    abxy(d, 300, top + 480, 27, 40)
    return im, d


def portrait(path, dragging=False, offset=0):
    im, d = base_portrait()
    dim = Image.new("RGBA", im.size, (0, 0, 0, 120))
    im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
    d = ImageDraw.Draw(im)
    x1 = W_P - 48 - offset
    panel(d, (x1 - 252, H_P - 66 - 430, x1, H_P - 66), False, dragging)
    caption(d, W_P, H_P, ["竖屏：把手同样在左上角，可左右拖；返回/主页/后台竖排在浮层右下角",
                          "滚动条缩到中段，给右下角的竖排导航让出位置"])
    im.save(path)
    return path


if __name__ == "__main__":
    base = "C:/Users/22453/Desktop/phoneKB/"
    print(land(base + "mock-gp8-l.png", False, 0, "右下角竖排：返回 / 主页 / 后台；滚动 ↑↓ 缩到中段，两者不打架"))
    print(land(base + "mock-gp8-l-drag.png", True, 70, "拖动中：把手变蓝，旁边显示虚影框，提示「左右拖动 · 松手保存」"))
    print(portrait(base + "mock-gp8-p.png", False, 0))
