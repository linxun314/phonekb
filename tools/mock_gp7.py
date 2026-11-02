
# -*- coding: utf-8 -*-
"""v0.23 mockups v3: touchpad key at TOP-RIGHT, popup panel pulled toward the centre (not hugging the right edge)."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from mock_gp4 import (P, BG, SURF, SURF2, KEY, TEXT, DIM, ACCENT, PANEL_LINE,
                      landscape_chrome, portrait_chrome, pill, dpad, abxy, touch_panel,
                      caption, rr, txt, chev)

W_L, H_L = 873, 393
W_P, H_P = 393, 873
CARD = (21, 25, 33)


def action_chip(d, x, y, label, w, h=27):
    rr(d, (x, y, x + w, y + h), 9, fill=SURF2, outline=ACCENT, width=1.4)
    txt(d, label, x + w / 2, y + h / 2, 11, ACCENT, anchor="mm")


def dropdown_chip(d, x, y, label, w, h=27, active=False):
    rr(d, (x, y, x + w, y + h), 9, fill=SURF2, outline=ACCENT if active else None, width=1.2)
    txt(d, label, x + 11, y + h / 2, 11, ACCENT if active else TEXT, anchor="lm")
    chev(d, x + w - 13, y + h / 2, 180, 7, DIM, 1.6)


def panel(d, box, land):
    x0, y0, x1, y1 = box
    rr(d, box, 16, fill=CARD, outline=PANEL_LINE, width=1.6)
    txt(d, "触控板", x0 + 16, y0 + 14, 10, DIM)
    col_w = 52
    rt = (x1 - 10 - col_w, y0 + 10, x1 - 10, y1 - 68)
    rr(d, rt, 12, fill=SURF2)
    h2 = (rt[1] + rt[3]) / 2
    chev(d, (rt[0] + rt[2]) / 2, rt[1] + (h2 - rt[1]) / 2, 0, 15, TEXT, 3.4)
    chev(d, (rt[0] + rt[2]) / 2, h2 + (rt[3] - h2) / 2, 180, 15, TEXT, 3.4)
    cx = (x0 + 16 + rt[0] - 10) / 2
    cy = (y0 + 34 + y1 - 62) / 2
    d.ellipse([P(cx - 8), P(cy - 8), P(cx + 8), P(cy + 8)], outline=DIM, width=max(1, P(1.2)))
    d.ellipse([P(cx - 3), P(cy - 3), P(cx + 3), P(cy + 3)], fill=DIM)
    txt(d, "触控区", cx, cy + 15, 12, DIM, anchor="ma")
    if land:
        txt(d, "单指移动 · 轻点＝左键 · 双指点击＝右键", cx, cy + 31, 9, (110, 120, 138), anchor="ma")
    else:
        txt(d, "单指移动 · 轻点＝左键", cx, cy + 31, 9, (110, 120, 138), anchor="ma")
        txt(d, "双指点击＝右键 · 双指滑＝滚动", cx, cy + 44, 9, (110, 120, 138), anchor="ma")
    bw = 74 if land else 60
    gap = 7
    total = bw * 3 + gap * 2
    bx = (x0 + 16 + x1 - 10) / 2 - total / 2
    for i, lab in enumerate(("返回", "主页", "后台")):
        x = bx + i * (bw + gap)
        rr(d, (x, y1 - 52, x + bw, y1 - 24), 9, fill=SURF2)
        txt(d, lab, x + bw / 2, y1 - 38, 10, anchor="mm")


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


def land(path, right_margin, panel_on=True, note=""):
    im, d = base_landscape()
    top = 56
    if panel_on:
        dim = Image.new("RGBA", im.size, (0, 0, 0, 120))
        im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
        d = ImageDraw.Draw(im)
        x1 = W_L - right_margin
        panel(d, (x1 - 392, top + 8, x1, H_L - 10), True)
        caption(d, W_L, H_L, [
            "触控键在右上角（R1 下面一格）；浮层右边留 " + str(right_margin) + "dp，整块往屏幕中间挪",
            note])
    else:
        caption(d, W_L, H_L, ["触控键在右上角（R1 下面一格），拇指从 R1 往下一滑就到",
                              "点它 → 右侧弹出浮层，不铺满屏幕"])
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


def portrait(path, right_margin, panel_on=True):
    im, d = base_portrait()
    if panel_on:
        dim = Image.new("RGBA", im.size, (0, 0, 0, 120))
        im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
        d = ImageDraw.Draw(im)
        x1 = W_P - right_margin
        panel(d, (x1 - 252, H_P - 66 - 430, x1, H_P - 66), False)
        caption(d, W_P, H_P, ["竖屏：触控键在右上角；浮层右留 " + str(right_margin) + "dp，整体往中间靠",
                              "浮层 252×430dp，右手拇指按住不用横跨屏幕"])
    else:
        caption(d, W_P, H_P, ["竖屏：触控键在右上角（三键行最右）"])
    im.save(path)
    return path


if __name__ == "__main__":
    base = "C:/Users/22453/Desktop/phoneKB/"
    print(land(base + "mock-gp7-l-key.png", 16, panel_on=False))
    print(land(base + "mock-gp7-l-a.png", 56, note="浮层 392×313dp，右留 56dp（比上一版往左挪了 40dp）"))
    print(land(base + "mock-gp7-l-b.png", 130, note="浮层再往中间挪一档：右留 130dp，触控区更居中，但会被方向盘挡住一部分"))
    print(portrait(base + "mock-gp7-p.png", 48))
