# -*- coding: utf-8 -*-
# PhoneKB v0.18 mockups: phone-style gamepad layout + button-size slider + media brightness buttons
from PIL import Image, ImageDraw, ImageFont

K = 2.75
REG = "C:/Windows/Fonts/msyh.ttc"
BOLD = "C:/Windows/Fonts/msyhbd.ttc"
def F(sp, bold=False):
    return ImageFont.truetype(BOLD if bold else REG, int(sp * K))

BG=(0x0F,0x11,0x15); SURF=(0x17,0x1B,0x22); SURF2=(0x1F,0x25,0x30); KEY=(0x26,0x2D,0x3A)
KEYP=(0x3B,0x49,0x68); ACC=(0x4C,0x8D,0xFF); TEXT=(0xE9,0xEC,0xF2); DIM=(0x98,0xA1,0xB3)
OK=(0x3D,0xDC,0x84)
Xc=(0x22,0x40,0x5F); Yc=(0x5A,0x47,0x16); Ac=(0x1F,0x51,0x36); Bc=(0x5B,0x26,0x2B)

def P(v): return v * K
def rr(d, x0, y0, x1, y1, r, fill=None, outline=None, w=1):
    d.rounded_rectangle([P(x0),P(y0),P(x1),P(y1)], radius=P(r), fill=fill, outline=outline, width=int(P(w)))
def txt(d, s, cx, cy, f, col=TEXT):
    w = d.textlength(s, font=f)
    d.text((cx - w/2, cy), s, font=f, fill=col, anchor="lm")
def txtl(d, s, x, cy, f, col=TEXT):
    d.text((P(x), cy), s, font=f, fill=col, anchor="lm")

def chevron(d, cx, cy, s, direction, col=TEXT, lw=0.16):
    # direction: 0 up 1 right 2 down 3 left
    w = P(s * lw)
    h = P(s * 0.42)
    if direction == 0: pts = [(P(cx)-h, P(cy)+h*0.7), (P(cx), P(cy)-h*0.5), (P(cx)+h, P(cy)+h*0.7)]
    elif direction == 2: pts = [(P(cx)-h, P(cy)-h*0.7), (P(cx), P(cy)+h*0.5), (P(cx)+h, P(cy)-h*0.7)]
    elif direction == 3: pts = [(P(cx)+h*0.7, P(cy)-h), (P(cx)-h*0.5, P(cy)), (P(cx)+h*0.7, P(cy)+h)]
    else: pts = [(P(cx)-h*0.7, P(cy)-h), (P(cx)+h*0.5, P(cy)), (P(cx)-h*0.7, P(cy)+h)]
    d.line(pts, fill=col, width=int(w), joint="curve")

def sun(d, cx, cy, r, col=TEXT):
    d.ellipse([P(cx-r*0.45),P(cy-r*0.45),P(cx+r*0.45),P(cy+r*0.45)], outline=col, width=int(P(r*0.16)))
    import math
    for i in range(8):
        a = i * math.pi / 4
        x1 = cx + math.cos(a) * r * 0.62; y1 = cy + math.sin(a) * r * 0.62
        x2 = cx + math.cos(a) * r * 0.95; y2 = cy + math.sin(a) * r * 0.95
        d.line([P(x1),P(y1),P(x2),P(y2)], fill=col, width=int(P(r*0.16)))

def status_bar(d, W, landscape=False):
    f = F(11)
    d.text((P(14), P(8)), "10:47", font=f, fill=TEXT)
    rr(d, W-30, 9, W-12, 19, 3, outline=TEXT, w=0.8)
    d.rectangle([P(W-28),P(11),P(W-20),P(17)], fill=OK)

def header_p(d, right_pills):
    rr(d, 14, 34, 62, 82, 14, fill=SURF2)
    d.line([P(38),P(48),P(38),P(64)], fill=TEXT, width=int(P(2.2)))
    d.arc([P(30),P(50),P(46),P(66)], start=-40, end=220, fill=TEXT, width=int(P(2.2)))
    d.ellipse([P(74),P(54),P(82),P(62)], fill=OK)
    d.text((P(92), P(44)), "已连接", font=F(15, True), fill=TEXT)
    d.text((P(92), P(64)), "iQOO Pad2", font=F(11), fill=DIM)
    x = 393 - 14
    for lab in reversed(right_pills):
        w = len(lab)*13 + 24
        x -= w
        rr(d, x, 40, x+w, 76, 12, fill=SURF2)
        txt(d, lab, P(x+w/2), P(58), F(12))
        x -= 8
    return 82

def tabs_p(d, active):
    names = ["键盘","触控板","媒体","手柄","双手","单手"]
    y0, y1 = 92, 132
    x = 14
    ws_ = [46,60,46,46,46,46]
    for n, w in zip(names, ws_):
        if n == active:
            rr(d, x, y0, x+w, y1, 12, fill=SURF2)
            txt(d, n, P(x+w/2), P((y0+y1)/2), F(13, True))
        else:
            txt(d, n, P(x+w/2), P((y0+y1)/2), F(13), DIM)
        x += w + (393-28-x+ w)/1 if False else ( (393-28 - sum(ws_) )/5 + 0 )
    return 132

def tabs_p2(d, active):
    names = ["键盘","触控板","媒体","手柄","双手","单手"]
    y0, y1 = 92, 132
    total = 393-28
    slot = total/6.0
    for i, n in enumerate(names):
        cx = 14 + slot*i + slot/2
        w = 52
        if n == active:
            rr(d, cx-w/2, y0, cx+w/2, y1, 12, fill=SURF2)
            txt(d, n, P(cx), P((y0+y1)/2), F(13, True))
        else:
            txt(d, n, P(cx), P((y0+y1)/2), F(13), DIM)
    return 132

def bar_l(d, active, extra=("旋转","伴侣","帮助")):
    rr(d, 14, 8, 62, 44, 12, fill=SURF2)
    d.line([P(38),P(18),P(38),P(34)], fill=TEXT, width=int(P(2.2)))
    d.arc([P(30),P(20),P(46),P(36)], start=-40, end=220, fill=TEXT, width=int(P(2.2)))
    d.ellipse([P(74),P(22),P(82),P(30)], fill=OK)
    d.text((P(90), P(26)), "已连接 · iQOO Pad2", font=F(14, True), fill=TEXT)
    names = ["键盘","触控板","媒体","手柄","双手","单手"]
    xs = 300
    slot = 68
    for i, n in enumerate(names):
        cx = xs + slot*i + slot/2
        if n == active:
            rr(d, cx-30, 10, cx+30, 42, 12, fill=SURF2)
            txt(d, n, P(cx), P(26), F(12, True))
        else:
            txt(d, n, P(cx), P(26), F(12), DIM)
    x = 873 - 12
    for lab in reversed(extra):
        w = len(lab)*12+22
        x -= w
        rr(d, x, 10, x+w, 42, 12, fill=SURF2)
        txt(d, lab, P(x+w/2), P(26), F(12))
        x -= 8
    return 44

def gp_top_pills(d, x0, x1, y0, h):
    # L1 | SELECT | START | R1  (phone-style: shoulders at corners, menu keys centre)
    w = x1 - x0
    l1w = w * 0.30; mw = w * 0.17; gap = 8
    lx1 = x0 + l1w
    sx0 = lx1 + gap; sx1 = sx0 + mw
    tx0 = sx1 + gap; tx1 = tx0 + mw
    rx0 = tx1 + gap
    rr(d, x0, y0, lx1, y0+h, 10, fill=SURF2); txt(d, "L1", P((x0+lx1)/2), P(y0+h/2), F(12))
    rr(d, sx0, y0, sx1, y0+h, 10, fill=SURF2); txt(d, "SELECT", P((sx0+sx1)/2), P(y0+h/2), F(10))
    rr(d, tx0, y0, tx1, y0+h, 10, fill=SURF2); txt(d, "START", P((tx0+tx1)/2), P(y0+h/2), F(10))
    rr(d, rx0, y0, x1, y0+h, 10, fill=SURF2); txt(d, "R1", P((rx0+x1)/2), P(y0+h/2), F(12))

def chip(d, x, y):
    rr(d, x, y, x+74, y+28, 10, fill=SURF2)
    d.text((P(x+10), P(y+14)), "移动盘", font=F(11), fill=TEXT, anchor="lm")
    cx = x+58
    d.line([P(cx-4),P(y+12),P(cx),P(y+17),P(cx+4),P(y+12)], fill=DIM, width=int(P(1.4)))

def dpad(d, cx, cy, s, dd):
    for (dx, dy, dirn) in [(0,-1,0),(1,0,1),(0,1,2),(-1,0,3)]:
        x0 = cx + dx*dd - s/2; y0 = cy + dy*dd - s/2
        rr(d, x0, y0, x0+s, y0+s, s*0.28, fill=KEY)
        chevron(d, cx+dx*dd, cy+dy*dd, s*0.9, dirn)

def disc(d, cx, cy, r, hat=None):
    d.ellipse([P(cx-r),P(cy-r),P(cx+r),P(cy+r)], fill=KEY, outline=(0x39,0x42,0x52), width=int(P(1)))
    if hat is not None:
        import math
        a0 = (hat*45 - 90 - 22.5); a1 = a0 + 45
        d.pieslice([P(cx-r),P(cy-r),P(cx+r),P(cy+r)], start=a0, end=a1, fill=KEYP)
    for (dx, dy, dirn) in [(0,-1,0),(1,0,1),(0,1,2),(-1,0,3)]:
        col = ACC if hat is not None and ((dirn==0 and hat in (7,0,1)) or (dirn==1 and hat in (1,2,3)) or (dirn==2 and hat in (3,4,5)) or (dirn==3 and hat in (5,6,7))) else TEXT
        chevron(d, cx+dx*r*0.62, cy+dy*r*0.62, r*0.62, dirn, col)
    d.ellipse([P(cx-r*0.10),P(cy-r*0.10),P(cx+r*0.10),P(cy+r*0.10)], fill=(0x8A,0x93,0xA6))
    for i in range(4):
        import math
        a = math.pi/4 + i*math.pi/2
        d.ellipse([P(cx+math.cos(a)*r*0.85-1.2),P(cy+math.sin(a)*r*0.85-1.2),P(cx+math.cos(a)*r*0.85+1.2),P(cy+math.sin(a)*r*0.85+1.2)], fill=(0x59,0x62,0x74))

def abxy(d, cx, cy, r, off):
    for (dx, dy, lab, col) in [(0,-1,"Y",Yc),(-1,0,"X",Xc),(1,0,"B",Bc),(0,1,"A",Ac)]:
        x = cx+dx*off; y = cy+dy*off
        d.ellipse([P(x-r),P(y-r),P(x+r),P(y+r)], fill=col)
        txt(d, lab, P(x), P(y), F(13, True))

def caption(d, W, H, s):
    d.rectangle([0, P(H)-P(30), P(W), P(H)], fill=BG)
    txt(d, s, P(W/2), P(H)-P(15), F(11), DIM)

# ---------------- portrait gamepad ----------------
def gp_portrait(path, mode, f=1.0, hat=None, cap=""):
    W, H = 393, 873
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG)
    d = ImageDraw.Draw(img)
    status_bar(d, W)
    header_p(d, ["旋转","伴侣","帮助"])
    tabs_p2(d, "手柄")
    top = 140
    gp_top_pills(d, 10, 383, top+6, 32)
    cy0 = top + 46
    rr(d, 10, cy0, 196, H-34, 16, fill=SURF)
    rr(d, 206, cy0, 383, H-34, 16, fill=SURF)
    chip(d, 18, cy0+8)
    my = cy0 + (H-34-cy0)*0.56
    if mode == "disc":
        r = 62*f
        disc(d, 103, my, r, hat)
        txt(d, "按住滑动换方向" if hat is None else "当前：" + ["上","右上","右","右下","下","左下","左","左上"][hat], P(103), P(my+r+16), F(10), DIM if hat is None else ACC)
    else:
        s = 46*f; dd = s*1.10
        dpad(d, 103, my, s, dd)
    fr = 25*f; off = fr*1.55
    abxy(d, 294, my, fr, off)
    caption(d, W, H, cap)
    img.save(path)
    print("saved", path)

# ---------------- landscape gamepad ----------------
def gp_landscape(path, mode, f=1.0, cap=""):
    W, H = 873, 393
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG)
    d = ImageDraw.Draw(img)
    top = bar_l(d, "手柄")
    gp_top_pills(d, 10, 863, top+8, 30)
    cy0 = top + 46
    rr(d, 10, cy0, 426, H-10, 16, fill=SURF)
    rr(d, 436, cy0, 863, H-10, 16, fill=SURF)
    chip(d, 18, cy0+8)
    my = cy0 + (H-10-cy0)*0.56
    if mode == "disc":
        r = min(150, (H-10-cy0)*0.36)*f
        disc(d, 218, my, r, None)
        txt(d, "按住滑动换方向", P(218), P(my+r+12), F(10), DIM)
    else:
        s = 44*f; dd = s*1.10
        dpad(d, 218, my, s, dd)
    fr = 25*f; off = fr*1.55
    abxy(d, 650, my, fr, off)
    caption(d, W, H, cap)
    img.save(path)
    print("saved", path)

# ---------------- settings dialog ----------------
def gp_dialog(path):
    W, H = 393, 873
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG)
    d = ImageDraw.Draw(img)
    status_bar(d, W); header_p(d, ["旋转","伴侣","帮助"]); tabs_p2(d, "手柄")
    gp_top_pills(d, 10, 383, 146, 32)
    rr(d, 10, 186, 196, H-34, 16, fill=SURF); rr(d, 206, 186, 383, H-34, 16, fill=SURF)
    # dim the page behind the dialog
    ov = Image.new("RGBA", img.size, (0, 0, 0, 150))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = 26, 300, 367, 580
    rr(d, x0, y0, x1, y1, 20, fill=SURF, outline=(0x33,0x3B,0x4A), w=0.8)
    txtl(d, "手柄设置", x0+20, P(y0+30), F(16, True))
    txtl(d, "移动盘类型", x0+20, P(y0+72), F(12), DIM)
    rr(d, x1-150, y0+58, x1-20, y0+88, 14, fill=SURF2)
    rr(d, x1-146, y0+61, x1-85, y0+85, 12, fill=KEYP)
    txt(d, "按键", P(x1-115), P(y0+73), F(11), ACC)
    txt(d, "圆盘", P(x1-52), P(y0+73), F(11))
    txtl(d, "按键大小", x0+20, P(y0+120), F(12), DIM)
    sx0, sx1 = x0+20, x1-90
    sy = y0+140
    d.line([P(sx0),P(sy),P(sx1),P(sy)], fill=(0x3A,0x42,0x52), width=int(P(2)))
    pos = sx0 + (sx1-sx0)*((100-60)/90.0)
    d.line([P(sx0),P(sy),P(pos),P(sy)], fill=ACC, width=int(P(2)))
    d.ellipse([P(pos-6),P(sy-6),P(pos+6),P(sy+6)], fill=ACC)
    txt(d, "100%", P(x1-50), P(sy), F(12), ACC)
    txtl(d, "只缩放按键 / 圆盘本身，不改变左右区域宽度", x0+20, P(y0+170), F(10), DIM)
    txtl(d, "圆盘：按住并滑动选择方向，松手回中", x0+20, P(y0+192), F(10), DIM)
    txt(d, "取消", P(x1-110), P(y1-28), F(13), DIM)
    rr(d, x1-84, y1-44, x1-16, y1-14, 15, fill=ACC)
    txt(d, "保存", P(x1-50), P(y1-29), F(13, True), (0x0B,0x12,0x20))
    caption(d, W, H, "设置弹窗：滑块改为「按键大小」60%–150%，默认 100%")
    img.save(path)
    print("saved", path)

# ---------------- sizes comparison ----------------
def gp_sizes(path):
    panels = [(0.7, "小 70%"), (1.0, "默认 100%"), (1.4, "大 140%")]
    pw, ph = 393, 760
    gap = 14
    W = pw*3 + gap*4
    H = ph + 120
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG)
    d = ImageDraw.Draw(img)
    for i, (f, lab) in enumerate(panels):
        ox = gap + i*(pw+gap)
        sub = Image.new("RGB", (int(P(pw)), int(P(ph))), BG)
        sd = ImageDraw.Draw(sub)
        status_bar(sd, pw); header_p(sd, ["旋转","帮助"]); tabs_p2(sd, "手柄")
        gp_top_pills(sd, 10, 383, 140, 30)
        rr(sd, 10, 178, 196, ph-10, 16, fill=SURF); rr(sd, 206, 178, 383, ph-10, 16, fill=SURF)
        chip(sd, 18, 186)
        my = 178 + (ph-10-178)*0.55
        r = 60*f
        disc(sd, 103, my, min(r, 120), None)
        fr = 25*f; off = fr*1.55
        abxy(sd, 294, my, min(fr,40), min(off,62))
        img.paste(sub, (int(P(ox)), int(P(20))))
        txt(d, lab, P(ox+pw/2), P(ph+40), F(15, True))
        txt(d, "区域宽度不变，只缩放按键", P(ox+pw/2), P(ph+70), F(10), DIM)
    img.save(path)
    print("saved", path)

# ---------------- media portrait ----------------
def media_portrait(path):
    W, H = 393, 873
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG)
    d = ImageDraw.Draw(img)
    status_bar(d, W); header_p(d, ["旋转","伴侣","帮助"]); tabs_p2(d, "媒体")
    dock_top = 300
    txt(d, "上方留空 · 拖动下方分隔条调整功能区高度", P(W/2), P(180), F(11), DIM)
    txt(d, "（功能区贴着屏幕底部，方便拇指操作）", P(W/2), P(202), F(10), DIM)
    txtl(d, "拖动调整功能区", 12, P(dock_top-12), F(10), DIM)
    rr(d, W/2-26, dock_top-18, W/2+26, dock_top-12, 3, fill=(0x8A,0x93,0xA6))
    d.text((P(W-12), P(dock_top-12)), "功能区 78% / 留空 22%", font=F(10), fill=DIM, anchor="rm")
    y = dock_top
    rr(d, 10, y, 383, y+40, 12, fill=SURF2, outline=ACC, w=1.2)
    txt(d, "触控板", P(W/2), P(y+20), F(13), ACC)
    y += 48
    rr(d, 10, y, 383, y+84, 16, fill=SURF)
    for i, dirn in enumerate([3, None, 1]):
        cx = 10 + 373*(i+0.5)/3
        if dirn is None:
            d.polygon([(P(cx-10),P(y+42-16)),(P(cx-10),P(y+42+16)),(P(cx+8),P(y+42))], fill=TEXT)
            d.rectangle([P(cx+12),P(y+42-16),P(cx+17),P(y+42+16)], fill=TEXT)
        elif dirn == 3:
            d.rectangle([P(cx-14),P(y+42-14),P(cx-9),P(y+42+14)], fill=TEXT)
            d.polygon([(P(cx-6),P(y+42-14)),(P(cx-6),P(y+42+14)),(P(cx+14),P(y+42))], fill=TEXT)
        else:
            d.polygon([(P(cx-14),P(y+42-14)),(P(cx-14),P(y+42+14)),(P(cx+6),P(y+42))], fill=TEXT)
            d.rectangle([P(cx+9),P(y+42-14),P(cx+14),P(y+42+14)], fill=TEXT)
    y += 92
    # brightness row: two wide pills
    rr(d, 10, y, 196, y+44, 12, fill=SURF2)
    sun(d, 40, y+22, 12, TEXT)
    d.line([P(58),P(y+22),P(70),P(y+22)], fill=TEXT, width=int(P(2.4)))
    txtl(d, "亮度 −", 80, P(y+22), F(12))
    rr(d, 206, y, 383, y+44, 12, fill=SURF2)
    sun(d, 236, y+22, 12, TEXT)
    d.line([P(254),P(y+22),P(266),P(y+22)], fill=TEXT, width=int(P(2.4)))
    d.line([P(260),P(y+16),P(260),P(y+28)], fill=TEXT, width=int(P(2.4)))
    txtl(d, "亮度 ＋", 276, P(y+22), F(12))
    y += 52
    # circle rows
    rowh = (H-34-y-8)/2.0
    icons1 = ["vol+","vol-","mute","CC"]
    icons2 = ["back","home","menu","power"]
    for ri, icons in enumerate([icons1, icons2]):
        cy = y + rowh*ri + rowh/2
        for ci, ic in enumerate(icons):
            cx = 10 + 373*(ci+0.5)/4
            r = min(44, rowh*0.36)
            d.ellipse([P(cx-r),P(cy-r),P(cx+r),P(cy+r)], fill=SURF2)
            if ic == "vol+": sunless(d, cx, cy, r, 2)
            elif ic == "vol-": sunless(d, cx, cy, r, 1)
            elif ic == "mute": sunless(d, cx, cy, r, 0)
            elif ic == "CC": txt(d, "CC", P(cx), P(cy), F(16, True))
            elif ic == "back": chevron(d, cx, cy, r*1.5, 3, TEXT, 0.14)
            elif ic == "home": home(d, cx, cy, r)
            elif ic == "menu":
                for k in (-1,0,1): d.line([P(cx-r*0.45),P(cy+k*r*0.28),P(cx+r*0.45),P(cy+k*r*0.28)], fill=TEXT, width=int(P(2.6)))
            else: power(d, cx, cy, r)
    caption(d, W, H, "媒体页新增「亮度 − / 亮度 ＋」一行（Consumer 0x0070 / 0x006F）")
    img.save(path)
    print("saved", path)

def sunless(d, cx, cy, r, mode):
    # speaker icon; mode 2 = waves+, 1 = one wave, 0 = cross
    d.polygon([(P(cx-r*0.42),P(cy-r*0.16)),(P(cx-r*0.14),P(cy-r*0.16)),(P(cx+r*0.10),P(cy-r*0.42)),(P(cx+r*0.10),P(cy+r*0.42)),(P(cx-r*0.14),P(cy+r*0.16)),(P(cx-r*0.42),P(cy+r*0.16))], fill=TEXT)
    if mode == 0:
        d.line([P(cx+r*0.22),P(cy-r*0.28),P(cx+r*0.52),P(cy+r*0.28)], fill=TEXT, width=int(P(2.4)))
        d.line([P(cx+r*0.52),P(cy-r*0.28),P(cx+r*0.22),P(cy+r*0.28)], fill=TEXT, width=int(P(2.4)))
    else:
        for k in range(mode):
            d.arc([P(cx+r*0.10),P(cy-r*(0.26+k*0.16)),P(cx+r*(0.42+k*0.22)),P(cy+r*(0.26+k*0.16))], start=-50, end=50, fill=TEXT, width=int(P(2.2)))
def home(d, cx, cy, r):
    d.line([P(cx-r*0.42),P(cy),P(cx),P(cy-r*0.42),P(cx+r*0.42),P(cy)], fill=TEXT, width=int(P(2.6)), joint="curve")
    d.rectangle([P(cx-r*0.20),P(cy),P(cx+r*0.20),P(cy+r*0.34)], outline=TEXT, width=int(P(2.2)))
def power(d, cx, cy, r):
    d.arc([P(cx-r*0.36),P(cy-r*0.36),P(cx+r*0.36),P(cy+r*0.36)], start=-50, end=230, fill=TEXT, width=int(P(2.6)))
    d.line([P(cx),P(cy-r*0.44),P(cx),P(cy-r*0.06)], fill=TEXT, width=int(P(2.6)))

# ---------------- media landscape ----------------
def media_landscape(path):
    W, H = 873, 393
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG)
    d = ImageDraw.Draw(img)
    top = bar_l(d, "媒体")
    y = top + 10
    # brightness pills flank the media bar
    rr(d, 10, y, 106, y+76, 14, fill=SURF2)
    sun(d, 40, y+30, 13, TEXT)
    d.line([P(62),P(y+30),P(76),P(y+30)], fill=TEXT, width=int(P(2.6)))
    txt(d, "亮度−", P(58), P(y+58), F(10), DIM)
    rr(d, 767, y, 863, y+76, 14, fill=SURF2)
    sun(d, 797, y+30, 13, TEXT)
    d.line([P(819),P(y+30),P(833),P(y+30)], fill=TEXT, width=int(P(2.6)))
    d.line([P(826),P(y+23),P(826),P(y+37)], fill=TEXT, width=int(P(2.6)))
    txt(d, "亮度＋", P(815), P(y+58), F(10), DIM)
    rr(d, 116, y, 757, y+76, 16, fill=SURF)
    for i in range(3):
        cx = 116 + 641*(i+0.5)/3
        if i == 0:
            d.rectangle([P(cx-16),P(y+38-15),P(cx-11),P(y+38+15)], fill=TEXT)
            d.polygon([(P(cx-8),P(y+38-15)),(P(cx-8),P(y+38+15)),(P(cx+14),P(y+38))], fill=TEXT)
        elif i == 1:
            d.polygon([(P(cx-12),P(y+38-17)),(P(cx-12),P(y+38+17)),(P(cx+10),P(y+38))], fill=TEXT)
            d.rectangle([P(cx+14),P(y+38-17),P(cx+19),P(y+38+17)], fill=TEXT)
        else:
            d.polygon([(P(cx-14),P(y+38-15)),(P(cx-14),P(y+38+15)),(P(cx+8),P(y+38))], fill=TEXT)
            d.rectangle([P(cx+11),P(y+38-15),P(cx+16),P(y+38+15)], fill=TEXT)
    y2 = y + 86
    rr(d, 10, y2, 863, H-10, 16, fill=SURF)
    rowh = (H-10-y2)/2.0
    for ri, icons in enumerate([["vol+","vol-","mute","CC"],["back","home","menu","power"]]):
        cy = y2 + rowh*ri + rowh/2
        for ci, ic in enumerate(icons):
            cx = 10 + 853*(ci+0.5)/4
            r = min(46, rowh*0.40)
            d.ellipse([P(cx-r),P(cy-r),P(cx+r),P(cy+r)], fill=SURF2)
            if ic == "vol+": sunless(d, cx, cy, r, 2)
            elif ic == "vol-": sunless(d, cx, cy, r, 1)
            elif ic == "mute": sunless(d, cx, cy, r, 0)
            elif ic == "CC": txt(d, "CC", P(cx), P(cy), F(16, True))
            elif ic == "back": chevron(d, cx, cy, r*1.5, 3, TEXT, 0.14)
            elif ic == "home": home(d, cx, cy, r)
            elif ic == "menu":
                for k in (-1,0,1): d.line([P(cx-r*0.45),P(cy+k*r*0.28),P(cx+r*0.45),P(cy+k*r*0.28)], fill=TEXT, width=int(P(2.6)))
            else: power(d, cx, cy, r)
    caption(d, W, H, "横屏：亮度键放在媒体条两侧，不占额外行高")
    img.save(path)
    print("saved", path)

import os
os.chdir(r"C:\Users\22453\Desktop\phoneKB")
gp_portrait("mock-v18-gp-buttons.png", "buttons", 1.0, None, "手机式手柄布局：肩键/菜单键顶行，方向键与 ABXY 下沉到拇指区")
gp_portrait("mock-v18-gp-disc.png", "disc", 1.0, None, "圆盘模式同样下沉居中，大小随「按键大小」缩放")
gp_portrait("mock-v18-gp-disc-pressed.png", "disc", 1.0, 6, "圆盘按住「左」：扇区高亮，发送 hat=6（修复后对位）")
gp_landscape("mock-v18-gp-land.png", "disc", 1.0, "横屏手机式布局：L1/SELECT/START/R1 顶行，双簇下沉")
gp_dialog("mock-v18-gp-dialog.png")
gp_sizes("mock-v18-gp-sizes.png")
media_portrait("mock-v18-media-p.png")
media_landscape("mock-v18-media-l.png")
print("ALL DONE")
