
# -*- coding: utf-8 -*-
"""Mockup: PhoneKB Windows 模式（横屏键盘页 + 竖屏触控板/快捷键页）"""
import os
from PIL import Image, ImageDraw, ImageFont

WS = r"C:\Users\22453\Desktop\phoneKB"
K = 2.75
F_B = r"C:\Windows\Fonts\msyhbd.ttc"
F_R = r"C:\Windows\Fonts\msyh.ttc"

BG      = (15, 17, 21)
SURF    = (23, 27, 34)
SURF2   = (31, 37, 48)
KEY     = (38, 45, 58)
KEY_P   = (59, 73, 104)
ACC     = (76, 141, 255)
TEXT    = (233, 236, 242)
DIM     = (152, 161, 179)
OK      = (61, 214, 132)

def dp(v): return v * K
def font(sz, bold=False):
    return ImageFont.truetype(F_B if bold else F_R, int(round(sz * K)))

def rrect(d, box, r, fill=None, outline=None, width=1.0):
    b = [dp(box[0]), dp(box[1]), dp(box[2]), dp(box[3])]
    d.rounded_rectangle(b, radius=dp(r), fill=fill, outline=outline,
                        width=max(1, int(round(dp(width)))))

def text(d, s, x, y, sz, color, bold=False, anchor="lm"):
    d.text((dp(x), dp(y)), s, font=font(sz, bold), fill=color, anchor=anchor)

def pill(d, x, y, w, h, label, sz=12, fill=SURF2, fg=TEXT, bold=False, outline=None, ow=1.0):
    rrect(d, (x, y, x + w, y + h), h / 2.0, fill=fill, outline=outline, width=ow)
    text(d, label, x + w / 2.0, y + h / 2.0, sz, fg, bold, "mm")

def key(d, x, y, w, h, label, sz=13, fill=KEY, fg=TEXT, bold=False, r=8):
    rrect(d, (x, y, x + w, y + h), r, fill=fill)
    text(d, label, x + w / 2.0, y + h / 2.0, sz, fg, bold, "mm")

def circle_key(d, cx, cy, r, label, sub, lit=False):
    fill = KEY_P if lit else SURF2
    d.ellipse([dp(cx - r), dp(cy - r), dp(cx + r), dp(cy + r)], fill=fill)
    text(d, label, cx, cy - 2, 13, TEXT, True, "mm")
    if sub:
        text(d, sub, cx, cy + 26, 9.5, DIM, False, "mm")

def power_icon(d, cx, cy, s, color):
    b = [dp(cx - s), dp(cy - s * 0.95), dp(cx + s), dp(cy + s * 1.05)]
    d.arc(b, start=-58, end=238, fill=color, width=max(2, int(dp(2.2))))
    d.line([dp(cx), dp(cy - s * 1.15), dp(cx), dp(cy - s * 0.1)], fill=color, width=max(2, int(dp(2.2))))

# ---------------- 横屏：键盘页 ----------------
def landscape(path):
    W, H = 2400, 1080
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    WD = W / K  # 872.7 dp
    # 顶栏
    rrect(d, (12, 10, 56, 54), 13, fill=SURF2)
    power_icon(d, 34, 32, 7.5, TEXT)
    d.ellipse([dp(66), dp(26), dp(76), dp(36)], fill=OK)
    text(d, "已连接 · DESKTOP-7F2K", 84, 26, 15, TEXT, True)
    text(d, "蓝牙 HID · Windows 11", 84, 43, 11, DIM)
    # 设备切换
    segx = 318
    pill(d, segx, 16, 58, 28, "平板", 12, SURF2, DIM)
    pill(d, segx + 62, 16, 62, 28, "电脑", 12, KEY_P, TEXT, True, ACC, 1.2)
    # 右侧
    rx = WD - 12
    pill(d, rx - 56, 16, 56, 28, "帮助", 12)
    pill(d, rx - 56 - 62, 16, 54, 28, "旋转", 12)
    # 工具条
    ty = 66
    x = 12
    for label, w, hot in [("Win", 50, True), ("Win+D", 60, False), ("Alt+Tab", 68, False),
                          ("Win+Shift+S", 94, False), ("任务管理器", 84, False)]:
        pill(d, x, ty, w, 30, label, 11.5, KEY_P if hot else SURF2, TEXT, hot)
        x += w + 8
    pill(d, WD - 12 - 88, ty, 88, 30, "触控板", 12.5, SURF, ACC, True, ACC, 1.4)
    # 键盘
    left, right = 12, WD - 12
    avail = right - left
    gap = 5
    row_h = 48
    y0 = 106
    def row(n, heights=None):
        pass
    # 第 1 行：数字
    r0 = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "="]
    bw = (avail - gap * 12 - 54) / 12.0
    x = left
    for k in r0:
        key(d, x, y0, bw, row_h, k, 14)
        x += bw + gap
    key(d, x, y0, 54, row_h, "⌫", 13)
    # 第 2 行：qwerty
    y = y0 + row_h + gap
    r1 = list("qwertyuiop") + ["[", "]", "\\"]
    bw = (avail - gap * 12) / 13.0
    x = left
    for k in r1:
        key(d, x, y, bw, row_h, k, 14)
        x += bw + gap
    # 第 3 行：asdf
    y += row_h + gap
    r2 = list("asdfghjkl") + [";", "'"]
    bw = (avail - gap * 11 - 74) / 11.0
    x = left
    for k in r2:
        key(d, x, y, bw, row_h, k, 14)
        x += bw + gap
    key(d, x, y, 74, row_h, "Enter", 12, SURF2)
    # 第 4 行：shift
    y += row_h + gap
    r3 = list("zxcvbnm") + [",", ".", "/"]
    bw = (avail - gap * 10 - 148) / 10.0
    x = left
    key(d, x, y, 72, row_h, "Shift", 12, SURF2)
    x += 72 + gap
    for k in r3:
        key(d, x, y, bw, row_h, k, 14)
        x += bw + gap
    key(d, x, y, 72, row_h, "Shift", 12, SURF2)
    # 第 5 行：修饰 + 方向
    y += row_h + gap
    key(d, left, y, 56, row_h, "Ctrl", 12, SURF2)
    x = left + 56 + gap
    key(d, x, y, 58, row_h, "Win", 12.5, KEY_P, TEXT, True)  # Win 键高亮（Windows 专用）
    x += 58 + gap
    key(d, x, y, 48, row_h, "Alt", 12, SURF2)
    x += 48 + gap
    arrows = ["←", "↑", "↓", "→"]
    aw = 27
    arrow_block = aw * 4 + gap * 3
    space_w = avail - 56 - 58 - 48 - 48 - 56 - arrow_block - gap * 10
    key(d, x, y, space_w, row_h, "", 13)
    text(d, "Space", x + space_w / 2, y + row_h / 2, 12, DIM, False, "mm")
    x += space_w + gap
    key(d, x, y, 48, row_h, "Alt", 12, SURF2)
    x += 48 + gap
    key(d, x, y, 56, row_h, "Ctrl", 12, SURF2)
    x += 56 + gap
    for a in arrows:
        key(d, x, y, aw, row_h, a, 13, SURF2)
        x += aw + gap
    # 底部说明
    text(d, "Windows 模式 · 按键直接送到电脑 · 中文用电脑自己的输入法", 12, 368 + 16, 10.5, DIM)
    img.save(path)
    print("saved", path, img.size)

# ---------------- 竖屏：触控板 + 快捷键 ----------------
def portrait(path):
    W, H = 1080, 2400
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    WD = W / K  # 392.7 dp
    rrect(d, (12, 12, 52, 52), 12, fill=SURF2)
    power_icon(d, 32, 32, 7, TEXT)
    d.ellipse([dp(62), dp(27), dp(72), dp(37)], fill=OK)
    text(d, "已连接 · DESKTOP-7F2K", 80, 26, 14, TEXT, True)
    text(d, "蓝牙 HID · Windows 11", 80, 42, 10.5, DIM)
    pill(d, WD - 12 - 52, 20, 52, 26, "帮助", 11.5)
    # 设备切换
    cx = WD / 2
    pill(d, cx - 96, 62, 92, 28, "平板", 12, SURF2, DIM)
    pill(d, cx + 4, 62, 92, 28, "电脑", 12, KEY_P, TEXT, True, ACC, 1.2)
    # 触控板卡
    rrect(d, (12, 102, WD - 12, 430), 22, fill=SURF)
    pill(d, 26, 114, 74, 24, "灵敏度 1.7×", 10.5, SURF2, ACC, False, ACC, 1.2)
    d.ellipse([dp(cx - 5), dp(244), dp(cx + 5), dp(254)], fill=DIM)
    text(d, "触控区", cx, 280, 13, TEXT, False, "mm")
    text(d, "单指移动 · 点击=左键 · 双指滚动", cx, 304, 11, DIM, False, "mm")
    # 常用键卡
    rrect(d, (12, 444, WD - 12, 700), 22, fill=SURF)
    text(d, "常用键", 26, 462, 12, DIM)
    top = ["音量+", "音量−", "静音", "截屏", "任务视图"]
    bot = ["锁屏", "睡眠", "播放", "上一首", "下一首"]
    colw = (WD - 24 - 24) / 5.0
    for i, (a, b) in enumerate(zip(top, bot)):
        ccx = 24 + colw * (i + 0.5)
        circle_key(d, ccx, 512, 25, a, None, lit=(i == 3))
        circle_key(d, ccx, 604, 25, b, None)
    text(d, "截屏 = Win+Shift+S　任务视图 = Win+Tab", 26, 660, 9.5, DIM)
    # 底部
    text(d, "电脑模式：按键、鼠标、媒体键都直接送到电脑", 12, 726, 10.5, DIM)
    text(d, "中文输入请用电脑自己的输入法（蓝牙只发按键编号）", 12, 746, 10.5, DIM)
    img.save(path)
    print("saved", path, img.size)

landscape(os.path.join(WS, "mock-win-l.png"))
portrait(os.path.join(WS, "mock-win-p.png"))
