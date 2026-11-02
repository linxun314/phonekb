# -*- coding: utf-8 -*-
"""v0.25 mockups: pointer sensitivity (mouse speed) - entry chip, dialog, effect diagram."""
import math
from PIL import Image, ImageDraw, ImageFont

OUT = "C:/Users/22453/Desktop/phoneKB/"
K = 2.75
FB = "C:/Windows/Fonts/msyhbd.ttc"
FR = "C:/Windows/Fonts/msyh.ttc"
BG    = (15, 17, 21)
SURF  = (23, 27, 34)
SURF2 = (31, 37, 48)
KEY   = (38, 45, 58)
TEXT  = (233, 236, 242)
DIM   = (152, 161, 179)
ACCENT= (76, 141, 255)
OK    = (61, 220, 132)

def f(sz, bold=False):
    return ImageFont.truetype(FB if bold else FR, int(round(sz * K)))

def rr(d, box, r, fill=None, outline=None, width=1.0):
    d.rounded_rectangle([box[0]*K, box[1]*K, box[2]*K, box[3]*K], radius=r*K, fill=fill,
                        outline=outline, width=max(1, int(round(width*K))))

def txt(d, s, x, y, sz, col=TEXT, anchor="mm", bold=False):
    d.text((x*K, y*K), s, font=f(sz, bold), fill=col, anchor=anchor)

def circle(d, cx, cy, r, fill=None, outline=None, width=1.0):
    d.ellipse([(cx-r)*K, (cy-r)*K, (cx+r)*K, (cy+r)*K], fill=fill, outline=outline,
              width=max(1, int(round(width*K))))

def chev(d, cx, cy, ang, half, col, w=2.0):
    a = math.radians(ang)
    p0 = (cx + half*math.cos(a-math.pi/2), cy + half*math.sin(a-math.pi/2))
    p1 = (cx + half*math.cos(a), cy + half*math.sin(a))
    p2 = (cx + half*math.cos(a+math.pi/2), cy + half*math.sin(a+math.pi/2))
    d.line([p0[0]*K, p0[1]*K, p1[0]*K, p1[1]*K, p2[0]*K, p2[1]*K], fill=col,
           width=max(1, int(round(w*K))), joint="curve")

def power_icon(d, cx, cy, r, col=TEXT):
    d.arc([(cx-r)*K, (cy-r)*K, (cx+r)*K, (cy+r)*K], 55, 305, fill=col, width=max(1, int(round(2.2*K))))
    d.line([cx*K, (cy-r*1.05)*K, cx*K, (cy-r*0.1)*K], fill=col, width=max(1, int(round(2.2*K))))

def status_bar(d, W):
    txt(d, "16:12", 16, 14, 12, TEXT, "lm", True)
    rr(d, (W-46, 9, W-14, 19), 3, fill=None, outline=DIM, width=1)
    rr(d, (W-44, 11, W-32, 17), 2, fill=OK)

def header_portrait(d, W):
    status_bar(d, W)
    rr(d, (12, 30, 60, 78), 14, fill=SURF2)
    power_icon(d, 36, 54, 9)
    circle(d, 74, 46, 3.4, fill=OK)
    txt(d, "已连接", 84, 45, 15, TEXT, "lm", True)
    txt(d, "iQOO Pad2", 84, 63, 11, DIM, "lm")
    for i, lab in enumerate(("旋转", "伴侣", "帮助")):
        x = W - 12 - (3-i)*60
        rr(d, (x, 34, x+56, 66), 11, fill=SURF2)
        txt(d, lab, x+28, 50, 12)

def header_land(d, W, tabs_x):
    status_bar(d, W)
    rr(d, (12, 30, 60, 78), 14, fill=SURF2)
    power_icon(d, 36, 54, 9)
    circle(d, 74, 46, 3.4, fill=OK)
    txt(d, "已连接 · iQOO Pad2", 84, 54, 13, TEXT, "lm", True)
    for i, lab in enumerate(("键盘", "触控板", "媒体", "手柄", "双手", "单手")):
        x = tabs_x + i*58
        if lab == "触控板":
            rr(d, (x-12, 31, x+52, 69), 10, fill=SURF2)
            txt(d, lab, x+20, 50, 12.5, TEXT, "mm", True)
        else:
            txt(d, lab, x+20, 50, 12.5, DIM)
    for i, lab in enumerate(("旋转", "伴侣", "帮助")):
        x = W - 12 - (3-i)*62
        rr(d, (x, 34, x+56, 66), 11, fill=SURF2)
        txt(d, lab, x+28, 50, 12)

def tabs(d, W, active, y=100):
    names = ("键盘", "触控板", "媒体", "手柄", "双手", "单手")
    for i, n in enumerate(names):
        x = 16 + i*60
        if n == active:
            rr(d, (x-6, y-8, x+54, y+24), 9, fill=SURF2)
            txt(d, n, x + 24, y+8, 13, TEXT, "mm", True)
        else:
            txt(d, n, x + 24, y+8, 13, DIM)

def sens_chip(d, x, y, val="1.0×", hot=True):
    w = 104
    rr(d, (x, y, x+w, y+28), 14, fill=SURF2, outline=(ACCENT if hot else None), width=1.4)
    txt(d, "灵敏度", x+12, y+14, 11.5, ACCENT if hot else TEXT, "lm", True)
    txt(d, val, x+w-12, y+14, 11.5, TEXT, "rm", hot)
    return (x, y, x+w, y+28)

def touchpad_portrait(d, W, H):
    header_portrait(d, W)
    tabs(d, W, "触控板")
    # blank area: chip in the top-left corner of the blank zone
    box = sens_chip(d, 16, 148)
    txt(d, "点这里调鼠标速度 / 滚轮速度", 16, 192, 10.5, ACCENT, "lm", True)
    txt(d, "上方留空 · 拖动下方分隔条调整功能区高度", W/2, 236, 12, DIM, "mm", True)
    txt(d, "（功能区贴着屏幕底部，方便拇指操作）", W/2, 256, 10.5, DIM)
    txt(d, "拖动调整功能区", 16, 300, 10.5, DIM, "lm")
    rr(d, (W/2-40, 297, W/2+40, 303), 3, fill=(120, 130, 148))
    txt(d, "60% / 40%", W-16, 300, 10.5, DIM, "rm")
    top = 316
    rr(d, (12, top, W-12, H-14), 12, fill=SURF)
    for i, lab in enumerate(("数字", "键盘", "菜单")):
        y = top + 10 + i*64
        rr(d, (22, y, 86, y+56), 10, fill=KEY)
        if i < 2:
            for a in range(3):
                for b in range(2 if i == 0 else 3):
                    circle(d, 46+b*12, y+18+a*6, 1.5, fill=TEXT)
        else:
            for a in range(3):
                rr(d, (40, y+14+a*8, 68, y+17+a*8), 1.5, fill=TEXT)
        txt(d, lab, 54, y+46, 11, TEXT)
    pad = (96, top+10, W-116, top+214)
    rr(d, pad, 12, fill=KEY)
    txt(d, "触控区", (pad[0]+pad[2])/2, top+96, 12, TEXT, "mm", True)
    txt(d, "单指移动 · 点击=左键 · 双指滚动", (pad[0]+pad[2])/2, top+118, 10, DIM)
    rr(d, (W-106, top+10, W-22, top+108), 10, fill=KEY)
    chev(d, W-64, top+50, 270, 9, TEXT, 2.0)
    rr(d, (W-106, top+116, W-22, top+214), 10, fill=KEY)
    chev(d, W-64, top+175, 90, 9, TEXT, 2.0)
    for i, lab in enumerate(("返回", "主页", "后台")):
        x = 22 + i*118
        rr(d, (x, H-74, x+110, H-22), 10, fill=KEY)
        txt(d, lab, x+55, H-48, 11)

def slider(d, x0, x1, y, frac, col=ACCENT):
    rr(d, (x0, y-2, x1, y+2), 2, fill=(70, 78, 92))
    rr(d, (x0, y-2, x0 + (x1-x0)*frac, y+2), 2, fill=col)
    circle(d, x0 + (x1-x0)*frac, y, 7, fill=col)

def seg(d, x, y, items, active, w=64, h=28, gap=6):
    for i, lab in enumerate(items):
        xi = x + i*(w+gap)
        if lab == active:
            rr(d, (xi, y, xi+w, y+h), (h/2), fill=(58, 96, 168))
            txt(d, lab, xi+w/2, y+h/2, 11, TEXT, "mm", True)
        else:
            rr(d, (xi, y, xi+w, y+h), (h/2), fill=KEY)
            txt(d, lab, xi+w/2, y+h/2, 11, DIM)

def dim(img, W, H, alpha=150):
    ov = Image.new("RGBA", img.size, (0, 0, 0, alpha))
    img.paste(ov, (0, 0), ov)

def dialog_portrait(d, W, H):
    w = 336
    x0 = (W - w)/2
    y0 = 150
    h = 430
    rr(d, (x0, y0, x0+w, y0+h), 20, fill=SURF)
    txt(d, "指针灵敏度", x0+20, y0+30, 17, TEXT, "lm", True)
    txt(d, "手指滑动有多快，平板光标就跟着走多快", x0+20, y0+52, 10.5, DIM, "lm")
    txt(d, "1.0×", x0+20, y0+96, 30, ACCENT, "lm", True)
    txt(d, "标准（现在的手感）", x0+104, y0+96, 12, TEXT, "lm")
    txt(d, "= 每 1 cm 手指行程 → 光标走 1.7 cm", x0+20, y0+120, 10.5, DIM, "lm")
    txt(d, "慢", x0+20, y0+154, 10.5, DIM, "lm")
    txt(d, "快", x0+w-20, y0+154, 10.5, DIM, "rm")
    slider(d, x0+44, x0+w-44, y0+154, 0.30)
    for v, fx in ((0.4, 0.0), (1.0, 0.30), (1.6, 0.60), (2.5, 1.0)):
        txt(d, str(v) + "×", x0+44 + (w-88)*fx, y0+174, 9.5, DIM)
    seg(d, x0+20, y0+194, ("慢", "标准", "快"), "标准")
    txt(d, "预设", x0+20+3*70+8, y0+208, 10.5, DIM, "lm")
    rr(d, (x0+16, y0+240, x0+w-16, y0+242), 1, fill=(50, 56, 70))
    txt(d, "滚轮速度（可选）", x0+20, y0+266, 12, TEXT, "lm", True)
    txt(d, "双指上下滑 / 滚动键，一次滑动滚几行", x0+20, y0+286, 10.5, DIM, "lm")
    txt(d, "3 行", x0+w-20, y0+266, 12, ACCENT, "rm", True)
    slider(d, x0+20, x0+w-20, y0+308, 0.40, (110, 160, 240))
    txt(d, "对触控板页 / 单手遥控台 / 触控板浮层 / 手柄页触控区 同时生效", x0+20, y0+340, 10, DIM, "lm")
    txt(d, "设置会记住，换页面、重启都保持", x0+20, y0+358, 10, DIM, "lm")
    rr(d, (x0+w-172, y0+384, x0+w-98, y0+412), 15, fill=KEY)
    txt(d, "取消", x0+w-135, y0+398, 12, TEXT)
    rr(d, (x0+w-92, y0+384, x0+w-18, y0+412), 15, fill=ACCENT)
    txt(d, "保存", x0+w-55, y0+398, 12, TEXT, "mm", True)

def dialog_land(d, W, H):
    w = 420
    x0 = (W - w)/2
    y0 = 62
    h = 280
    rr(d, (x0, y0, x0+w, y0+h), 18, fill=SURF)
    txt(d, "指针灵敏度", x0+20, y0+26, 16, TEXT, "lm", True)
    txt(d, "1.0×", x0+w-20, y0+26, 20, ACCENT, "rm", True)
    txt(d, "慢", x0+20, y0+64, 10.5, DIM, "lm")
    txt(d, "快", x0+w-20, y0+64, 10.5, DIM, "rm")
    slider(d, x0+44, x0+w-44, y0+64, 0.30)
    seg(d, x0+20, y0+92, ("慢", "标准", "快"), "标准", 72, 28)
    txt(d, "滚轮速度（可选）", x0+20, y0+152, 12, TEXT, "lm", True)
    txt(d, "3 行", x0+w-20, y0+152, 12, ACCENT, "rm", True)
    slider(d, x0+20, x0+w-20, y0+176, 0.40, (110, 160, 240))
    txt(d, "对所有触控板页面同时生效 · 会记住", x0+20, y0+204, 10, DIM, "lm")
    rr(d, (x0+w-152, y0+232, x0+w-82, y0+260), 14, fill=KEY)
    txt(d, "取消", x0+w-117, y0+246, 12, TEXT)
    rr(d, (x0+w-76, y0+232, x0+w-16, y0+260), 14, fill=ACCENT)
    txt(d, "保存", x0+w-46, y0+246, 12, TEXT, "mm", True)

def effect():
    W, H = 1200, 620
    img = Image.new("RGB", (int(W*K), int(H*K)), BG)
    d = ImageDraw.Draw(img)
    txt(d, "同一段手指滑动，三档速度下平板光标走的距离", W/2, 34, 15, TEXT, "mm", True)
    rows = (("慢 0.6×", 0.6, "光标走 1.0 cm，适合精细拖选", (150, 170, 200)),
            ("标准 1.0×", 1.0, "光标走 1.7 cm，就是现在的手感", ACCENT),
            ("快 1.6×", 1.6, "光标走 2.7 cm，大屏幕扫得快", (110, 200, 160)))
    y = 110
    for name, mul, note, col in rows:
        rr(d, (40, y, 360, y+96), 14, fill=SURF)
        txt(d, name, 58, y+28, 15, col, "lm", True)
        txt(d, note, 58, y+56, 11, DIM, "lm")
        fx0, fx1 = 400, 560
        rr(d, (fx0, y+44, fx1, y+52), 4, fill=(90, 98, 114))
        circle(d, fx1, y+48, 8, fill=(200, 210, 225))
        txt(d, "手指 2 cm", (fx0+fx1)/2, y+24, 10, DIM)
        chev(d, 590, y+48, 90, 9, DIM, 2.0)
        bx0 = 620
        bx1 = bx0 + 400 * mul / 1.6
        rr(d, (bx0, y+44, bx1, y+52), 4, fill=col)
        circle(d, bx0, y+48, 4, fill=(120, 130, 150))
        circle(d, bx1, y+48, 9, outline=col, width=2)
        circle(d, bx1, y+48, 3, fill=col)
        txt(d, "平板光标 %.1f cm" % (2*0.85*mul), bx1 + 16, y+48, 11, col, "lm", True)
        y += 130
    txt(d, "注：1× 沿用现在代码里的 1.7 倍系数；滑块范围 0.4×–2.5×，默认 1.0×", W/2, H-40, 11, DIM, "mm")
    img.save(OUT + "mock-v25-effect.png")

def main():
    W, H = 393, 873
    img = Image.new("RGB", (int(W*K), int(H*K)), BG)
    d = ImageDraw.Draw(img)
    touchpad_portrait(d, W, H)
    img.save(OUT + "mock-v25-p-touchpad.png")

    img = Image.new("RGB", (int(W*K), int(H*K)), BG)
    d = ImageDraw.Draw(img)
    touchpad_portrait(d, W, H)
    dim(img, W, H)
    d = ImageDraw.Draw(img)
    dialog_portrait(d, W, H)
    img.save(OUT + "mock-v25-p-dialog.png")

    W2, H2 = 873, 393
    img = Image.new("RGB", (int(W2*K), int(H2*K)), BG)
    d = ImageDraw.Draw(img)
    header_land(d, W2, 262)
    for i, lab in enumerate(("数字", "键盘", "菜单")):
        y = 90 + i*74
        rr(d, (14, y, 96, y+66), 10, fill=SURF2)
        txt(d, lab, 55, y+33, 11)
    rr(d, (108, 90, W2-124, 320), 12, fill=SURF)
    txt(d, "触控区", (108+W2-124)/2, 190, 13, TEXT, "mm", True)
    txt(d, "单指移动 · 点击=左键 · 双指滚动", (108+W2-124)/2, 212, 10.5, DIM)
    txt(d, "灵敏度 1.0×", (108+W2-124)/2, 250, 11, ACCENT, "mm", True)
    rr(d, (W2-112, 90, W2-16, 202), 10, fill=SURF2); chev(d, W2-64, 146, 270, 10, TEXT)
    rr(d, (W2-112, 210, W2-16, 320), 10, fill=SURF2); chev(d, W2-64, 265, 90, 10, TEXT)
    for i, lab in enumerate(("返回", "主页", "后台")):
        x = 108 + i*180
        rr(d, (x, 332, x+168, 376), 10, fill=KEY)
        txt(d, lab, x+84, 354, 11)
    dim(img, W2, H2)
    d = ImageDraw.Draw(img)
    dialog_land(d, W2, H2)
    img.save(OUT + "mock-v25-l-dialog.png")

    effect()
    print("done")

if __name__ == "__main__":
    main()