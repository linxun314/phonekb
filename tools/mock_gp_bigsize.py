
from PIL import Image, ImageDraw, ImageFont
import math

F = "C:/Windows/Fonts/msyh.ttc"
FB = "C:/Windows/Fonts/msyhbd.ttc"
K = 2.75  # px per dp for a 393dp wide phone

BG=(0x0F,0x11,0x15); SURF=(0x17,0x1B,0x22); SURF2=(0x1F,0x25,0x30); KEY=(0x26,0x2D,0x3A)
TEXT=(0xE9,0xEC,0xF2); DIM=(0x98,0xA1,0xB3); ACC=(0x4C,0x8D,0xFF)
BODY=(0x2C,0x30,0x42); RIM=(0x3A,0x3F,0x54); WHITE=(0xD3,0xDA,0xE7)

def px(v, s):  # dp -> px with extra scale
    return int(round(v*K*s))

def font(sz, bold=True):
    return ImageFont.truetype(FB if bold else F, int(sz))

def rr(d, box, r, fill, outline=None, w=2):
    d.rounded_rectangle([int(v) for v in box], radius=int(r), fill=fill, outline=outline, width=int(w))

def ctext(d, xy, s, f, fill):
    d.text(xy, s, font=f, fill=fill, anchor="mm")

def chev(d, cx, cy, size, ang, fill, width):
    pts=[]
    for bx, by in ((-0.46,0.30),(0,-0.36),(0.46,0.30)):
        a=math.radians(ang-270)
        x=bx*size*math.cos(a)-by*size*math.sin(a)
        y=bx*size*math.sin(a)+by*size*math.cos(a)
        pts.append((cx+x, cy+y))
    d.line(pts, fill=fill, width=int(width), joint="curve")

def top_bar(d, W, land):
    hh = px(64,1) if land else px(150,1)
    d.rectangle([0,0,W,hh], fill=BG)
    r = px(17,1)
    rr(d, (px(12,1), px(8,1), px(12,1)+px(34,1), px(8,1)+px(34,1)), r, SURF2)
    d.ellipse([px(25,1),px(21,1),px(25,1)+px(5,1),px(21,1)+px(5,1)], fill=(0x3D,0xDC,0x84))
    ctext(d, (px(12,1)+px(48,1), px(18,1)), "已连接 · iQOO Pad2", font(px(11,1)), TEXT)
    if land:
        x = W - px(12,1)
        for lbl in ("帮助","伴侣","旋转"):
            wl = px(40,1)
            rr(d, (x-wl, px(8,1), x, px(8,1)+px(26,1)), px(10,1), SURF2)
            ctext(d, ((x-wl/2), px(8,1)+px(13,1)), lbl, font(px(9,1)), TEXT)
            x -= wl + px(6,1)
        tabs=("键盘","触控板","媒体","手柄","双手","单手")
        tx = W - px(12,1) - 3*px(46,1) - px(18,1)
        for i,lbl in enumerate(tabs):
            w2 = px(44,1)
            tx -= w2
            if lbl=="手柄":
                rr(d, (tx, px(8,1), tx+w2, px(8,1)+px(26,1)), px(10,1), SURF2)
                ctext(d, (tx+w2/2, px(8,1)+px(13,1)), lbl, font(px(9,1)), TEXT)
            else:
                ctext(d, (tx+w2/2, px(8,1)+px(13,1)), lbl, font(px(9,1)), DIM)
            tx -= px(6,1)
    else:
        x = W - px(12,1)
        for lbl in ("帮助","伴侣","旋转"):
            wl = px(40,1)
            rr(d, (x-wl, px(8,1), x, px(8,1)+px(34,1)), px(11,1), SURF2)
            ctext(d, (x-wl/2, px(8,1)+px(17,1)), lbl, font(px(11,1)), TEXT)
            x -= wl + px(6,1)
        tabs=("键盘","触控板","媒体","手柄","双手","单手")
        tw = W/len(tabs)
        for i,lbl in enumerate(tabs):
            cx = tw*i + tw/2
            if lbl=="手柄":
                rr(d, (cx-px(26,1), px(52,1), cx+px(26,1), px(52,1)+px(26,1)), px(9,1), SURF2)
                ctext(d, (cx, px(52,1)+px(13,1)), lbl, font(px(11,1)), TEXT)
            else:
                ctext(d, (cx, px(52,1)+px(13,1)), lbl, font(px(11,1)), DIM)
    return hh

def chips(d, land, x0, y0):
    # 移动盘 / 触控 chips (left) + 触控板 chip (right)
    f = font(px(10,1))
    rr(d, (x0, y0, x0+px(72,1), y0+px(24,1)), px(10,1), SURF2)
    ctext(d, (x0+px(30,1), y0+px(12,1)), "移动盘", f, TEXT)
    chev(d, x0+px(58,1), y0+px(12,1), px(7,1), 90, DIM, px(1.6,1))
    rr(d, (x0+px(78,1), y0, x0+px(78,1)+px(66,1), y0+px(24,1)), px(10,1), SURF2)
    ctext(d, (x0+px(78,1)+px(33,1), y0+px(12,1)), "触控 关", f, TEXT)

def body_skin(d, box, layer):
    rr(d, box, px(26,1), BODY, RIM, max(1,int(px(1.2,1))))
    return box

def draw_skin(land, pad_scale, btn_scale, cap_boost):
    if land:
        W,H = 2400, 1080
    else:
        W,H = 1080, 2400
    im = Image.new("RGB",(W,H),BG); d=ImageDraw.Draw(im)
    hh = top_bar(d, W, land)
    if land:
        bx0,by0 = px(16,1), hh+px(8,1)
        bx1,by1 = W-px(16,1), H-px(10,1)
        chips(d, land, bx0, by0-px(3,1))
        by0 += px(30,1)
    else:
        bx0,by0 = px(16,1), hh+px(6,1)
        bx1,by1 = W-px(16,1), H-px(10,1)
        chips(d, land, bx0, by0)
        by0 += px(34,1)
    body_skin(d,(bx0,by0,bx1,by1),1)
    bw, bh = bx1-bx0, by1-by0
    padScale = pad_scale*cap_boost
    btnScale = btn_scale*cap_boost
    ringR = min(bh*0.19, bw*0.095) if land else min(bw*0.145, bh*0.10)
    ringR = max(px(30,1), min(px(96 if land else 150,1), ringR*padScale))
    padCx = bx0 + bw*(0.225 if land else 0.28); padCy = by0 + bh*(0.29 if land else 0.17)
    # ring
    d.ellipse([padCx-ringR,padCy-ringR,padCx+ringR,padCy+ringR], fill=(0x14,0x18,0x22), outline=WHITE, width=int(px(2,1)))
    s = ringR*0.62; arm = ringR*0.60
    for ang,(dx,dy) in ((0,(0,-1)),(180,(0,1)),(270,(-1,0)),(90,(1,0))):
        kx, ky = padCx+dx*arm, padCy+dy*arm
        rr(d,(kx-s/2,ky-s/2,kx+s/2,ky+s/2), px(9,1), KEY)
        chev(d, kx, ky, s*0.42, ang, TEXT, px(3.2,1))
    d.ellipse([padCx-px(3,1),padCy-px(3,1),padCx+px(3,1),padCy+px(3,1)], fill=DIM)
    # face cluster
    fr = ringR*0.30*btnScale; off = ringR*0.52
    faceCx = bx1 - bw*(0.225 if land else 0.28); faceCy = padCy
    fy=(0xFF,)
    colors={"Y":(0x5A,0x47,0x16),"X":(0x22,0x40,0x5F),"B":(0x5B,0x26,0x2B),"A":(0x1F,0x51,0x36)}
    ring2 = off+fr+px(10,1)
    d.ellipse([faceCx-ring2,faceCy-ring2,faceCx+ring2,faceCy+ring2], outline=WHITE, width=int(px(2,1)))
    pos={"Y":(faceCx,faceCy-off),"X":(faceCx-off,faceCy),"B":(faceCx+off,faceCy),"A":(faceCx,faceCy+off)}
    for lbl,(cx,cy) in pos.items():
        d.ellipse([cx-fr,cy-fr,cx+fr,cy+fr], fill=colors[lbl])
        ctext(d,(cx,cy),lbl,font(fr*0.8),TEXT)
    # shoulders
    sw = bw*(0.068 if land else 0.34)*btnScale
    sh = bh*(0.105 if land else 0.052)*btnScale
    lbX = bx0 + bw*(0.015 if land else 0.06)
    tlY = by0 + bh*(0.22 if land else 0.40)
    blY = tlY+sh+bh*0.05 if land else by0+bh*0.472
    for (x,y,lbl) in ((lbX,tlY,"L1"),(lbX,blY,"LT")):
        rr(d,(x,y,x+sw,y+sh), px(12,1), SURF2); ctext(d,(x+sw/2,y+sh/2),lbl,font(sh*0.42),TEXT)
    rbX = bx1 - bw*(0.015 if land else 0.06) - sw
    for (x,y,lbl) in ((rbX,tlY,"R1"),(rbX,blY,"RT")):
        rr(d,(x,y,x+sw,y+sh), px(12,1), SURF2); ctext(d,(x+sw/2,y+sh/2),lbl,font(sh*0.42),TEXT)
    # menus
    mh=(px(30,1) if land else px(24,1))*btnScale; mw=(px(78,1) if land else px(60,1))*btnScale
    menuCy = by0 + bh*(0.13 if land else 0.30); menuCx = (bx0+bx1)/2
    rr(d,(menuCx-px(6,1)-mw,menuCy-mh/2,menuCx-px(6,1),menuCy+mh/2), px(11,1), SURF2)
    ctext(d,(menuCx-px(6,1)-mw/2,menuCy),"SELECT",font(mh*0.40),TEXT)
    rr(d,(menuCx+px(6,1),menuCy-mh/2,menuCx+px(6,1)+mw,menuCy+mh/2), px(11,1), SURF2)
    ctext(d,(menuCx+px(6,1)+mw/2,menuCy),"START",font(mh*0.40),TEXT)
    # sticks
    sr = (min(bh*0.105,px(36,1)) if land else min(bw*0.15, bh*0.075))*btnScale
    sy = by0 + bh*(0.79 if land else 0.645)
    sxL = bx0+bw*(0.225 if land else 0.28); sxR = bx1-bw*(0.225 if land else 0.28)
    for cx in (sxL,sxR):
        d.ellipse([cx-sr,sy-sr,cx+sr,sy+sr], fill=(0x14,0x18,0x22), outline=WHITE, width=int(px(2,1)))
        d.ellipse([cx-sr*0.54,sy-sr*0.54,cx+sr*0.54,sy+sr*0.54], fill=(0xED,0xF0,0xF6))
    return im

def label_strip(im, text):
    d = ImageDraw.Draw(im)
    f = font(px(11,1))
    tw = d.textlength(text, font=f)
    d.rectangle([0, im.size[1]-int(px(20,1)), im.size[0], im.size[1]], fill=(0x10,0x13,0x19))
    d.text(((im.size[0]-tw)/2, im.size[1]-int(px(15,1))), text, font=f, fill=TEXT)
    return im

def compose(items, land):
    ims=[label_strip(im,t) for im,t in items]
    gap = 24
    if land:
        W = max(i.size[0] for i in ims); H = sum(i.size[1] for i in ims)+gap*(len(ims)-1)
        out=Image.new("RGB",(W,H),(8,9,12)); y=0
        for i in ims: out.paste(i,(0,y)); y+=i.size[1]+gap
    else:
        H = max(i.size[1] for i in ims); W = sum(i.size[0] for i in ims)+gap*(len(ims)-1)
        out=Image.new("RGB",(W,H),(8,9,12)); x=0
        for i in ims: out.paste(i,(x,0)); x+=i.size[0]+gap
    return out

# current caps (cap_boost=1) vs raised caps (cap_boost=1.45) so the slider can actually go bigger
sets_p = [
    (draw_skin(False,1.0,1.0,1.0), "现在 100%（最大只能到 150%，键就长这么大了）"),
    (draw_skin(False,1.6,1.6,1.45), "放大后 约 160%"),
    (draw_skin(False,2.2,2.2,1.45), "放大后 约 220%（新增上限）"),
]
sets_l = [
    (draw_skin(True,1.0,1.0,1.0), "现在 100%"),
    (draw_skin(True,1.6,1.6,1.45), "放大后 约 160%"),
    (draw_skin(True,2.2,2.2,1.45), "放大后 约 220%（新增上限）"),
]
compose(sets_p, False).save(r"C:\Users\22453\Desktop\phoneKB\mock-gp-bigsize-p.png")
compose(sets_l, True).save(r"C:\Users\22453\Desktop\phoneKB\mock-gp-bigsize-l.png")
print("ok")
