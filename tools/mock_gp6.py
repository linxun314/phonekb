
# -*- coding: utf-8 -*-
"""v0.23 mockups v2: touchpad trigger at the top-left, popup panel docked to the right (not fullscreen)."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from mock_gp4 import (P, BG, SURF, SURF2, KEY, TEXT, DIM, ACCENT, PANEL_LINE,
                      landscape_chrome, portrait_chrome, chip as chip4, pill, dpad, abxy, touch_panel,
                      caption, rr, txt, chev, K)

W_L, H_L = 873, 393
W_P, H_P = 393, 873
CARD = (21, 25, 33)


def action_chip(d, x, y, label, w, h=27, arrow=False):
    rr(d, (x, y, x + w, y + h), 9, fill=SURF2, outline=ACCENT, width=1.4)
    txt(d, label, x + (w - 10) / 2, y + h / 2, 11, ACCENT, anchor="mm")
    if arrow:
        chev(d, x + w - 10, y + h / 2, 180, 6, ACCENT, 1.4)


def dropdown_chip(d, x, y, label, w, h=27, active=False, arrow=True):
    rr(d, (x, y, x + w, y + h), 9, fill=SURF2, outline=ACCENT if active else None, width=1.2)
    txt(d, label, x + 11, y + h / 2, 11, ACCENT if active else TEXT, anchor="lm")
    if arrow:
        chev(d, x + w - 13, y + h / 2, 180, 7, DIM, 1.6)


def panel_right(d, box, land):
    """Compact touchpad overlay docked to the right; scroll column inside its right edge."""
    x0, y0, x1, y1 = box
    rr(d, box, 16, fill=CARD, outline=PANEL_LINE, width=1.6)
    txt(d, "触控板", x0 + 16, y0 + 14, 10, DIM)
    col_w = 54
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
    bw = 74 if land else 62
    gap = 7
    total = bw * 3 + gap * 2
    bx = (x0 + 16 + x1 - 10) / 2 - total / 2
    for i, lab in enumerate(("返回", "主页", "后台")):
        x = bx + i * (bw + gap)
        rr(d, (x, y1 - 52, x + bw, y1 - 24), 9, fill=SURF2)
        txt(d, lab, x + bw / 2, y1 - 38, 10, anchor="mm")


def landscape(path, key_at, panel=True):
    im = Image.new("RGB", (P(W_L), P(H_L + 48)), BG)
    d = ImageDraw.Draw(im)
    landscape_chrome(d, W_L)
    top = 56
    # placeholder page behind the popup
    pill(d, (16, top + 14, 112, top + 52), "L1")
    pill(d, (W_L - 112, top + 14, W_L - 16, top + 52), "R1")
    pill(d, (W_L / 2 - 76, top + 14, W_L / 2 - 8, top + 41), "SELECT", 9)
    pill(d, (W_L / 2 + 8, top + 14, W_L / 2 + 76, top + 41), "START", 9)
    dpad(d, 152, top + 209, 58, 54)
    abxy(d, 721, top + 209, 29, 43)
    touch_panel(d, (276, top + 69, 596, top + 249))
    dropdown_chip(d, W_L / 2 - 128, H_L - 41, "移动盘", 78)
    dropdown_chip(d, W_L / 2 - 42, H_L - 41, "触控 中间", 84)

    if key_at == "left":
        action_chip(d, 16, top + 64, "触控板", 82)
    else:
        action_chip(d, W_L - 98, top + 64, "触控板", 82)

    if panel:
        dim = Image.new("RGBA", im.size, (0, 0, 0, 120))
        im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
        d = ImageDraw.Draw(im)
        panel_right(d, (W_L - 16 - 392, top + 8, W_L - 16, H_L - 10), True)
        caption(d, W_L, H_L, [
            "触控键在" + ("左上角" if key_at == "left" else "右上角") + "：「L1」下面一格，拇指一伸就到",
            "点开后浮层只占右侧 392×313dp（约 45% 屏宽），左半屏的方向盘 / ABXY 照样看得见、照样能按"])
    else:
        caption(d, W_L, H_L, ["触控键位置：" + ("左上角（L1 下面）" if key_at == "left" else "右上角（R1 下面）"),
                              "点它 → 右侧弹出浮层，不铺满屏幕"])
    im.save(path)
    return path


def portrait(path, key_at, panel=True):
    im = Image.new("RGB", (P(W_P), P(H_P + 48)), BG)
    d = ImageDraw.Draw(im)
    portrait_chrome(d, W_P)
    top = 128
    if key_at == "left":
        action_chip(d, 14, top + 10, "触控板", 80)
        dropdown_chip(d, 102, top + 10, "移动盘", 76)
        dropdown_chip(d, 186, top + 10, "触控 中间", 82)
    else:
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

    if panel:
        dim = Image.new("RGBA", im.size, (0, 0, 0, 120))
        im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
        d = ImageDraw.Draw(im)
        px1, py2 = W_P - 14, H_P - 20
        px0, py1 = px1 - 252, py2 - 430
        panel_right(d, (px0, py0 if False else py1 - 430 + 430 - 430, px1, py2) if False else (px0, py1, px1, py2), False)
        caption(d, W_P, H_P, [
            "竖屏：触控键在" + ("最左（左上角）" if key_at == "left" else "最右（右上角）"),
            "点开后浮层靠右下角弹出 252×430dp，右手拇指原地就能滑，左手不用离开握持"])
    else:
        caption(d, W_P, H_P, ["触控键位置：" + ("左上角" if key_at == "left" else "右上角")])
    im.save(path)
    return path


if __name__ == "__main__":
    base = "C:/Users/22453/Desktop/phoneKB/"
    print(landscape(base + "mock-gp6-l-left.png", "left"))
    print(landscape(base + "mock-gp6-l-right.png", "right"))
    print(portrait(base + "mock-gp6-p-left.png", "left"))
    print(portrait(base + "mock-gp6-p-right.png", "right"))
