
# -*- coding: utf-8 -*-
"""PhoneKB 30s promo video: 1920x1080, 30fps, no audio, no burned subtitles."""
import os, math, subprocess, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

WS = r"C:\Users\22453\Desktop\phoneKB"
SHOTS = os.path.join(WS, "docs", "screenshots")
BUILD = os.path.join(WS, "build")
OUT = os.path.join(BUILD, "PhoneKB-30s.mp4")
FFMPEG = r"C:\Users\22453\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.1-full_build\bin\ffmpeg.exe"
ICON = os.path.join(WS, "design", "app-icon-1024.png")

W, H = 1920, 1080
FPS = 30
DUR = 30.0
XF = 0.42  # crossfade seconds

BG_TOP = (12, 14, 20)
BG_BOT = (5, 7, 11)
ACCENT = (76, 141, 255)
ACCENT_SOFT = (40, 78, 150)
WHITE = (234, 238, 246)
DIM = (150, 160, 178)

def clamp01(x): return max(0.0, min(1.0, x))
def seg(t, a, b): return clamp01((t - a) / (b - a)) if b > a else 0.0
def eo(t): return 1 - (1 - t) ** 3
def eio(t): return t * t * (3 - 2 * t)

# ---------- background ----------
def make_bg():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        k = y / (H - 1)
        c = tuple(int(BG_TOP[i] * (1 - k) + BG_BOT[i] * k) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)
    glow = Image.new("L", (W // 4, H // 4), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse([20, 20, W // 4 - 20, H // 4 - 20], fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(70)).resize((W, H), Image.BILINEAR)
    tint = Image.new("RGB", (W, H), (18, 30, 58))
    return Image.composite(tint, img, glow.point(lambda v: int(v * 0.30)))

BG = make_bg()

def new_frame():
    return BG.convert("RGBA")

def layer():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))

def comp(frame, lay):
    out = Image.alpha_composite(frame, lay)
    return out

# ---------- screenshots ----------
PAD = 320  # border padding so zoom/pan crops never go negative
CORE_W, CORE_H = 2400, 962
CPX, CPY = PAD + CORE_W // 2, PAD + CORE_H // 2  # core center in padded space

def load_shot(name):
    im = Image.open(os.path.join(SHOTS, name)).convert("RGB")
    core = im.crop((0, 118, 2400, 1080))  # 2400x962, chrome bar removed
    out = Image.new("RGB", (CORE_W + PAD * 2, CORE_H + PAD * 2), BG_BOT)
    out.paste(core, (PAD, PAD))
    return out

CARD_W, CARD_H = 1740, 697
CARD_X, CARD_Y = (W - CARD_W) // 2, (H - CARD_H) // 2

def make_mask(w, h, r):
    m = Image.new("L", (w * 2, h * 2), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * 2 - 1, h * 2 - 1], radius=r * 2, fill=255)
    return m.resize((w, h), Image.LANCZOS)

CARD_MASK = make_mask(CARD_W, CARD_H, 26)

def make_shadow():
    pad = 90
    sh = Image.new("L", (CARD_W + pad * 2, CARD_H + pad * 2), 0)
    ImageDraw.Draw(sh).rounded_rectangle([pad, pad, pad + CARD_W, pad + CARD_H], radius=26, fill=190)
    sh = sh.filter(ImageFilter.GaussianBlur(38))
    return sh, pad

SHADOW, SHADOW_PAD = make_shadow()

SHADOW_LAYER = Image.new("RGBA", (CARD_W + SHADOW_PAD * 2, CARD_H + SHADOW_PAD * 2), (0, 0, 0, 255))

def put_card(frame, src, zoom, panx=0.0, pany=0.0):
    sw = CORE_W / zoom
    sh = CORE_H / zoom
    cx = CPX + panx
    cy = CPY + pany
    box = (cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2)
    crop = src.resize((CARD_W, CARD_H), Image.LANCZOS, box=box)
    frame.paste(SHADOW_LAYER, (CARD_X - SHADOW_PAD, CARD_Y - SHADOW_PAD), SHADOW)
    frame.paste(crop.convert("RGBA"), (CARD_X, CARD_Y), CARD_MASK)

def to_screen(sx, sy, zoom, panx=0.0, pany=0.0):
    """sx, sy are in core (2400x962) coordinates."""
    sw = CORE_W / zoom
    sh = CORE_H / zoom
    cx = CPX + panx
    cy = CPY + pany
    x0 = cx - sw / 2
    y0 = cy - sh / 2
    px = PAD + sx
    py = PAD + sy
    return (CARD_X + (px - x0) * (CARD_W / sw), CARD_Y + (py - y0) * (CARD_H / sh))

def ripple(lay, cx, cy, r0, alpha):
    if alpha <= 0:
        return
    r = r0
    a = int(clamp01(alpha) * 210)
    ImageDraw.Draw(lay).ellipse([cx - r, cy - r, cx + r, cy + r],
                                outline=(ACCENT[0], ACCENT[1], ACCENT[2], a), width=4)

def dot(lay, cx, cy, r, alpha=255, color=(255, 255, 255)):
    ImageDraw.Draw(lay).ellipse([cx - r, cy - r, cx + r, cy + r],
                                fill=(color[0], color[1], color[2], int(alpha)))

def cursor_tri(lay, cx, cy, s, ang=0.0):
    pts = []
    for a in (0, 2.4, -2.4):
        pts.append((cx + math.cos(ang + a) * s, cy + math.sin(ang + a) * s))
    ImageDraw.Draw(lay).polygon(pts, fill=(255, 255, 255, 235))
    ImageDraw.Draw(lay).line(pts + [pts[0]], fill=(30, 38, 52, 235), width=2)

# ---------- preloaded assets ----------
IMG_KB = load_shot("01-keyboard-landscape-computer.png")
IMG_TP = load_shot("03-touchpad-landscape.png")
IMG_MED = load_shot("06-media-landscape.png")
IMG_GP = load_shot("07-gamepad-landscape.png")

ICON_IM = Image.open(ICON).convert("RGBA")

FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_R = r"C:\Windows\Fonts\msyh.ttc"
def font(sz, bold=True):
    return ImageFont.truetype(FONT_B if bold else FONT_R, sz)

# ---------- generic silhouette ----------
def round_silhouette(lay, box, radius, alpha, fill, stroke, sw_=3):
    a = int(clamp01(alpha) * 255)
    b = [int(v) for v in box]
    ImageDraw.Draw(lay).rounded_rectangle(b, radius=radius,
                                          fill=(fill[0], fill[1], fill[2], a),
                                          outline=(stroke[0], stroke[1], stroke[2], a), width=sw_)

def bt_waves(lay, cx, cy, p, alpha=1.0, scale=1.0, spread=150.0):
    d = ImageDraw.Draw(lay)
    for i in range(3):
        ph = (p + i / 3.0) % 1.0
        r = (28 + ph * spread) * scale
        a = int(clamp01(alpha) * (1 - ph) * 200)
        if a <= 2:
            continue
        d.arc([cx - r, cy - r * 0.62, cx + r, cy + r * 0.62], start=-52, end=52,
              fill=(ACCENT[0], ACCENT[1], ACCENT[2], a), width=3)
        d.arc([cx - r, cy - r * 0.62, cx + r, cy + r * 0.62], start=128, end=232,
              fill=(ACCENT[0], ACCENT[1], ACCENT[2], a), width=3)

# ---------- SHOT 1: intro ----------
def shot_intro(p):
    f = new_frame()
    lay = layer()
    d = ImageDraw.Draw(lay)
    # tablet (landscape) center-right
    e1 = eo(seg(p, 0.05, 0.75))
    tw, th = 660, 456
    tcx, tcy = 1230 + (1 - e1) * 240, 540
    tbox = (tcx - tw / 2, tcy - th / 2, tcx + tw / 2, tcy + th / 2)
    round_silhouette(lay, tbox, 30, e1, (20, 26, 38), (58, 70, 92))
    # tablet screen inner
    if e1 > 0.2:
        ia = clamp01((e1 - 0.2) / 0.8)
        inset = 14
        round_silhouette(lay, (tbox[0] + inset, tbox[1] + inset, tbox[2] - inset, tbox[3] - inset),
                         22, ia, (12, 15, 22), (34, 42, 58), 2)
        # keyboard ghost grid
        gp = eo(seg(p, 0.45, 1.0))
        if gp > 0:
            gx0, gy0 = tbox[0] + 62, tbox[1] + 118
            gw, gh = tw - 124, th - 240
            cols, rows = 10, 4
            kx, ky = 9, 9
            cw = (gw - kx * (cols - 1)) / cols
            ch = (gh - ky * (rows - 1)) / rows
            for r in range(rows):
                for c in range(cols):
                    if c == cols - 1 and r < 2:
                        continue
                    x = gx0 + c * (cw + kx)
                    y = gy0 + r * (ch + ky)
                    a = int(gp * (70 + 60 * clamp01(r / 3.0)))
                    d.rounded_rectangle([x, y, x + cw, y + ch], radius=6,
                                        fill=(38, 48, 66, a))
    # phone (portrait) left
    e2 = eo(seg(p, 0.0, 0.55))
    pw, ph = 196, 404
    pcx, pcy = 360 - (1 - e2) * 260, 540
    pbox = (pcx - pw / 2, pcy - ph / 2, pcx + pw / 2, pcy + ph / 2)
    round_silhouette(lay, pbox, 26, e2, (22, 29, 42), (70, 92, 130))
    if e2 > 0.35:
        ia = clamp01((e2 - 0.35) / 0.65)
        inset = 9
        round_silhouette(lay, (pbox[0] + inset, pbox[1] + inset, pbox[2] - inset, pbox[3] - inset),
                         20, ia, (16, 21, 30), (44, 58, 82), 2)
        # tiny key grid on phone
        for r in range(5):
            for c in range(4):
                x = pbox[0] + 22 + c * 40
                y = pbox[1] + 150 + r * 34
                d.rounded_rectangle([x, y, x + 30, y + 24], radius=4, fill=(40, 52, 74, int(ia * 190)))
    # bluetooth waves between
    bt_waves(lay, 800, 470, seg(p, 0.2, 1.0) * 2.2, alpha=eo(seg(p, 0.25, 0.7)))
    # caption-free: nothing else
    return comp(f, lay)

# ---------- SHOT 2: keyboard ----------
def shot_kb(p):
    f = new_frame()
    zoom = 1.0 + 0.075 * eio(p)
    put_card(f, IMG_KB, zoom, panx=-10 * p, pany=-4 * p)
    lay = layer()
    # pulses on a few keys
    keys = [(300, 500), (1200, 500), (1900, 500)]
    for i, (sx, sy) in enumerate(keys):
        for k in (0.18, 0.55):
            ph = seg(p, k + i * 0.06, k + i * 0.06 + 0.30)
            if 0 < ph < 1:
                cxp, cyp = to_screen(sx, sy, zoom, -10 * p, -4 * p)
                ripple(lay, cxp, cyp, 18 + ph * 78, (1 - ph) * 0.9)
                dot(lay, cxp, cyp, 9, (1 - ph) * 180, (255, 255, 255))
    return comp(f, lay)

# ---------- SHOT 3: touchpad ----------
def shot_tp(p):
    f = new_frame()
    zoom = 1.045 + 0.075 * eio(p)
    put_card(f, IMG_TP, zoom, panx=12 * p, pany=-6 * p)
    lay = layer()
    # cursor path (source coords, y offset already cropped)
    t = eio(p)
    pts = [(820, 330), (1050, 215), (1330, 290), (1150, 375)]
    # simple catmull along 4 pts
    idx = min(int(t * 3), 2)
    lt = t * 3 - idx
    x0, y0 = pts[idx]
    x1, y1 = pts[idx + 1]
    cx = x0 + (x1 - x0) * lt
    cy = y0 + (y1 - y0) * lt
    sxs, sys_ = to_screen(cx, cy, zoom, 12 * p, -6 * p)
    cursor_tri(lay, sxs, sys_, 17, ang=math.radians(-90) + 0.15 * math.sin(p * 12))
    # click ripples at two moments
    for k in (0.32, 0.72):
        ph = seg(p, k, k + 0.26)
        if 0 < ph < 1:
            rx, ry = to_screen(1050, 215, zoom, 12 * p, -6 * p) if k < 0.5 else to_screen(1330, 290, zoom, 12 * p, -6 * p)
            ripple(lay, rx, ry, 12 + ph * 58, (1 - ph) * 0.65)
    return comp(f, lay)

# ---------- SHOT 4: media ----------
def shot_med(p):
    f = new_frame()
    zoom = 1.03 + 0.07 * eio(p)
    put_card(f, IMG_MED, zoom, panx=-8 * p, pany=0)
    lay = layer()
    hits = [(330, 396), (784, 396), (2082, 396)]
    for i, (sx, sy) in enumerate(hits):
        ph = seg(p, 0.12 + i * 0.22, 0.12 + i * 0.22 + 0.30)
        if 0 < ph < 1:
            cxp, cyp = to_screen(sx, sy, zoom, -8 * p, 0)
            ripple(lay, cxp, cyp, 16 + ph * 70, (1 - ph) * 0.9)
    return comp(f, lay)

# ---------- SHOT 5: gamepad ----------
def shot_gp(p):
    f = new_frame()
    zoom = 1.04 + 0.07 * eio(p)
    put_card(f, IMG_GP, zoom, panx=6 * p, pany=-4 * p)
    lay = layer()
    # stick dot orbit
    ang = p * math.pi * 1.7
    r = 62
    sx = 600 + math.cos(ang) * r
    sy = 519 + math.sin(ang) * r * 0.85
    px, py = to_screen(sx, sy, zoom, 6 * p, -4 * p)
    dot(lay, px, py, 22, 120, (255, 255, 255))
    dot(lay, px, py, 12, 235, (255, 255, 255))
    # face button flashes
    for i, (bx, by) in enumerate([(1922, 690), (1780, 690), (1850, 520), (1990, 520)]):
        ph = seg(p, 0.10 + i * 0.16, 0.10 + i * 0.16 + 0.24)
        if 0 < ph < 1:
            qx, qy = to_screen(bx, by, zoom, 6 * p, -4 * p)
            ripple(lay, qx, qy, 8 + ph * 40, (1 - ph) * 0.55)
    return comp(f, lay)

# ---------- SHOT 6: dataflow ----------
def shot_flow(p):
    f = new_frame()
    lay = layer()
    d = ImageDraw.Draw(lay)
    acx, acy = 470, 540
    bcx, bcy = 1470, 540
    ph_w, ph_h = 190, 396
    tb_w, tb_h = 470, 330
    round_silhouette(lay, (acx - ph_w / 2, acy - ph_h / 2, acx + ph_w / 2, acy + ph_h / 2),
                     26, 1.0, (22, 29, 42), (70, 92, 130))
    round_silhouette(lay, (acx - ph_w / 2 + 9, acy - ph_h / 2 + 9, acx + ph_w / 2 - 9, acy + ph_h / 2 - 9),
                     20, 1.0, (16, 21, 30), (40, 52, 74), 2)
    round_silhouette(lay, (bcx - tb_w / 2, bcy - tb_h / 2, bcx + tb_w / 2, bcy + tb_h / 2),
                     26, 1.0, (20, 26, 38), (58, 70, 92))
    round_silhouette(lay, (bcx - tb_w / 2 + 12, bcy - tb_h / 2 + 12, bcx + tb_w / 2 - 12, bcy + tb_h / 2 - 12),
                     18, 1.0, (12, 15, 22), (34, 42, 58), 2)
    # app window lighting up on tablet
    lit = eo(seg(p, 0.62, 0.95))
    if lit > 0:
        wx0, wy0 = bcx - tb_w / 2 + 54, bcy - tb_h / 2 + 56
        wx1, wy1 = bcx + tb_w / 2 - 54, bcy + tb_h / 2 - 56
        d.rounded_rectangle([wx0, wy0, wx1, wy1], radius=12,
                            fill=(28, 40, 62, int(lit * 210)),
                            outline=(ACCENT[0], ACCENT[1], ACCENT[2], int(lit * 170)), width=2)
        for i in range(3):
            yy = wy0 + 24 + i * 26
            d.rounded_rectangle([wx0 + 20, yy, wx0 + 20 + (wx1 - wx0 - 40) * (0.85 - i * 0.2), yy + 9],
                                radius=4, fill=(120, 150, 200, int(lit * 200)))
    # packets flowing phone -> tablet
    n = 7
    for i in range(n):
        ph = (p * 1.55 + i / n) % 1.0
        x = acx + 120 + (bcx - 100 - (acx + 120)) * ph
        y = acy + math.sin(ph * math.pi * 2.0 + i) * 26
        a = int(255 * (1 - abs(ph - 0.5) * 2) ** 0.6)
        d.rounded_rectangle([x - 13, y - 9, x + 13, y + 9], radius=5,
                            fill=(ACCENT[0], ACCENT[1], ACCENT[2], max(30, a)))
    bt_waves(lay, (acx + bcx) // 2, acy, p * 2.4, alpha=0.85, spread=170)
    return comp(f, lay)

# ---------- SHOT 7: endcard ----------
END_ICON = ICON_IM.resize((240, 240), Image.LANCZOS)
END_GLOW = Image.new("L", (620, 620), 0)
ImageDraw.Draw(END_GLOW).ellipse([120, 120, 500, 500], fill=170)
END_GLOW = END_GLOW.filter(ImageFilter.GaussianBlur(70))

def shot_end(p):
    f = new_frame()
    lay = layer()
    e = eo(seg(p, 0.0, 0.55))
    # glow
    ga = int(e * 190)
    glow_rgba = Image.new("RGBA", (620, 620), (ACCENT[0], ACCENT[1], ACCENT[2], 0))
    glow_rgba.putalpha(END_GLOW.point(lambda v: int(v * e * 0.55)))
    lay.paste(glow_rgba, (W // 2 - 310, 300), glow_rgba)
    s = 0.82 + 0.18 * e
    iw = int(240 * s)
    ic = END_ICON.resize((iw, iw), Image.LANCZOS)
    m = make_mask(iw, iw, int(iw * 0.24))
    lay.paste(ic, (W // 2 - iw // 2, int(430 - iw // 2)), m)
    d = ImageDraw.Draw(lay)
    # name
    ta = int(clamp01(seg(p, 0.28, 0.7)) * 255)
    ftxt = font(66, True)
    txt = "PhoneKB"
    bb = d.textbbox((0, 0), txt, font=ftxt)
    tx = W // 2 - (bb[2] - bb[0]) // 2
    d.text((tx, 600), txt, font=ftxt, fill=(WHITE[0], WHITE[1], WHITE[2], ta))
    # accent underline
    ua = int(clamp01(seg(p, 0.5, 0.9)) * 255)
    lw = int(120 * eo(seg(p, 0.5, 0.9)))
    d.rounded_rectangle([W // 2 - lw // 2, 692, W // 2 + lw // 2, 697], radius=3,
                        fill=(ACCENT[0], ACCENT[1], ACCENT[2], ua))
    return comp(f, lay)

# ---------- timeline ----------
SHOT_DEFS = [
    (0.0,  4.2,  shot_intro),
    (4.2,  9.0,  shot_kb),
    (9.0,  13.5, shot_tp),
    (13.5, 18.2, shot_med),
    (18.2, 22.5, shot_gp),
    (22.5, 26.3, shot_flow),
    (26.3, 30.0, shot_end),
]

def render_at(t):
    idx = 0
    for i, (a, b, fn) in enumerate(SHOT_DEFS):
        if a <= t < b:
            idx = i
            break
    else:
        idx = len(SHOT_DEFS) - 1
    a, b, fn = SHOT_DEFS[idx]
    p = clamp01((t - a) / (b - a))
    cur = fn(p)
    if idx > 0 and t - a < XF:
        pa, pb, pfn = SHOT_DEFS[idx - 1]
        prev = pfn(1.0)
        w = eio(clamp01((t - a) / XF))
        cur = Image.blend(prev, cur, w)
    return cur.convert("RGB")

def main():
    n = int(DUR * FPS)
    cmd = [FFMPEG, "-y", "-loglevel", "error", "-nostats", "-f", "rawvideo",
           "-pix_fmt", "rgb24", "-s", "%dx%d" % (W, H), "-r", str(FPS), "-i", "-",
           "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "19",
           "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT]
    errf = open(os.path.join(BUILD, "ffmpeg-err.txt"), "w+b")
    pr = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                          stderr=errf)
    for i in range(n):
        t = i / float(FPS)
        fr = render_at(t)
        pr.stdin.write(fr.tobytes())
        if i % 150 == 0:
            print("frame %d/%d  t=%.1fs" % (i, n, t), flush=True)
    pr.stdin.close()
    rc = pr.wait()
    errf.seek(0)
    err = errf.read().decode("utf-8", "ignore")
    print("ffmpeg rc =", rc)
    if rc != 0:
        print(err[-3000:])
        sys.exit(1)
    print("DONE", OUT, os.path.getsize(OUT), "bytes")

if __name__ == "__main__":
    main()
