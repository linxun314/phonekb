
# -*- coding: utf-8 -*-
"""PhoneKB 30s 原理说明片：无配音、画面无字幕，靠动效图解 HID 原理。"""
import os, math, sys, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_video_30s as mv
from PIL import Image, ImageDraw, ImageFilter

W, H, FPS = mv.W, mv.H, mv.FPS
BG, ACCENT, WHITE, DIM = mv.BG, mv.ACCENT, mv.WHITE, mv.DIM
clamp01, seg, eo, eio = mv.clamp01, mv.seg, mv.eo, mv.eio
BUILD = mv.BUILD
OUT = os.path.join(BUILD, "PhoneKB-30s-原理.mp4")
XF = 0.40

PHONE_FILL = (18, 22, 31)
PHONE_EDGE = (72, 94, 132)
TAB_FILL = (20, 26, 38)
TAB_EDGE = (58, 70, 92)
KEY_FILL = (40, 48, 62)
SCREEN = (12, 15, 22)

def A(c, a):
    return (int(c[0]), int(c[1]), int(c[2]), int(clamp01(a) * 255))

def rr(d, box, r, fill=None, outline=None, width=2, alpha=1.0):
    if alpha <= 0.01:
        return
    kw = {}
    if fill is not None:
        kw["fill"] = A(fill, alpha)
    if outline is not None:
        kw["outline"] = A(outline, alpha)
        kw["width"] = width
    d.rounded_rectangle([box[0], box[1], box[2], box[3]], radius=r, **kw)

def glow_blob(img, cx, cy, rx, ry, color=ACCENT, strength=1.0):
    g = Image.new("L", (int(rx * 2), int(ry * 2)), 0)
    ImageDraw.Draw(g).ellipse([0, 0, int(rx * 2) - 1, int(ry * 2) - 1], fill=110)
    g = g.filter(ImageFilter.GaussianBlur(max(6, int(min(rx, ry) * 0.55))))
    col = Image.new("RGBA", g.size, (color[0], color[1], color[2], 0))
    col.putalpha(g.point(lambda v: int(v * clamp01(strength))))
    img.paste(col, (int(cx - rx), int(cy - ry)), col)

def phone(lay, cx, cy, w, h, alpha=1.0, screen_alpha=1.0):
    d = ImageDraw.Draw(lay)
    rr(d, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), w * 0.14,
       fill=PHONE_FILL, outline=PHONE_EDGE, width=3, alpha=alpha)
    rr(d, (cx - w / 2 + w * 0.06, cy - h / 2 + w * 0.08, cx + w / 2 - w * 0.06, cy + h / 2 - w * 0.08),
       w * 0.11, fill=SCREEN, outline=(40, 52, 74), width=2, alpha=alpha * screen_alpha)

def tablet(lay, cx, cy, w, h, alpha=1.0, glow=0.0):
    d = ImageDraw.Draw(lay)
    rr(d, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), 28,
       fill=TAB_FILL, outline=TAB_EDGE, width=3, alpha=alpha)
    rr(d, (cx - w / 2 + 12, cy - h / 2 + 12, cx + w / 2 - 12, cy + h / 2 - 12), 20,
       fill=SCREEN, outline=(34, 42, 58), width=2, alpha=alpha)

def keygrid(lay, x0, y0, w, h, cols, rows, alpha=1.0, stagger_p=1.0, lit=None):
    d = ImageDraw.Draw(lay)
    gx, gy = 6, 6
    cw = (w - gx * (cols - 1)) / cols
    ch = (h - gy * (rows - 1)) / rows
    for r in range(rows):
        for c in range(cols):
            k = (r * cols + c) / float(cols * rows)
            a = clamp01((stagger_p - k) * 3.0)
            if a <= 0:
                continue
            x = x0 + c * (cw + gx)
            y = y0 + r * (ch + gy)
            lt = 0.0
            if lit is not None:
                lt = lit(r, c)
            col = (int(KEY_FILL[0] + (ACCENT[0] - KEY_FILL[0]) * lt * 0.55),
                   int(KEY_FILL[1] + (ACCENT[1] - KEY_FILL[1]) * lt * 0.55),
                   int(KEY_FILL[2] + (ACCENT[2] - KEY_FILL[2]) * lt * 0.55))
            rr(d, (x, y, x + cw, y + ch), 4, fill=col, alpha=alpha * a)

def doc_card(lay, cx, cy, w, h, alpha=1.0, rows=5, cols=7, tilt=0.0):
    d = ImageDraw.Draw(lay)
    rr(d, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), 10,
       fill=(226, 231, 240), outline=(150, 165, 190), width=2, alpha=alpha)
    pad = 10
    cw = (w - pad * 2) / cols
    ch = (h - pad * 2 - 6) / rows
    for r in range(rows):
        for c in range(cols):
            x = cx - w / 2 + pad + c * cw
            y = cy - h / 2 + pad + r * ch
            on = (r * 7 + c * 3) % 5
            col = (60, 80, 110) if on else (196, 204, 216)
            rr(d, (x + 2, y + 2, x + cw - 3, y + ch - 3), 2, fill=col, alpha=alpha * 0.95)

def byte_chip(lay, cx, cy, w=52, h=30, alpha=1.0):
    d = ImageDraw.Draw(lay)
    rr(d, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), 7,
       fill=(40, 74, 140), outline=(96, 150, 240), width=2, alpha=alpha)
    for i in range(3):
        x = cx - w * 0.28 + i * w * 0.28
        d.ellipse([x - 2.6, cy - 2.6, x + 2.6, cy + 2.6],
                  fill=A((210, 226, 255), alpha))

def cursor_arrow(lay, cx, cy, s, alpha=1.0):
    d = ImageDraw.Draw(lay)
    pts = [(cx, cy), (cx + s * 0.10, cy + s * 0.92), (cx + s * 0.34, cy + s * 0.66),
           (cx + s * 0.60, cy + s * 0.98), (cx + s * 0.74, cy + s * 0.88),
           (cx + s * 0.48, cy + s * 0.56), (cx + s * 0.80, cy + s * 0.50)]
    d.polygon(pts, fill=A((250, 252, 255), alpha))

# ---------------- BEAT A: 手机变成输入设备 ----------------
def beat_a(p):
    f = BG.convert("RGBA")
    lay = mv.layer()
    ta = eo(seg(p, 0.0, 0.35))
    tablet(lay, 1450, 545, 560, 390, alpha=0.45 * ta)
    cx, cy = 640, 545
    w, h = 270, 540
    phone(lay, cx, cy, w, h, 1.0)
    # 屏幕内键盘逐行长出
    gx0 = cx - w / 2 + 26
    gy0 = cy - h / 2 + 110
    keygrid(lay, gx0, gy0, w - 52, h - 250, 4, 5,
            alpha=1.0, stagger_p=eo(seg(p, 0.12, 0.72)),
            lit=lambda r, c: max(0.0, 1.0 - abs(((p * 2.2) % 1.0) - ((r * 4 + c) / 21.0)) * 4.0))
    # 手机外圈脉冲环
    d = ImageDraw.Draw(lay)
    for i in range(2):
        ph = ((p * 1.15) + i * 0.5) % 1.0
        if ph < 0.55:
            k = ph / 0.55
            pad = 18 + 70 * k
            a = (1 - k) * 0.55
            rr(d, (cx - w / 2 - pad, cy - h / 2 - pad, cx + w / 2 + pad, cy + h / 2 + pad),
               w * 0.14 + pad * 0.5, outline=ACCENT, width=2, alpha=a)
    # 蓝牙波纹
    mv.bt_waves(lay, 980, 470, seg(p, 0.35, 1.0) * 2.0, alpha=eo(seg(p, 0.4, 0.8)), spread=190)
    return mv.comp(f, lay)

# ---------------- BEAT B: 报告描述符 ----------------
def beat_b(p):
    f = BG.convert("RGBA")
    lay = mv.layer()
    phone(lay, 330, 545, 210, 420, 1.0)
    keygrid(lay, 330 - 76, 545 - 120, 152, 200, 3, 4, alpha=0.9, stagger_p=1.0)
    tablet(lay, 1430, 545, 600, 420, 1.0)
    # 平板屏幕里长出键盘（表示"被认成键盘"）
    landed = eo(seg(p, 0.52, 0.98))
    if landed > 0:
        keygrid(lay, 1430 - 230, 545 - 95, 460, 190, 8, 3, alpha=0.75, stagger_p=landed)
    # 三张说明书卡从手机飞向平板，进入平板前淡出
    for i in range(3):
        st = 0.05 + i * 0.15
        q = eo(seg(p, st, st + 0.46))
        if q <= 0:
            continue
        x = 470 + (1140 - 470) * q
        y = 480 - 80 * math.sin(q * math.pi) + i * 22
        a = clamp01(q * 4.0) * (1.0 - clamp01((q - 0.70) / 0.30))
        if a > 0.02:
            doc_card(lay, x, y, 148, 100, alpha=a, rows=4, cols=6)
    # 完成时平板外圈高亮
    if p > 0.72:
        d = ImageDraw.Draw(lay)
        k = eo(seg(p, 0.72, 0.98))
        rr(d, (1430 - 300 - 8 * k, 545 - 210 - 8 * k, 1430 + 300 + 8 * k, 545 + 210 + 8 * k),
           28, outline=ACCENT, width=3, alpha=0.85 * (1 - k * 0.25))
    return mv.comp(f, lay)

# ---------------- BEAT C: 真机截图快切 ----------------
SHOT_IMG = [mv.load_shot("01-keyboard-landscape-computer.png"),
            mv.load_shot("03-touchpad-landscape.png"),
            mv.load_shot("07-gamepad-landscape.png")]
CARD_W, CARD_H = mv.CARD_W, mv.CARD_H
CARD_X, CARD_Y = mv.CARD_X, mv.CARD_Y

def one_shot(src, q, ripple_xy):
    f = BG.convert("RGBA")
    zoom = 1.0 + 0.10 * eio(q)
    sw = mv.CORE_W / zoom
    sh = mv.CORE_H / zoom
    box = (mv.CPX - sw / 2, mv.CPY - sh / 2, mv.CPX + sw / 2, mv.CPY + sh / 2)
    crop = src.resize((CARD_W, CARD_H), Image.LANCZOS, box=box)
    f.paste(mv.SHADOW_LAYER, (CARD_X - mv.SHADOW_PAD, CARD_Y - mv.SHADOW_PAD), mv.SHADOW)
    f.paste(crop.convert("RGBA"), (CARD_X, CARD_Y), mv.CARD_MASK)
    lay = mv.layer()
    for k in (0.18, 0.62):
        ph = seg(q, k, k + 0.28)
        if 0 < ph < 1:
            x = CARD_X + ripple_xy[0] / mv.CORE_W * CARD_W
            y = CARD_Y + ripple_xy[1] / mv.CORE_H * CARD_H
            mv.ripple(lay, x, y, 14 + ph * 62, (1 - ph) * 0.8)
    return mv.comp(f, lay)

def beat_c(p):
    segs = [(0.0, 0.34, 0, (330, 396)), (0.34, 0.67, 1, (960, 300)), (0.67, 1.0, 2, (1922, 620))]
    for a, b, idx, rp in segs:
        if a <= p < b:
            q = (p - a) / (b - a)
            cur = one_shot(SHOT_IMG[idx], q, rp)
            fade = 0.16 / (b - a)
            if a > 0 and q < fade:
                prev = one_shot(SHOT_IMG[idx - 1], 1.0, segs[idx - 1][3])
                cur = Image.blend(prev, cur, eio(clamp01(q / fade)))
            return cur
    return one_shot(SHOT_IMG[2], 1.0, segs[2][3])

# ---------------- BEAT D: 数据流 ----------------
def beat_d(p):
    f = BG.convert("RGBA")
    lay = mv.layer()
    d = ImageDraw.Draw(lay)
    phone(lay, 300, 545, 215, 430, 1.0)
    keygrid(lay, 300 - 78, 545 - 110, 156, 190, 3, 4, alpha=0.9, stagger_p=1.0,
            lit=lambda r, c: (1.0 if (r * 3 + c) % 4 == 0 else 0.0) * (0.5 + 0.5 * math.sin(p * 9 + r)))
    # 按压点
    d.ellipse([300 - 13, 545 - 13, 300 + 13, 545 + 13], fill=A((255, 255, 255), 0.9))
    d.ellipse([300 - 26, 545 - 26, 300 + 26, 545 + 26], outline=A(ACCENT, 0.7), width=3)
    # 平板 + 三层
    tablet(lay, 1470, 545, 470, 400, 1.0)
    lx0, lx1 = 1470 - 170, 1470 + 170
    layer_tops = [450, 528, 606]
    cyc = (p * 1.9) % 1.0
    hit_stage = int(cyc * 3.2)
    for i, ty in enumerate(layer_tops):
        on = 1.0 if (hit_stage >= i) else 0.0
        col = (26 + 26 * on, 34 + 40 * on, 46 + 62 * on)
        rr(d, (lx0, ty, lx1, ty + 66), 10, fill=col,
           outline=(70 + 40 * on, 92 + 30 * on, 130 + 30 * on), width=2)
        for j in range(4):
            xx = lx0 + 18 + j * 78
            rr(d, (xx, ty + 18, xx + 56, ty + 27), 4, fill=(120, 150, 200), alpha=0.45 + 0.5 * on)
            rr(d, (xx, ty + 36, xx + 40, ty + 45), 4, fill=(120, 150, 200), alpha=0.3 + 0.5 * on)
    # App 窗口 + 光标
    app_on = clamp01((cyc - 0.62) / 0.38)
    if app_on > 0:
        ax0, ay0 = 1470 - 118, 356
        rr(d, (ax0, ay0, ax0 + 236, ay0 + 68), 10, fill=(22, 30, 44),
           outline=(90, 120, 170), width=2, alpha=0.4 + 0.6 * app_on)
        caret = 1.0 if (p * 5.0) % 1.0 < 0.55 else 0.25
        rr(d, (ax0 + 22, ay0 + 22, ax0 + 118, ay0 + 30), 4, fill=(140, 168, 214), alpha=app_on)
        rr(d, (ax0 + 22, ay0 + 40, ax0 + 148, ay0 + 48), 4, fill=(100, 124, 164), alpha=app_on * 0.8)
        rr(d, (ax0 + 156, ay0 + 22, ax0 + 162, ay0 + 50), 3, fill=(255, 255, 255), alpha=app_on * caret)
    # 字节包流动
    for i in range(4):
        ph = (p * 1.55 + i * 0.25) % 1.0
        x = 440 + (1300 - 440) * ph
        y = 545 - 30 * math.sin(ph * math.pi)
        a = clamp01(1.0 - abs(ph - 0.5) * 1.6)
        byte_chip(lay, x, y, 46, 26, alpha=a)
    # 包穿过 BT
    mv.bt_waves(lay, 880, 545, p * 2.2, alpha=0.8, spread=170)
    return mv.comp(f, lay)

# ---------------- BEAT E: 合并窗口 + 延迟 ----------------
def beat_e(p):
    f = BG.convert("RGBA")
    lay = mv.layer()
    d = ImageDraw.Draw(lay)
    axis_y = 620
    x0, x1 = 240, 1560
    d.line([x0, axis_y, x1, axis_y], fill=A((70, 82, 104), 1.0), width=3)
    for i in range(14):
        x = x0 + (x1 - x0) * i / 13.0
        d.line([x, axis_y - 8, x, axis_y + 8], fill=A((70, 82, 104), 1.0), width=2)
    cyc = (p * 1.7) % 1.0
    # 一串采样点挤进 2ms 窗口
    base = x0 + 240
    win_x0, win_x1 = base, base + 150
    pts = [base + 12, base + 44, base + 78, base + 112, base + 140]
    shown = seg(cyc, 0.0, 0.45)
    for i, px in enumerate(pts):
        if i > shown * len(pts):
            break
        a = 1.0
        if cyc > 0.45:
            a = clamp01(1.0 - (cyc - 0.45) / 0.20) if i < 4 else 1.0
        r = 15 if i == 4 else 12
        d.ellipse([px - r, axis_y - r, px + r, axis_y + r],
                  fill=A(ACCENT if i == 4 else (110, 128, 160), a))
    if cyc > 0.30:
        k = eo(seg(cyc, 0.30, 0.45))
        rr(d, (win_x0, axis_y - 52, win_x0 + (win_x1 - win_x0) * k, axis_y - 34), 8,
           outline=ACCENT, width=2, alpha=0.9)
    # 只发最新：单包飞到终点
    fly = eo(seg(cyc, 0.62, 0.95))
    if fly > 0:
        fx = base + 140 + (x1 - 60 - base - 140) * fly
        byte_chip(lay, fx, axis_y, 50, 28, alpha=1.0)
        d.ellipse([fx - 34, axis_y - 34, fx + 34, axis_y + 34], outline=A(ACCENT, 0.45), width=2)
    # 秒表弧 + 数字
    acx, acy, ar = x1 - 40, axis_y, 78
    if cyc > 0.75:
        k = eo(seg(cyc, 0.75, 1.0))
        d.arc([acx - ar, acy - ar, acx + ar, acy + ar], start=-90, end=-90 + 300 * k,
              fill=A(ACCENT, 0.9), width=5)
    ft = mv.font(46, True)
    txt = "8 ms"
    bb = d.textbbox((0, 0), txt, font=ft)
    d.text((acx - (bb[2] - bb[0]) / 2, acy - ar - 78), txt, font=ft, fill=A(WHITE, 0.9))
    ft2 = mv.font(24, False)
    t2 = "p50"
    bb2 = d.textbbox((0, 0), t2, font=ft2)
    d.text((acx - (bb2[2] - bb2[0]) / 2, acy - ar - 118), t2, font=ft2, fill=A(DIM, 0.85))
    return mv.comp(f, lay)

# ---------------- BEAT F: 系统输入层，任意 App ----------------
def beat_f(p):
    f = BG.convert("RGBA")
    lay = mv.layer()
    d = ImageDraw.Draw(lay)
    tablet(lay, 960, 520, 900, 560, 1.0)
    tiles = [(-190, -120), (190, -120), (-190, 120), (190, 120)]
    idx = min(3, int(eo(seg(p, 0.12, 0.72)) * 3.999))
    for i, (dx, dy) in enumerate(tiles):
        on = 1.0 if i == idx else 0.0
        bx0, by0 = 960 + dx - 150, 520 + dy - 92
        rr(d, (bx0, by0, bx0 + 300, by0 + 184), 14,
           fill=(24 + 22 * on, 32 + 34 * on, 44 + 52 * on),
           outline=(70 + 60 * on, 92 + 40 * on, 130 + 40 * on), width=2)
        rr(d, (bx0 + 22, by0 + 22, bx0 + 168, by0 + 34), 5,
           fill=(120, 148, 194), alpha=0.45 + 0.5 * on)
        rr(d, (bx0 + 22, by0 + 46, bx0 + 120, by0 + 58), 5,
           fill=(110, 134, 176), alpha=0.3 + 0.5 * on)
    t = eo(seg(p, 0.12, 0.72))
    pos = [(-190, -120), (190, -120), (190, 120), (-190, 120)]
    i0 = min(3, int(t * 3.999))
    i1 = min(3, i0 + 1)
    lt = t * 3.999 - i0
    cxp = 960 + pos[i0][0] + (pos[i1][0] - pos[i0][0]) * lt
    cyp = 520 + pos[i0][1] + (pos[i1][1] - pos[i0][1]) * lt + 30
    cursor_arrow(lay, cxp, cyp, 44, 1.0)
    # 投屏对比：被划掉
    if p > 0.70:
        k = eo(seg(p, 0.70, 0.95))
        bx, by = 1450, 880
        rr(d, (bx - 150, by - 56, bx + 150, by + 56), 12,
           fill=(28, 22, 26), outline=(150, 80, 80), width=2, alpha=0.75 * k)
        for j in range(3):
            rr(d, (bx - 132, by - 40 + j * 26, bx - 132 + 150, by - 26 + j * 26), 5,
               fill=(150, 108, 108), alpha=0.5 * k)
        d.line([bx - 160, by - 66, bx + 160, by + 66], fill=A((230, 90, 90), k), width=6)
        d.line([bx + 160, by - 66, bx - 160, by + 66], fill=A((230, 90, 90), k), width=6)
    return mv.comp(f, lay)

# ---------------- timeline ----------------
SHOTS = [
    (0.0, 4.6, beat_a),
    (4.6, 9.2, beat_b),
    (9.2, 13.4, beat_c),
    (13.4, 18.6, beat_d),
    (18.6, 22.8, beat_e),
    (22.8, 26.6, beat_f),
    (26.6, 30.0, mv.shot_end),
]

def render_at(t):
    idx = len(SHOTS) - 1
    for i, (a, b, fn) in enumerate(SHOTS):
        if a <= t < b:
            idx = i
            break
    a, b, fn = SHOTS[idx]
    p = clamp01((t - a) / (b - a))
    cur = fn(p)
    if idx > 0 and t - a < XF:
        prev = SHOTS[idx - 1][2](1.0)
        cur = Image.blend(prev, cur, eio(clamp01((t - a) / XF)))
    return cur.convert("RGB")

def main():
    n = int(30.0 * FPS)
    cmd = [mv.FFMPEG, "-y", "-loglevel", "error", "-nostats", "-f", "rawvideo",
           "-pix_fmt", "rgb24", "-s", "%dx%d" % (W, H), "-r", str(FPS), "-i", "-",
           "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "19",
           "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT]
    errf = open(os.path.join(BUILD, "ffmpeg-err2.txt"), "w+b")
    pr = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=errf)
    for i in range(n):
        pr.stdin.write(render_at(i / float(FPS)).tobytes())
        if i % 150 == 0:
            print("frame %d/%d" % (i, n), flush=True)
    pr.stdin.close()
    rc = pr.wait()
    errf.seek(0)
    err = errf.read().decode("utf-8", "ignore")
    print("ffmpeg rc =", rc)
    if rc != 0:
        print(err[-2500:])
        sys.exit(1)
    print("DONE", OUT, os.path.getsize(OUT))

if __name__ == "__main__":
    main()
