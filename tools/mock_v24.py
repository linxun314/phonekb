# -*- coding: utf-8 -*-
"""v0.24 mockups: full-body controller skin layout, layout switcher, free-drag edit mode."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

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
BODY  = (44, 48, 66)
BODY2 = (54, 59, 80)

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
    import math
    a = math.radians(ang)
    p0 = (cx + half*math.cos(a-math.pi/2), cy + half*math.sin(a-math.pi/2))
    p1 = (cx + half*math.cos(a), cy + half*math.sin(a))
    p2 = (cx + half*math.cos(a+math.pi/2), cy + half*math.sin(a+math.pi/2))
    d.line([p0[0]*K, p0[1]*K, p1[0]*K, p1[1]*K, p2[0]*K, p2[1]*K], fill=col,
           width=max(1, int(round(w*K))), joint="curve")

def power_icon(d, cx, cy, r, col=TEXT):
    import math
    d.arc([(cx-r)*K, (cy-r)*K, (cx+r)*K, (cy+r)*K], 55, 305, fill=col, width=max(1, int(round(2.2*K))))
    d.line([cx*K, (cy-r*1.05)*K, cx*K, (cy-r*0.1)*K], fill=col, width=max(1, int(round(2.2*K))))

def gear(d, cx, cy, r, col):
    import math
    for i in range(8):
        a = math.radians(i*45)
        d.line([(cx + r*0.72*math.cos(a))*K, (cy + r*0.72*math.sin(a))*K,
                (cx + r*1.12*math.cos(a))*K, (cy + r*1.12*math.sin(a))*K], fill=col,
               width=max(1, int(round(2.4*K))))
    circle(d, cx, cy, r*0.78, outline=col, width=2.4)
    circle(d, cx, cy, r*0.22, fill=col)

def status_bar(d, W):
    txt(d, "16:12", 16, 14, 12, TEXT, "lm", True)
    rr(d, (W-46, 9, W-14, 19), 3, fill=None, outline=DIM, width=1)
    rr(d, (W-44, 11, W-32, 17), 2, fill=OK)

def chrome_portrait(d, W):
    status_bar(d, W)
    rr(d, (12, 30, 60, 78), 14, fill=SURF2)
    power_icon(d, 36, 54, 9)
    circle(d, 74, 46, 3.4, fill=OK)
    txt(d, "已连接", 84, 45, 15, TEXT, "lm", True)
    txt(d, "iQOO Pad2", 84, 63, 11, DIM, "lm")
    for i, lab in enumerate(("旋转", "伴侣", "帮助")):
        x = W - 12 - (3-i)*62
        rr(d, (x, 34, x+56, 66), 11, fill=SURF2)
        txt(d, lab, x+28, 50, 12)
    tabs = ("键盘", "触控板", "媒体", "手柄", "双手", "单手")
    y = 92
    for i, lab in enumerate(tabs):
        x = 14 + i*60
        if lab == "手柄":
            rr(d, (x-4, y-4, x+52, y+34), 10, fill=SURF2)
            txt(d, lab, x+24, y+15, 13, TEXT, "mm", True)
        else:
            txt(d, lab, x+24, y+15, 13, DIM)

def chrome_landscape(d, W, H):
    toolbar_h = 44
    rr(d, (10, 6, 58, 40), 11, fill=SURF2)
    power_icon(d, 34, 23, 8)
    circle(d, 72, 18, 3.2, fill=OK)
    txt(d, "已连接 · iQOO Pad2", 82, 23, 13, TEXT, "lm", True)
    tabs = ("键盘", "触控板", "媒体", "手柄", "双手", "单手")
    x0 = 250
    for i, lab in enumerate(tabs):
        x = x0 + i*62
        if lab == "手柄":
            rr(d, (x-6, 8, x+52, 38), 10, fill=SURF2)
            txt(d, lab, x+23, 23, 12.5, TEXT, "mm", True)
        else:
            txt(d, lab, x+23, 23, 12.5, DIM)
    for i, lab in enumerate(("旋转", "伴侣", "帮助")):
        x = W - 12 - (3-i)*58
        rr(d, (x, 8, x+52, 38), 10, fill=SURF2)
        txt(d, lab, x+26, 23, 12)
    return toolbar_h

# ---------------- skin pieces ----------------
def body_shell(d, box, r=34):
    x0, y0, x1, y1 = box
    shadow = Image.new("RGBA", (int((x1-x0+40)*K), int((y1-y0+40)*K)), (0,0,0,0))
    ds = ImageDraw.Draw(shadow)
    ds.rounded_rectangle([20*K, 24*K, (x1-x0+20)*K, (y1-y0+20)*K], radius=r*K, fill=(0,0,0,110))
    return shadow

def skin(d, box, land):
    """Classic controller skin: rounded body, D-pad, ABXY ring, LB/LT/RB/RT, two sticks, gear."""
    import math
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    rr(d, box, 40 if not land else 32, fill=BODY, outline=BODY2, width=2)
    WHITE = (228, 232, 240)

    if not land:
        # explicit dp layout for the tall portrait body (373 x 720)
        gx, gy = x1 - 26, y0 + 44
        gear(d, gx, gy, 9, (200, 206, 220))
        txt(d, "HID 协议已连接", w / 2 + x0, y0 + 46, 11, (214, 220, 232), "mm", True)
        txt(d, "手柄皮肤 · 经典布局", w / 2 + x0, y0 + 64, 9.5, (150, 158, 175))
        for cx2, icon in ((w / 2 + x0 - 20, "back"), (w / 2 + x0 + 20, "start")):
            circle(d, cx2, y0 + 88, 7.5, outline=WHITE, width=1.8)
            chev(d, cx2, y0 + 88, 270 if icon == "back" else 90, 3.6, WHITE, 1.6)

        dcx, dcy, R = x0 + 86, y0 + 186, 34
        circle(d, dcx, dcy, R, outline=WHITE, width=2.2)
        arm, th = R * 0.92, R * 0.30
        for ang in (0, 90, 180, 270):
            a = math.radians(ang)
            ux, uy = math.cos(a), math.sin(a)
            pxv, pyv = -uy, ux
            base = th * 0.45
            tipx, tipy = dcx + ux * arm, dcy + uy * arm
            l1 = (dcx + ux * base + pxv * th, dcy + uy * base + pyv * th)
            l2 = (dcx + ux * base - pxv * th, dcy + uy * base - pyv * th)
            t1 = (tipx + pxv * th * 0.55, tipy + pyv * th * 0.55)
            t2 = (tipx - pxv * th * 0.55, tipy - pyv * th * 0.55)
            d.polygon([(l1[0]*K, l1[1]*K), (t1[0]*K, t1[1]*K), (t2[0]*K, t2[1]*K), (l2[0]*K, l2[1]*K)], fill=(232, 236, 244))

        acx, acy, ar = x0 + 287, y0 + 186, 33
        circle(d, acx, acy, ar, outline=WHITE, width=2.2)
        off = ar * 0.52
        for lab, dx, dy in (("Y", 0, -off), ("X", -off, 0), ("B", off, 0), ("A", 0, off)):
            circle(d, acx+dx, acy+dy, ar*0.34, outline=WHITE, width=1.8)
            txt(d, lab, acx+dx, acy+dy, 11.5, (240, 243, 249), "mm", True)

        for bx, by, lab in ((x0+14, y0+258, "LB"), (x0+14, y0+306, "LT"),
                            (x1-92, y0+258, "RB"), (x1-92, y0+306, "RT")):
            rr(d, (bx, by, bx+78, by+38), 9, fill=None, outline=WHITE, width=2)
            txt(d, lab, bx+39, by+19, 11.5, (240, 243, 249), "mm", True)

        for cx2, lab in ((x0+102, "L"), (x1-102, "R")):
            circle(d, cx2, y0+470, 40, outline=WHITE, width=2.2)
            circle(d, cx2, y0+470, 18, fill=(238, 241, 247))
            txt(d, lab, cx2, y0+470, 11, (70, 76, 92), "mm", True)
        return

    # ---- landscape: 857 x 325 body
    gx, gy = x1 - 40, y0 + 38
    gear(d, gx, gy, 10, (200, 206, 220))
    txt(d, "HID 协议已连接", w/2 + x0, y0 + 38, 11.5, (214, 220, 232), "mm", True)
    txt(d, "手柄皮肤 · 经典布局", w/2 + x0, y0 + 58, 10, (150, 158, 175))
    for cx2, icon in ((w/2 + x0 - 22, "back"), (w/2 + x0 + 22, "start")):
        circle(d, cx2, y0 + 86, 8, outline=WHITE, width=1.8)
        chev(d, cx2, y0 + 86, 270 if icon == "back" else 90, 3.8, WHITE, 1.6)

    dcx, dcy, R = x0 + w*0.20, y0 + h*0.47, h*0.21
    circle(d, dcx, dcy, R, outline=WHITE, width=2.4)
    arm, th = R*0.92, R*0.30
    for ang in (0, 90, 180, 270):
        a = math.radians(ang)
        ux, uy = math.cos(a), math.sin(a)
        pxv, pyv = -uy, ux
        base = th*0.45
        tipx, tipy = dcx + ux*arm, dcy + uy*arm
        l1 = (dcx + ux*base + pxv*th, dcy + uy*base + pyv*th)
        l2 = (dcx + ux*base - pxv*th, dcy + uy*base - pyv*th)
        t1 = (tipx + pxv*th*0.55, tipy + pyv*th*0.55)
        t2 = (tipx - pxv*th*0.55, tipy - pyv*th*0.55)
        d.polygon([(l1[0]*K, l1[1]*K), (t1[0]*K, t1[1]*K), (t2[0]*K, t2[1]*K), (l2[0]*K, l2[1]*K)], fill=(232, 236, 244))

    acx, acy, ar = x0 + w*0.80, y0 + h*0.47, h*0.20
    circle(d, acx, acy, ar, outline=WHITE, width=2.4)
    off = ar*0.52
    for lab, dx, dy in (("Y", 0, -off), ("X", -off, 0), ("B", off, 0), ("A", 0, off)):
        circle(d, acx+dx, acy+dy, ar*0.34, outline=WHITE, width=1.8)
        txt(d, lab, acx+dx, acy+dy, 12, (240, 243, 249), "mm", True)

    sw, shh = w*0.115, h*0.17
    for bx, by, lab in ((x0+w*0.045, y0+h*0.10, "LB"), (x0+w*0.045, y0+h*0.72, "LT"),
                        (x1-w*0.045-sw, y0+h*0.10, "RB"), (x1-w*0.045-sw, y0+h*0.72, "RT")):
        rr(d, (bx, by, bx+sw, by+shh), 9, fill=None, outline=WHITE, width=2.2)
        txt(d, lab, bx+sw/2, by+shh/2, 12, (240, 243, 249), "mm", True)

    for fx, lab in ((0.335, "L"), (0.665, "R")):
        cx2 = x0 + w*fx
        circle(d, cx2, y0 + h*0.83, h*0.155, outline=WHITE, width=2.2)
        circle(d, cx2, y0 + h*0.83, h*0.075, fill=(238, 241, 247))
        txt(d, lab, cx2, y0 + h*0.83, 11.5, (70, 76, 92), "mm", True)


def page_skin(path, land):
    W, H = (873, 393) if land else (393, 873)
    im = Image.new("RGB", (int(W*K), int(H*K)), BG)
    d = ImageDraw.Draw(im)
    if land:
        th = chrome_landscape(d, W, H)
        skin_box = (8, th + 6, W - 8, H - 8)
    else:
        chrome_portrait(d, W)
        skin_box = (10, 132, W - 10, 700)
    skin(d, skin_box, land)
    txt(d, "新布局：整机手柄皮肤（经典 Xbox 式轮廓）", W/2, H - 2.5 if land else H - 2.5, 9, DIM)
    im.save(OUT + path)

# ---------------- switcher dialog ----------------
def page_switch(path):
    W, H = 393, 873
    im = Image.new("RGB", (int(W*K), int(H*K)), BG)
    d = ImageDraw.Draw(im)
    chrome_portrait(d, W)
    skin(d, (10, 132, W-10, H-10), False)
    ov = Image.new("RGBA", im.size, (0, 0, 0, 150))
    im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(im)

    box = (22, 205, W-22, 690)
    rr(d, box, 22, fill=SURF, outline=(70, 78, 96), width=1.4)
    txt(d, "手柄设置", box[0]+22, box[1]+28, 17, TEXT, "lm", True)
    txt(d, "布局", box[0]+22, box[1]+74, 13, DIM, "lm")

    cards = (("浮键", "现在的样子", False), ("皮肤", "整机手柄", True), ("自定义", "自己拖位置", False))
    cw = (box[2]-box[0]-2*22-2*10)/3
    for i, (name, sub, sel) in enumerate(cards):
        x = box[0]+22 + i*(cw+10)
        y = box[1]+92
        rr(d, (x, y, x+cw, y+120), 14, fill=(30, 36, 47) if not sel else (36, 48, 72),
           outline=ACCENT if sel else (58, 66, 82), width=2 if sel else 1.2)
        if i == 0:
            for k in range(4):
                circle(d, x+cw*0.30+k*0, 0, 0) if False else None
            circle(d, x+cw*0.30, y+46, 15, outline=DIM, width=1.6)
            for k in range(2):
                circle(d, x+cw*0.72, y+30+k*30, 8, outline=DIM, width=1.4)
        elif i == 1:
            rr(d, (x+12, y+22, x+cw-12, y+70), 12, fill=(56, 62, 84), outline=DIM, width=1.2)
            circle(d, x+cw*0.32, y+40, 8, outline=DIM, width=1.4)
            circle(d, x+cw*0.68, y+40, 8, outline=DIM, width=1.4)
        else:
            for k, (dx, dy) in enumerate(((0.30,0.30),(0.70,0.30),(0.30,0.62),(0.70,0.62))):
                rr(d, (x+cw*dx-9, y+dy*80, x+cw*dx+9, y+dy*80+18), 5, fill=(52, 60, 78), outline=DIM, width=1)
            txt(d, "拖动", x+cw/2, y+80, 9, DIM)
        txt(d, name, x+cw/2, y+96, 12.5, ACCENT if sel else TEXT, "mm", True)
        txt(d, sub, x+cw/2, y+112, 9, DIM)
    txt(d, "切换后立即生效并记住；自定义可随时长按进入编辑", box[0]+22, box[1]+236, 9.5, DIM, "lm")

    # other rows (compact)
    rows = (("移动盘类型", ("按键", "圆盘"), 0), ("间距微调", None, 1))
    ry = box[1]+262
    txt(d, "移动盘 / 按键大小", box[0]+22, ry, 11.5, TEXT, "lm")
    sr = (box[0]+22, ry+22, box[2]-22, ry+26)
    rr(d, sr, 2, fill=(58, 66, 84))
    rr(d, (sr[0], sr[1], sr[0]+(sr[2]-sr[0])*0.5, sr[3]), 2, fill=ACCENT)
    circle(d, sr[0]+(sr[2]-sr[0])*0.5, ry+24, 6.5, fill=ACCENT, outline=BG, width=1.5)

    txt(d, "方向键方向", box[0]+22, ry+58, 11.5, TEXT, "lm")
    for i, lab in enumerate(("正常", "修正一格")):
        x = box[2]-22 - (2-i)*84
        rr(d, (x, ry+44, x+78, ry+74), 9, fill=(36, 48, 72) if i == 0 else SURF2)
        txt(d, lab, x+39, ry+59, 10.5, ACCENT if i == 0 else TEXT)

    btn_w, btn_gap = 92, 10
    bx0 = box[2]-22-btn_w*2-btn_gap
    for i, lab in enumerate(("取消", "保存")):
        x = bx0 + i*(btn_w+btn_gap)
        rr(d, (x, box[3]-46, x+btn_w, box[3]-14), 20, fill=SURF2 if i == 0 else ACCENT)
        txt(d, lab, x+btn_w/2, box[3]-30, 12.5, TEXT if i == 0 else (12, 20, 40), "mm", True)
    im.save(OUT + path)

# ---------------- edit mode ----------------
def page_edit(path):
    W, H = 873, 393
    im = Image.new("RGB", (int(W*K), int(H*K)), BG)
    d = ImageDraw.Draw(im)
    th = chrome_landscape(d, W, H)
    box = (8, th + 6, W - 8, H - 8)
    skin(d, box, True)
    # edit overlay: dashed frames around keys, one key being dragged
    dashed_rr(d, (box[0]+10, box[1]+8, box[0]+10+150, box[1]+8+34), 10, ACCENT)
    txt(d, "编辑布局 · 拖动任意按键，松手即保存", box[0]+168, box[1]+25, 10, ACCENT, "lm")
    for i, lab in enumerate(("重置", "完成")):
        x = box[2]-12-(2-i)*64
        rr(d, (x, box[1]+8, x+58, box[1]+38), 10, fill=SURF2 if i == 0 else ACCENT)
        txt(d, lab, x+29, box[1]+23, 10.5, TEXT if i == 0 else (12, 20, 40), "mm", True)

    dashed_circle(d, box[0]+box[2], 0, 0) if False else None
    # frames around the main clusters
    hb = (box[0]+18, box[1]+70, box[0]+150, box[1]+170)
    dashed_circle(d, (hb[0]+hb[2])/2, (hb[1]+hb[3])/2, (hb[2]-hb[0])/2, ACCENT)
    xb = (box[2]-150, box[1]+70, box[2]-18, box[1]+170)
    dashed_circle(d, (xb[0]+xb[2])/2, (xb[1]+xb[3])/2, (xb[2]-xb[0])/2, ACCENT)
    # dragged stick with ghost
    rr(d, (box[0]+120, box[1]+120, box[0]+250, box[1]+200), 12, fill=None, outline=(120, 130, 150), width=1.2)
    txt(d, "原位置", box[0]+185, box[1]+210, 8.5, DIM)
    cx, cy = box[0]+300, box[1]+160
    rr(d, (cx-54, cy-34, cx+54, cy+34), 12, fill=(52, 58, 80), outline=ACCENT, width=2)
    txt(d, "L 摇杆", cx, cy, 11, ACCENT, "mm", True)
    d.line([(box[0]+250)*K, (box[1]+165)*K, (cx-56)*K, (cy-2)*K], fill=ACCENT, width=2)
    im.save(OUT + path)

def dashed_rr(d, box, r, col, seg=6, gap=4, w=1.6):
    x0, y0, x1, y1 = box
    def line(a, b):
        import math
        dx, dy = b[0]-a[0], b[1]-a[1]
        L = math.hypot(dx, dy)
        n = max(1, int(L/(seg+gap)))
        for i in range(n):
            t0 = i*(seg+gap)/L
            t1 = min(1.0, (i*(seg+gap)+seg)/L)
            d.line([(a[0]+dx*t0)*K, (a[1]+dy*t0)*K, (a[0]+dx*t1)*K, (a[1]+dy*t1)*K], fill=col,
                   width=max(1, int(round(w*K))))
    line((x0+r, y0), (x1-r, y0)); line((x1-r, y0), (x1, y0+r))
    line((x1, y0+r), (x1, y1-r)); line((x1, y1-r), (x1-r, y1))
    line((x1-r, y1), (x0+r, y1)); line((x0+r, y1), (x0, y1-r))
    line((x0, y1-r), (x0, y0+r)); line((x0, y0+r), (x0+r, y0))

def dashed_circle(d, cx, cy, r, col):
    import math
    seg = 14
    for k in range(0, 360, seg+8):
        d.arc([(cx-r)*K, (cy-r)*K, (cx+r)*K, (cy+r)*K], k, k+seg, fill=col, width=max(1, int(round(1.6*K))))

page_skin("mock-v24-skin-p.png", False)
page_skin("mock-v24-skin-l.png", True)
page_switch("mock-v24-switch.png")
page_edit("mock-v24-edit.png")
print("ok")
