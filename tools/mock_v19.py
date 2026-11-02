
from PIL import Image, ImageDraw, ImageFont
import math
K = 2.75
def P(v): return v * K
FONT_R = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"
_cache = {}
def F(sz, bold=False):
    k = (sz, bold)
    if k not in _cache:
        _cache[k] = ImageFont.truetype(FONT_B if bold else FONT_R, int(round(sz * K)))
    return _cache[k]

BG=(15,17,21); SURF=(23,27,34); SURF2=(31,37,48); KEY=(38,45,58)
ACCENT=(76,141,255); TEXT=(233,236,242); DIM=(152,161,179); OK=(61,220,132)
XA=(34,64,95); YA=(90,71,22); AA=(31,81,54); BA=(91,38,43)

def rr(d, x0, y0, x1, y1, r, fill=None, outline=None, w=1.0):
    d.rounded_rectangle([P(x0),P(y0),P(x1),P(y1)], radius=P(r), fill=fill, outline=outline, width=max(1,int(P(w))))

def txt(d, s, x, y, sz, color=TEXT, bold=False, anchor="mm"):
    d.text((P(x),P(y)), s, font=F(sz,bold), fill=color, anchor=anchor)

def chev(d, cx, cy, ang, size, color=TEXT, w=5.0):
    # base chevron pointing up, then rotate to requested angle
    base = [(-0.46, 0.30), (0.0, -0.36), (0.46, 0.30)]
    rot = math.radians(ang - 270)
    pts = []
    for bx, by in base:
        rx = bx*math.cos(rot) - by*math.sin(rot)
        ry = bx*math.sin(rot) + by*math.cos(rot)
        pts.append((P(cx + rx*size), P(cy + ry*size)))
    d.line(pts, fill=color, width=int(P(w)), joint="curve")

def power_icon(d, cx, cy, r, color=TEXT, w=2.2):
    d.arc([P(cx-r),P(cy-r),P(cx+r),P(cy+r)], start=55, end=305, fill=color, width=int(P(w)))
    d.line([(P(cx),P(cy-r-1)),(P(cx),P(cy+r*0.35))], fill=color, width=int(P(w)))

def status_bar(d, W):
    txt(d, "10:47", 16, 13, 11, TEXT, anchor="lm")
    rr(d, W-32, 8, W-14, 18, 3, outline=TEXT, w=0.8)
    d.rectangle([P(W-29),P(10),P(W-21),P(17)], fill=OK)

def header(d, W, compact):
    if compact:
        rr(d, 10, 6, 48, 44, 12, fill=SURF2); power_icon(d, 29, 25, 8)
        d.ellipse([P(58),P(22),P(64),P(28)], fill=OK)
        txt(d, "已连接 · iQOO Pad2", 70, 25, 14, TEXT, bold=True, anchor="lm")
        x = W - 10
        for label, wid in [("帮助",56),("伴侣",56),("旋转",52)]:
            rr(d, x-wid, 10, x, 40, 12, fill=SURF2); txt(d, label, x-wid/2, 25, 12, TEXT)
            x -= wid + 6
    else:
        rr(d, 16, 30, 60, 74, 12, fill=SURF2); power_icon(d, 38, 52, 9)
        d.ellipse([P(70),P(45),P(77),P(52)], fill=OK)
        txt(d, "已连接", 84, 43, 15, TEXT, bold=True, anchor="lm")
        txt(d, "iQOO Pad2", 84, 62, 12, DIM, anchor="lm")
        x = W - 16
        for label in ["帮助","伴侣","旋转"]:
            rr(d, x-56, 34, x, 70, 12, fill=SURF2); txt(d, label, x-28, 52, 12, TEXT)
            x -= 64

def tabs(d, W, y, active):
    names = ["键盘","触控板","媒体","手柄","双手","单手"]
    pad = 12; gap = 4
    cellw = (W - pad*2 - gap*(len(names)-1)) / len(names)
    for i, name in enumerate(names):
        x0 = pad + i*(cellw+gap)
        if name == active: rr(d, x0, y, x0+cellw, y+38, 11, fill=SURF2)
        txt(d, name, x0+cellw/2, y+19, 12, TEXT if name==active else DIM)

def dpad(d, cx, cy, s, arm):
    for ang, (dx, dy) in [(270,(0,-1)),(90,(0,1)),(180,(-1,0)),(0,(1,0))]:
        kx, ky = cx+dx*arm, cy+dy*arm
        rr(d, kx-s/2, ky-s/2, kx+s/2, ky+s/2, s*0.30, fill=KEY)
        chev(d, kx, ky, ang, s*0.30, TEXT, 5.0)

def abxy(d, cx, cy, r, off):
    for label, dx, dy, col in [("Y",0,-off,YA),("B",off,0,BA),("X",-off,0,XA),("A",0,off,AA)]:
        d.ellipse([P(cx+dx-r),P(cy+dy-r),P(cx+dx+r),P(cy+dy+r)], fill=col)
        txt(d, label, cx+dx, cy+dy, 15, (238,240,244), bold=True)

def pill(d, x0, y0, x1, y1, label, sz=13):
    rr(d, x0, y0, x1, y1, (y1-y0)/2, fill=KEY)
    txt(d, label, (x0+x1)/2, (y0+y1)/2, sz, TEXT)

def caption(d, W, lines):
    H = d.im.size[1]/K
    y = H - 40 if len(lines) == 2 else H - 26
    for i, s in enumerate(lines):
        txt(d, s, W/2, y + i*18, 10, DIM)

def render_portrait(variant, path):
    W, H = 393, 873
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG); d = ImageDraw.Draw(img)
    status_bar(d, W); header(d, W, False); tabs(d, W, 92, "手柄")
    top = 148; bottom = H - 60
    if variant == "A":
        dpad(d, 100, bottom-96, 54, 50)
        abxy(d, W-16-27-40, bottom-118, 27, 40)
        pill(d, 16, bottom-238, 112, bottom-202, "L1")
        pill(d, W-112, bottom-238, W-16, bottom-202, "R1")
        pill(d, 168, bottom-52, 236, bottom-22, "SELECT", 11)
        pill(d, 244, bottom-52, 312, bottom-22, "START", 11)
        cap = ["方案 A · 掌机式（PPSSPP / Dolphin 同款思路）",
               "方向盘贴左下 · ABXY 贴右下 · 肩键靠内侧中段 · 菜单键底部中央"]
    else:
        pill(d, 16, top+4, 118, top+38, "L1")
        pill(d, W-118, top+4, W-16, top+38, "R1")
        pill(d, W/2-66, top+4, W/2-6, top+34, "SELECT", 10)
        pill(d, W/2+6, top+4, W/2+66, top+34, "START", 10)
        dpad(d, 100, bottom-96, 56, 52)
        abxy(d, W-16-29-42, bottom-112, 29, 42)
        cap = ["方案 B · Switch 式",
               "肩键与菜单键全部顶置 · 方向盘 / ABXY 沉到左右下角 · 中间整块留空"]
    caption(d, W, cap)
    img.save(path)

def render_landscape(variant, path):
    W, H = 873, 393
    img = Image.new("RGB", (int(P(W)), int(P(H))), BG); d = ImageDraw.Draw(img)
    header(d, W, True); tabs(d, W, 50, "手柄")
    top = 100; bottom = H - 40
    if variant == "A":
        dpad(d, 104, bottom-96, 58, 56)
        abxy(d, W-16-29-43, bottom-106, 29, 43)
        pill(d, 16, top+18, 112, top+54, "L1")
        pill(d, W-112, top+18, W-16, top+54, "R1")
        pill(d, W/2-72, bottom-46, W/2-2, bottom-16, "SELECT", 11)
        pill(d, W/2+6, bottom-46, W/2+76, bottom-16, "START", 11)
        cap = ["横屏 · 方案 A（掌机式）：肩键靠内、双簇贴下角、菜单键底部偏中"]
    else:
        pill(d, 16, top+14, 130, top+50, "L1")
        pill(d, W-130, top+14, W-16, top+50, "R1")
        pill(d, W/2-74, top+14, W/2-8, top+44, "SELECT", 10)
        pill(d, W/2+8, top+14, W/2+74, top+44, "START", 10)
        dpad(d, 108, bottom-96, 60, 58)
        abxy(d, W-16-31-45, bottom-104, 31, 45)
        cap = ["横屏 · 方案 B（Switch 式）：肩键 / 菜单键顶行对齐、双簇沉到拇指下缘"]
    caption(d, W, cap)
    img.save(path)

base = r"C:\Users\22453\Desktop\phoneKB"
render_portrait("A", base + r"\mock-v19-p-A.png")
render_portrait("B", base + r"\mock-v19-p-B.png")
render_landscape("A", base + r"\mock-v19-l-A.png")
render_landscape("B", base + r"\mock-v19-l-B.png")
print("OK")
