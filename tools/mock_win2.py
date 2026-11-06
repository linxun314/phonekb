
# -*- coding: utf-8 -*-
"""Mockup v2: Windows 模式 —— 竖屏键盘 / 竖屏触控板(含鼠标三键) / 横屏手柄页"""
import os
from PIL import Image, ImageDraw, ImageFont

WS = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
F_B = r"C:\Windows\Fonts\msyhbd.ttc"
F_R = r"C:\Windows\Fonts\msyh.ttc"

BG    = (15, 17, 21)
SURF  = (23, 27, 34)
SURF2 = (31, 37, 48)
KEY   = (38, 45, 58)
KEYP  = (59, 73, 104)
ACC   = (76, 141, 255)
TEXT  = (233, 236, 242)
DIM   = (152, 161, 179)
OK    = (61, 214, 132)

Y_COL = (176, 142, 52)
X_COL = (44, 82, 138)
B_COL = (150, 52, 52)
A_COL = (44, 112, 62)

def dp(v): return v * K
def font(sz, bold=False): return ImageFont.truetype(F_B if bold else F_R, int(round(sz * K)))
def rrect(d, box, r, fill=None, outline=None, width=1.0):
    d.rounded_rectangle([dp(box[0]), dp(box[1]), dp(box[2]), dp(box[3])],
                        radius=dp(r), fill=fill, outline=outline,
                        width=max(1, int(round(dp(width)))))
def text(d, s, x, y, sz, color, bold=False, anchor="lm"):
    d.text((dp(x), dp(y)), s, font=font(sz, bold), fill=color, anchor=anchor)
def pill(d, x, y, w, h, label, sz=12, fill=SURF2, fg=TEXT, bold=False, outline=None, ow=1.0):
    rrect(d, (x, y, x + w, y + h), h / 2.0, fill=fill, outline=outline, width=ow)
    text(d, label, x + w / 2.0, y + h / 2.0, sz, fg, bold, "mm")
def key(d, x, y, w, h, label, sz=13, fill=KEY, fg=TEXT, bold=False, r=8):
    rrect(d, (x, y, x + w, y + h), r, fill=fill)
    text(d, label, x + w / 2.0, y + h / 2.0, sz, fg, bold, "mm")
def power_icon(d, cx, cy, s, color):
    d.arc([dp(cx - s), dp(cy - s * 0.95), dp(cx + s), dp(cy + s * 1.05)],
          start=-58, end=238, fill=color, width=max(2, int(dp(2.2))))
    d.line([dp(cx), dp(cy - s * 1.15), dp(cx), dp(cy - s * 0.1)], fill=color,
           width=max(2, int(dp(2.2))))
def circle_key(d, cx, cy, r, label, sub=None, lit=False, fy=None):
    rrect(d, (cx - r, cy - r, cx + r, cy + r), r, fill=KEYP if lit else SURF2)
    text(d, label, cx, cy, 12.5, TEXT, True, "mm")
    if sub: text(d, sub, cx, cy + r + 12, 9.5, DIM, False, "mm")

def handle(d, cx, y, w=64, hint=None):
    """横向拖拽条：一根短圆条 + 可选右侧提示"""
    rrect(d, (cx - w / 2, y - 2.5, cx + w / 2, y + 3.5), 3, fill=(120, 132, 152))
    rrect(d, (cx - w / 2 + 14, y - 5.5, cx + w / 2 - 14, y - 3.5), 1, fill=(72, 82, 100))
    if hint:
        text(d, hint, cx + w / 2 + 10, y + 1, 9, DIM)

def topbar(d, WD, sub2="蓝牙 HID · Windows 11", helpw=True):
    rrect(d, (12, 12, 52, 52), 12, fill=SURF2)
    power_icon(d, 32, 32, 7, TEXT)
    d.ellipse([dp(62), dp(27), dp(72), dp(37)], fill=OK)
    text(d, "已连接 · DESKTOP-7F2K", 80, 26, 14, TEXT, True)
    text(d, sub2, 80, 43, 10.5, DIM)
    if helpw:
        pill(d, WD - 12 - 52, 20, 52, 26, "帮助", 11.5)

def device_switch(d, WD, y=62):
    cx = WD / 2
    pill(d, cx - 96, y, 92, 28, "平板", 12, SURF2, DIM)
    pill(d, cx + 4, y, 92, 28, "电脑", 12, KEYP, TEXT, True, ACC, 1.2)

# ---------- 1) 竖屏键盘页 ----------
def p_keyboard(path):
    W, H = 1080, 2400
    img = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(img)
    WD = W / K
    topbar(d, WD)
    device_switch(d, WD)
    # 工具栏
    rrect(d, (12, 102, 214, 148), 12, fill=SURF2)
    text(d, "切换布局", 20, 118, 12.5, TEXT, True)
    text(d, "当前：电脑布局", 20, 136, 9.5, DIM)
    pill(d, WD - 12 - 84, 106, 84, 32, "触控板", 12.5, SURF, ACC, True, ACC, 1.3)
    # 快捷键条
    sy = 156
    xs = 12
    for label, w in [("Win", 60), ("Win+D", 66), ("Alt+Tab", 70), ("截屏", 56), ("锁屏", 56)]:
        pill(d, xs, sy, w, 30, label, 11, KEYP if label == "Win" else SURF2, TEXT, label == "Win")
        xs += w + 6
    # 拉伸条（竖屏）：拖动改键盘高度，双击恢复
    handle(d, WD / 2, 196, 66, "拖动调整高度 · 双击恢复")
    # 键盘 6 行
    left, right = 12, WD - 12
    avail = right - left
    gap = 6
    y = 212
    rh = (690 - y - gap * 5) / 6.0
    rows = [
        (list("1234567890"), []),
        (list("qwertyuiop"), []),
        (list("asdfghjkl"), []),
    ]
    for keys, _ in rows:
        n = len(keys)
        kw = (avail - gap * (n - 1)) / n
        x = left
        for k in keys:
            key(d, x, y, kw, rh, k, 13.5)
            x += kw + gap
        y += rh + gap
    # ⇧ + zxcvbnm + ⌫
    kw = (avail - 48 - 48 - gap * 8) / 7.0
    x = left
    key(d, x, y, 48, rh, "Shift", 10.5, SURF2); x += 48 + gap
    for k in "zxcvbnm":
        key(d, x, y, kw, rh, k, 13.5); x += kw + gap
    key(d, x, y, 48, rh, "退格", 10.5, SURF2)
    y += rh + gap
    # 修饰行
    ctrl_w, win_w, alt_w = 46, 48, 40
    space_w = avail - (ctrl_w * 2 + win_w + alt_w * 2) - gap * 5
    x = left
    key(d, x, y, ctrl_w, rh, "Ctrl", 11, SURF2); x += ctrl_w + gap
    key(d, x, y, win_w, rh, "Win", 11.5, KEYP, TEXT, True); x += win_w + gap
    key(d, x, y, alt_w, rh, "Alt", 11, SURF2); x += alt_w + gap
    key(d, x, y, space_w, rh, "Space", 11, KEY); x += space_w + gap
    key(d, x, y, alt_w, rh, "Alt", 11, SURF2); x += alt_w + gap
    key(d, x, y, ctrl_w, rh, "Ctrl", 11, SURF2)
    y += rh + gap
    # Esc Tab 方向
    n = 6
    kw = (avail - gap * 5) / n
    x = left
    for k, sz in [("Esc", 11), ("Tab", 11), ("←", 13), ("↑", 13), ("↓", 13), ("→", 13)]:
        key(d, x, y, kw, rh, k, sz, SURF2); x += kw + gap
    # 底部常用键条（竖屏补足）
    sy2 = 706
    labels = [["音量+", "音量−", "静音", "截屏", "任务视图"],
              ["锁屏", "睡眠", "播放", "上一首", "下一首"]]
    bw = (avail - 8 * 4) / 5.0
    for r, rowlabels in enumerate(labels):
        x = left
        for lb in rowlabels:
            rrect(d, (x, sy2 + r * 44, x + bw, sy2 + r * 44 + 38), 10, fill=SURF2)
            text(d, lb, x + bw / 2, sy2 + r * 44 + 19, 10.5, TEXT, False, "mm")
            x += bw + 8
    text(d, "Windows 模式：按键直接送到电脑，中文请用电脑输入法", 12, 808, 10.5, DIM)
    img.save(path); print("saved", path, img.size)

# ---------- 2) 竖屏触控板页 ----------
def p_touchpad(path):
    W, H = 1080, 2400
    img = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(img)
    WD = W / K
    topbar(d, WD)
    device_switch(d, WD)
    cx = WD / 2
    # 触控板卡
    rrect(d, (12, 102, WD - 12, 470), 22, fill=SURF)
    pill(d, 26, 114, 76, 24, "灵敏度 1.7×", 10.5, SURF2, ACC, False, ACC, 1.2)
    d.ellipse([dp(cx - 5), dp(232), dp(cx + 5), dp(242)], fill=DIM)
    text(d, "触控区", cx, 266, 13, TEXT, False, "mm")
    text(d, "单指移动 · 轻点=左键 · 双指=滚动", cx, 290, 11, DIM, False, "mm")
    # 鼠标三键
    bw = (WD - 24 - 24 - 8 * 2) / 3.0
    x = 24
    for label, hot in [("左键", True), ("中键", False), ("右键", False)]:
        rrect(d, (x, 404, x + bw, 452), 10, fill=KEYP if hot else SURF2,
              outline=ACC if hot else None, width=1.2)
        text(d, label, x + bw / 2, 428, 12.5, TEXT, hot, "mm")
        x += bw + 8
    # 拉伸条（竖屏）：拖动改触控板/常用键比例
    handle(d, WD / 2, 482, 66, "拖动调整比例 · 双击恢复")
    # 常用键卡
    rrect(d, (12, 500, WD - 12, 758), 22, fill=SURF)
    text(d, "常用键", 26, 518, 12, DIM)
    top = ["音量+", "音量−", "静音", "截屏", "任务视图"]
    bot = ["锁屏", "睡眠", "播放", "上一首", "下一首"]
    colw = (WD - 48) / 5.0
    for i, (a, b) in enumerate(zip(top, bot)):
        ccx = 24 + colw * (i + 0.5)
        circle_key(d, ccx, 568, 24, a, lit=(i == 3))
        circle_key(d, ccx, 654, 24, b)
    text(d, "截屏 = Win+Shift+S　任务视图 = Win+Tab", 26, 716, 9.5, DIM)
    text(d, "电脑模式：鼠标、音量、媒体键都直接送到电脑", 12, 778, 10.5, DIM)
    text(d, "中文输入请用电脑自己的输入法（蓝牙只发按键编号）", 12, 798, 10.5, DIM)
    img.save(path); print("saved", path, img.size)

# ---------- 3) 横屏手柄页 ----------
def l_gamepad(path):
    W, H = 2400, 1080
    img = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(img)
    WD = W / K
    # 顶栏
    rrect(d, (12, 10, 56, 54), 13, fill=SURF2)
    power_icon(d, 34, 32, 7.5, TEXT)
    d.ellipse([dp(66), dp(26), dp(76), dp(36)], fill=OK)
    text(d, "已连接 · DESKTOP-7F2K", 84, 26, 15, TEXT, True)
    text(d, "蓝牙 HID · Windows 11 · 手柄", 84, 43, 11, DIM)
    # 页签
    tabs = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
    tx = 390
    for t in tabs:
        active = (t == "手柄")
        w = 62 if t != "双手" else 56
        pill(d, tx, 16, w, 28, t, 12, KEYP if active else SURF2, TEXT if active else DIM, active)
        tx += w + 8
    pill(d, WD - 12 - 54, 16, 54, 28, "旋转", 12)
    pill(d, WD - 12 - 54 - 62, 16, 54, 28, "帮助", 12)
    # 工具条
    pill(d, 12, 66, 74, 30, "移动盘", 11.5, SURF2, TEXT)
    d.polygon([(dp(69), dp(78)), (dp(80), dp(78)), (dp(74.5), dp(85))], fill=DIM)
    pill(d, 98, 66, 66, 30, "触控 关", 11.5, SURF2, DIM)
    pill(d, WD - 12 - 88, 66, 88, 30, "触控板", 12.5, SURF, ACC, True, ACC, 1.4)
    # 面板
    rrect(d, (12, 104, WD - 12, 372), 20, fill=SURF)
    # L1 / R1
    pill(d, 30, 116, 118, 34, "L1", 12.5, SURF2)
    pill(d, WD - 30 - 118, 116, 118, 34, "R1", 12.5, SURF2)
    # 移动盘（左）
    dcx, dcy, dr = 196, 246, 84
    d.ellipse([dp(dcx - dr), dp(dcy - dr), dp(dcx + dr), dp(dcy + dr)],
              outline=(120, 132, 152), width=max(2, int(dp(1.8))))
    for ang in (0, 90, 180, 270):
        a = math.radians(ang)
        px = dcx + math.sin(a) * 52
        py = dcy - math.cos(a) * 52
        s = 21
        tip = (px + math.sin(a) * s, py - math.cos(a) * s)
        l = (px + math.sin(a - 1.05) * s * 0.95, py - math.cos(a - 1.05) * s * 0.95)
        r = (px + math.sin(a + 1.05) * s * 0.95, py - math.cos(a + 1.05) * s * 0.95)
        d.polygon([(dp(tip[0]), dp(tip[1])), (dp(l[0]), dp(l[1])), (dp(r[0]), dp(r[1]))], fill=TEXT)
    d.ellipse([dp(dcx - 10), dp(dcy - 10), dp(dcx + 10), dp(dcy + 10)],
              fill=(210, 218, 232))
    text(d, "按一下发方向键", dcx, dcy + dr + 16, 9.5, DIM, False, "mm")
    # SELECT / START
    pill(d, 318, 214, 96, 32, "SELECT", 11, SURF2)
    pill(d, 424, 214, 96, 32, "START", 11, SURF2)
    # L / R 小圆
    for lx, lb in [(316, "L"), (472, "R")]:
        d.ellipse([dp(lx - 24), dp(316 - 24), dp(lx + 24), dp(316 + 24)],
                  fill=(240, 244, 250))
        text(d, lb, lx, 316, 12, (30, 36, 48), True, "mm")
    # 右摇杆 + ABXY
    rcx, rcy, rr = 700, 246, 84
    d.ellipse([dp(rcx - rr), dp(rcy - rr), dp(rcx + rr), dp(rcy + rr)],
              outline=(120, 132, 152), width=max(2, int(dp(1.8))))
    for label, col, ox, oy in [("Y", Y_COL, 0, -52), ("X", X_COL, -52, 0),
                               ("B", B_COL, 52, 0), ("A", A_COL, 0, 52)]:
        bx, by, br = rcx + ox, rcy + oy, 26
        d.ellipse([dp(bx - br), dp(by - br), dp(bx + br), dp(by + br)], fill=col)
        text(d, label, bx, by, 13, TEXT, True, "mm")
    d.ellipse([dp(rcx - 8), dp(rcy - 8), dp(rcx + 8), dp(rcy + 8)], fill=(120, 132, 152))
    # LT / RT
    pill(d, 30, 320, 104, 34, "LT", 12.5, SURF2)
    pill(d, WD - 30 - 104, 320, 104, 34, "RT", 12.5, SURF2)
    # 底部提示
    text(d, "Windows 认成「HID 游戏控制器」· DirectInput 游戏可直接用（纯 XInput 游戏请用 Steam 映射）",
         WD / 2, 386, 10, DIM, False, "mm")
    img.save(path); print("saved", path, img.size)

import math
p_keyboard(os.path.join(WS, "mock-win-p-kb.png"))
p_touchpad(os.path.join(WS, "mock-win-p-tp.png"))
l_gamepad(os.path.join(WS, "mock-win-gp.png"))
