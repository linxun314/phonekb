
# -*- coding: utf-8 -*-
"""v0.23 mockups: gamepad page gets an on-demand touchpad-overlay key (like the keyboard page's)."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from mock_gp4 import (P, BG, SURF, SURF2, KEY, TEXT, DIM, ACCENT, PANEL_LINE,
                      landscape_chrome, portrait_chrome, chip as chip4, pill, dpad, abxy, touch_panel,
                      caption, rr, txt, chev, K)

W_L, H_L = 873, 393
W_P, H_P = 393, 873

def chip5(d, x, y, label, w, active=False, faint=False):
    box = (x, y, x + w, y + 27)
    rr(d, box, 9, fill=SURF2, outline=ACCENT if active else None, width=1.2)
    col = ACCENT if active else (DIM if faint else TEXT)
    if label == "触控板":
        # no chevron: it is a plain action key, not a dropdown
        txt(d, label, x + w / 2, y + 13, 11, ACCENT if active else TEXT, anchor="mm")
    else:
        txt(d, label, x + 12, y + 13, 11, col, anchor="lm")
        chev(d, x + w - 14, y + 13, 180, 7, DIM, 1.6)

def chips_row(d, cx, y):
    """移动盘 + 触控 + 触控板, centred on cx."""
    total = 78 + 8 + 84 + 8 + 88
    x = cx - total / 2
    chip5(d, x, y, "移动盘", 78)
    chip5(d, x + 86, y, "触控 中间", 84, active=False)
    chip5(d, x + 178, y, "触控板", 88, active=True)

def landscape_v23(path, overlay=False):
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
    chips_row(d, W_L / 2, H_L - 41)
    if overlay:
        dim = Image.new("RGBA", im.size, (0, 0, 0, 150))
        im = Image.alpha_composite(im.convert("RGBA"), dim).convert("RGB")
        d = ImageDraw.Draw(im)
        rr(d, (24, top + 6, W_L - 24, H_L - 8), 18, fill=(21, 25, 33), outline=PANEL_LINE, width=1.6)
        txt(d, "触控板", 40, top + 18, 11, DIM)
        rr(d, (W_L - 88, top + 22, W_L - 40, top + 140), 12, fill=SURF2)
        chev(d, W_L - 64, top + 52, 0, 18, TEXT, 4)
        rr(d, (W_L - 88, (top + 150), W_L - 40, (top + 268)), 12, fill=SURF2)
        chev(d, W_L - 64, top + 232, 180, 18, TEXT, 4)
        cx = (40 + W_L - 100) / 2
        cy = (top + 30 + H_L - 78) / 2
        d.ellipse([P(cx - 9), P(cy - 9), P(cx + 9), P(cy + 9)], outline=DIM, width=max(1, P(1.2)))
        d.ellipse([P(cx - 3), P(cy - 3), P(cx + 3), P(cy + 3)], fill=DIM)
        txt(d, "触控区", cx, cy + 18, 13, DIM, anchor="ma")
        txt(d, "单指移动 · 轻点＝左键 · 双指点击＝右键 · 双指滑＝滚动", cx, cy + 36, 9, (110, 120, 138), anchor="ma")
        bw = 96
        for i, lab in enumerate(("返回", "主页", "后台")):
            x = W_L / 2 - bw * 1.5 - 8 + i * (bw + 8)
            rr(d, (x, H_L - 56, x + bw, H_L - 22), 10, fill=SURF2)
            txt(d, lab, x + bw / 2, H_L - 39, 11, anchor="mm")
        caption(d, W_L, H_L, ["点「触控板」小键 → 从底部弹出和键盘页同款的浮层（大触控区 + ↑↓ 滚动 + 返回/主页/后台）",
                              "再点一次或点空白处收起；手柄页原来的「中间/全屏」触控区保持不动"])
    else:
        caption(d, W_L, H_L, ["手柄页底部现在有三个小键：移动盘 / 触控 中间 / 触控板（新）",
                              "「触控板」和键盘页工具栏那个一样 —— 点一下弹出浮层，触控设成「关」时也能用"])
    im.save(path)
    return path

def portrait_v23(path):
    im = Image.new("RGB", (P(W_P), P(H_P + 48)), BG)
    d = ImageDraw.Draw(im)
    portrait_chrome(d, W_P)
    top = 128
    chips_row(d, W_P / 2, top + 10)
    touch_panel(d, (26, top + 60, 367, top + 330))
    # shoulder row: L1 | SELECT | START | R1
    pill(d, (16, top + 350, 112, top + 388), "L1")
    pill(d, (124, top + 355, 192, top + 382), "SELECT", 9)
    pill(d, (200, top + 355, 268, top + 382), "START", 9)
    pill(d, (W_P - 112, top + 350, W_P - 16, top + 388), "R1")
    dpad(d, 99, top + 480, 54, 50)
    abxy(d, 300, top + 480, 27, 40)
    caption(d, W_P, H_P, ["竖屏：三个小键排一行，「触控板」在最右（蓝色＝可点）",
                          "为了腾位置，SELECT / START 挪到 L1、R1 同一行，中间正好放得下"])
    im.save(path)
    return path

if __name__ == "__main__":
    base = "C:/Users/22453/Desktop/phoneKB/"
    print(landscape_v23(base + "mock-gp5-l.png"))
    print(landscape_v23(base + "mock-gp5-overlay.png", overlay=True))
    print(portrait_v23(base + "mock-gp5-p.png"))
