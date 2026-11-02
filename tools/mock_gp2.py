# -*- coding: utf-8 -*-
"""Gamepad page mockups v2: movement pad type (buttons / round hold-disc) + size presets.
All geometry is in dp; P() converts to pixels only at draw primitives."""
import math
from PIL import Image, ImageDraw, ImageFont

K = 2.75
Wd, Hd = 1080.0 / K, 2400.0 / K      # 392.7 x 872.7 dp
WdL, HdL = 2400.0 / K, 1080.0 / K    # 872.7 x 392.7 dp

BG = (15, 17, 21)
SURFACE = (23, 27, 34)
SURFACE2 = (31, 37, 48)
KEY = (38, 45, 58)
KEYP = (59, 73, 104)
ACCENT = (76, 141, 255)
TEXT = (233, 236, 242)
DIM = (152, 161, 179)
OK = (61, 220, 132)
FB = "C:/Windows/Fonts/msyh.ttc"
FBD = "C:/Windows/Fonts/msyhbd.ttc"


def P(v):
    return int(round(v * K))


_fonts = {}


def font(sp, bold=False):
    key = (round(sp, 2), bold)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(FBD if bold else FB, max(8, P(sp)))
    return _fonts[key]


def rr(d, box, r, fill=None, outline=None, width=1.0):
    d.rounded_rectangle([P(box[0]), P(box[1]), P(box[2]), P(box[3])],
                        radius=P(r), fill=fill, outline=outline,
                        width=max(1, P(width)) if outline else 1)


def ellipse(d, cx, cy, rx, ry=None, fill=None, outline=None, width=1.0):
    ry = rx if ry is None else ry
    d.ellipse([P(cx - rx), P(cy - ry), P(cx + rx), P(cy + ry)],
              fill=fill, outline=outline, width=max(1, P(width)) if outline else 1)


def tcenter(d, s, x, y, f, fill):
    d.text((P(x), P(y)), s, font=f, fill=fill, anchor="mm")


def tleft(d, s, x, y, f, fill, anchor="la"):
    d.text((P(x), P(y)), s, font=f, fill=fill, anchor=anchor)


def chevron(d, cx, cy, ang, size, w, color):
    a = math.radians(ang)
    tipx = cx + math.cos(a) * size * 0.5
    tipy = cy + math.sin(a) * size * 0.5
    backx = cx - math.cos(a) * size * 0.5
    backy = cy - math.sin(a) * size * 0.5
    perpx = -math.sin(a) * size * 0.5
    perpy = math.cos(a) * size * 0.5
    d.line([(P(backx + perpx), P(backy + perpy)), (P(tipx), P(tipy)),
            (P(backx - perpx), P(backy - perpy))], fill=color, width=max(2, P(w)), joint="curve")


def power_icon(d, cx, cy, r, color):
    d.arc([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], start=50, end=310, fill=color, width=P(2.2))
    d.line([(P(cx), P(cy - r - 2.6)), (P(cx), P(cy - 0.8))], fill=color, width=P(2.2))


def blend(c1, c2, f):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * f)) for i in range(3))


def seg(d, x, y, h, cells, cellw, fsize=12):
    n = len(cells)
    rr(d, [x, y, x + cellw * n, y + h], h / 2.0, fill=SURFACE2)
    for i, (label, sel) in enumerate(cells):
        cx = x + cellw * i + cellw / 2.0
        cy = y + h / 2.0
        if sel:
            rr(d, [x + cellw * i + 2, y + 2, x + cellw * (i + 1) - 2, y + h - 2], (h - 4) / 2.0, fill=KEYP)
        tcenter(d, label, cx, cy, font(fsize), ACCENT if sel else DIM)


def draw_right_card(d, box):
    x0, y0, x1, y1 = box
    inner, shoulder_h, bottom_h, gap = 12.0, 30.0, 28.0, 8.0
    pillw = ((x1 - x0) - inner * 2 - gap) / 2.0
    lx = x0 + inner
    top = y0 + inner
    rr(d, [lx, top, lx + pillw, top + shoulder_h], 12, fill=SURFACE2)
    rr(d, [lx + pillw + gap, top, lx + pillw * 2 + gap, top + shoulder_h], 12, fill=SURFACE2)
    tcenter(d, "L1", lx + pillw / 2, top + shoulder_h / 2, font(12), TEXT)
    tcenter(d, "R1", lx + pillw * 1.5 + gap, top + shoulder_h / 2, font(12), TEXT)
    by = y1 - inner - bottom_h
    rr(d, [lx, by, lx + pillw, by + bottom_h], 12, fill=SURFACE2)
    rr(d, [lx + pillw + gap, by, lx + pillw * 2 + gap, by + bottom_h], 12, fill=SURFACE2)
    tcenter(d, "SELECT", lx + pillw / 2, by + bottom_h / 2, font(11), TEXT)
    tcenter(d, "START", lx + pillw * 1.5 + gap, by + bottom_h / 2, font(11), TEXT)
    reg_top = top + shoulder_h + gap
    reg_bot = by - gap
    fcy = (reg_top + reg_bot) / 2.0
    fcx = (x0 + x1) / 2.0
    r = min(26.0, (reg_bot - reg_top) / 2.9, (x1 - x0) * 0.13)
    off = r * 1.5
    faces = [("Y", fcx, fcy - off, (90, 71, 22)), ("X", fcx - off, fcy, (34, 64, 95)),
             ("B", fcx + off, fcy, (91, 38, 43)), ("A", fcx, fcy + off, (31, 81, 54))]
    for label, cx, cy, fill in faces:
        ellipse(d, cx, cy, r, fill=fill)
        tcenter(d, label, cx, cy, font(15, True), TEXT)


def draw_button_pad(d, box, pressed=None):
    x0, y0, x1, y1 = box
    cw, ch = x1 - x0, y1 - y0
    s = min(cw * 0.28, ch * 0.26)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    off = s * 1.10
    dirs = [("up", 0, -1, -90), ("down", 0, 1, 90), ("left", -1, 0, 180), ("right", 1, 0, 0)]
    for name, dx, dy, ang in dirs:
        kx, ky = cx + dx * off, cy + dy * off
        on = pressed == name
        rr(d, [kx - s / 2, ky - s / 2, kx + s / 2, ky + s / 2], s * 0.28, fill=KEYP if on else KEY)
        chevron(d, kx, ky, ang, s * 0.46, max(2.2, s * 0.10), ACCENT if on else TEXT)


DIR_ANGLES = {"up": (-135, -45), "upright": (-90, 0), "right": (-45, 45), "downright": (0, 90),
              "down": (45, 135), "downleft": (90, 180), "left": (135, 225), "upleft": (180, 270)}
DIR_LIT = {"up": {"up"}, "down": {"down"}, "left": {"left"}, "right": {"right"},
           "upright": {"up", "right"}, "downright": {"down", "right"},
           "downleft": {"down", "left"}, "upleft": {"up", "left"}}
DIR_TEXT = {"up": "当前：上", "down": "当前：下", "left": "当前：左", "right": "当前：右",
            "upright": "当前：右上", "downright": "当前：右下",
            "downleft": "当前：左下", "upleft": "当前：左上"}


def draw_disc_pad(d, box, pressed=None):
    x0, y0, x1, y1 = box
    cw, ch = x1 - x0, y1 - y0
    r = min(cw * 0.40, ch * 0.42)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    ellipse(d, cx, cy, r, fill=KEY, outline=blend(KEY, TEXT, 0.10), width=1.2)
    lit = set()
    if pressed in DIR_ANGLES:
        s0, s1 = DIR_ANGLES[pressed]
        d.pieslice([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], start=s0, end=s1,
                   fill=blend(KEY, ACCENT, 0.30))
        lit = DIR_LIT[pressed]
    # diagonal hint dots
    for ang in (45, 135, 225, 315):
        a = math.radians(ang)
        ellipse(d, cx + math.cos(a) * r * 0.86, cy + math.sin(a) * r * 0.86, 1.0, fill=blend(KEY, TEXT, 0.22))
    for name, dx, dy, ang in [("up", 0, -1, -90), ("right", 1, 0, 0), ("down", 0, 1, 90), ("left", -1, 0, 180)]:
        ax, ay = cx + dx * r * 0.60, cy + dy * r * 0.60
        chevron(d, ax, ay, ang, r * 0.44, max(2.5, r * 0.11), ACCENT if name in lit else TEXT)
    ellipse(d, cx, cy, r * 0.15, fill=SURFACE2)
    ellipse(d, cx, cy, r * 0.055, fill=DIM)
    hint = DIR_TEXT.get(pressed, "按住滑动换方向")
    tcenter(d, hint, cx, cy + r + 14, font(10), ACCENT if pressed else DIM)


def draw_settings_row(d, y, mode, size):
    h = 30.0
    yy = y + 4
    x = 14.0
    tleft(d, "移动盘", x, yy + h / 2, font(12), DIM, anchor="lm")
    x += 40
    seg(d, x, yy, h, [("按键", mode == "buttons"), ("圆盘", mode == "disc")], 50)
    x += 100 + 14
    tleft(d, "大小", x, yy + h / 2, font(12), DIM, anchor="lm")
    x += 30
    seg(d, x, yy, h, [("小", size == "small"), ("中", size == "mid"), ("大", size == "large")], 34)
    return y + 42


def draw_status_portrait(d):
    tleft(d, "10:47", 16, 9, font(14), TEXT)
    rr(d, [Wd - 54, 12, Wd - 30, 24], 3, outline=TEXT, width=1.2)
    d.rectangle([P(Wd - 50), P(14), P(Wd - 36), P(22)], fill=OK)
    d.rectangle([P(Wd - 29), P(16), P(Wd - 27), P(20)], fill=TEXT)


def draw_header_portrait(d):
    rr(d, [14, 34, 62, 82], 16, fill=KEY)
    power_icon(d, 38, 58, 9, TEXT)
    ellipse(d, 78.5, 53.5, 4.5, fill=OK)
    tleft(d, "已连接", 88, 37, font(16, True), TEXT)
    tleft(d, "iQOO Pad2", 88, 60, font(12), DIM)
    x = Wd - 14
    for label in ["帮助", "伴侣", "旋转"]:
        x -= 56
        rr(d, [x, 36, x + 56, 70], 10, fill=SURFACE2)
        tcenter(d, label, x + 28, 53, font(12.5), TEXT)
        x -= 6


def draw_tabs_portrait(d):
    tabs = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
    y = 92.0
    x = 16.0
    for t in tabs:
        tw = d.textlength(t, font=font(13)) / K
        cellw = tw + 24
        if t == "手柄":
            rr(d, [x - 10, y, x + tw + 10, y + 32], 12, fill=SURFACE2)
        tcenter(d, t, x + tw / 2, y + 16, font(13), TEXT if t == "手柄" else DIM)
        x += cellw


def draw_toolbar_land(d):
    rr(d, [10, 7, 46, 39], 12, fill=KEY)
    power_icon(d, 28, 23, 8, TEXT)
    ellipse(d, 54.5, 22.5, 4.2, fill=OK)
    tleft(d, "已连接 · iQOO Pad2", 64, HdL * 0 + 15, font(15, True), TEXT, anchor="la")
    x = 250.0
    for t in ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]:
        tw = d.textlength(t, font=font(13)) / K
        if t == "手柄":
            rr(d, [x - 9, 7, x + tw + 9, 37], 11, fill=SURFACE2)
        tcenter(d, t, x + tw / 2, 22, font(13), TEXT if t == "手柄" else DIM)
        x += tw + 24
    x = WdL - 10
    for label in ["帮助", "伴侣", "旋转"]:
        x -= 56
        rr(d, [x, 7, x + 56, 37], 10, fill=SURFACE2)
        tcenter(d, label, x + 28, 22, font(12.5), TEXT)
        x -= 6


def render_portrait(mode="buttons", size="mid", pressed=None, variant="A", caption="", show_caption=True):
    img = Image.new("RGB", (1080, 2400), BG)
    d = ImageDraw.Draw(img)
    draw_status_portrait(d)
    draw_header_portrait(d)
    draw_tabs_portrait(d)
    content_top = 136.0
    cards_top = draw_settings_row(d, content_top, mode, size) if variant == "A" else content_top + 4
    pad, gap = 10.0, 10.0
    avail = Wd - pad * 2 - gap
    pct = {"small": 0.36, "mid": 0.42, "large": 0.50}[size]
    leftw = avail * pct
    cards_bottom = Hd - 44
    left = (pad, cards_top, pad + leftw, cards_bottom)
    right = (pad + leftw + gap, cards_top, Wd - pad, cards_bottom)
    rr(d, left, 22, fill=SURFACE)
    rr(d, right, 22, fill=SURFACE)
    if mode == "buttons":
        draw_button_pad(d, left, pressed)
    else:
        draw_disc_pad(d, left, pressed)
    draw_right_card(d, right)
    if variant == "B":
        chip = [left[0] + 8, left[1] + 8, left[0] + 8 + 72, left[1] + 8 + 30]
        rr(d, chip, 12, fill=SURFACE2)
        tleft(d, "移动盘", chip[0] + 12, chip[1] + 5, font(11), TEXT)
        chevron(d, chip[2] - 14, chip[1] + 16, 90, 8, 1.8, TEXT)
    if show_caption and caption:
        tcenter(d, caption, Wd / 2, Hd - 20, font(11), DIM)
    return img


def render_landscape(mode="disc", size="mid", pressed=None):
    img = Image.new("RGB", (2400, 1080), BG)
    d = ImageDraw.Draw(img)
    draw_toolbar_land(d)
    cards_top = draw_settings_row(d, 46, mode, size)
    pad, gap = 10.0, 10.0
    avail = WdL - pad * 2 - gap
    pct = {"small": 0.36, "mid": 0.42, "large": 0.50}[size]
    leftw = avail * pct
    cards_bottom = HdL - 10
    left = (pad, cards_top, pad + leftw, cards_bottom)
    right = (pad + leftw + gap, cards_top, WdL - pad, cards_bottom)
    rr(d, left, 22, fill=SURFACE)
    rr(d, right, 22, fill=SURFACE)
    if mode == "buttons":
        draw_button_pad(d, left, pressed)
    else:
        draw_disc_pad(d, left, pressed)
    draw_right_card(d, right)
    return img


def render_popup(mode="buttons", size="mid"):
    img = render_portrait(mode=mode, size=size, variant="B", show_caption=False).convert("RGBA")
    ov = Image.new("RGBA", img.size, (0, 0, 0, 120))
    img = Image.alpha_composite(img, ov).convert("RGB")
    d = ImageDraw.Draw(img)
    pw, ph = 322.0, 244.0
    px, py = (Wd - pw) / 2.0, (Hd - ph) / 2.0 - 26
    rr(d, [px, py, px + pw, py + ph], 20, fill=SURFACE, outline=(60, 70, 90), width=1.2)
    tleft(d, "手柄设置", px + 20, py + 16, font(17, True), TEXT)
    ry = py + 60
    tleft(d, "移动盘类型", px + 20, ry + 15, font(12), TEXT, anchor="lm")
    seg(d, px + pw - 20 - 104, ry, 30, [("按键", mode == "buttons"), ("圆盘", mode == "disc")], 52)
    ry2 = py + 110
    tleft(d, "移动盘大小", px + 20, ry2 + 15, font(12), TEXT, anchor="lm")
    seg(d, px + pw - 20 - 102, ry2, 30,
        [("小", size == "small"), ("中", size == "mid"), ("大", size == "large")], 34)
    tleft(d, "圆盘：按住并滑动选择方向，松手回中", px + 20, py + 158, font(10), DIM)
    tcenter(d, "取消", px + 66, py + ph - 32, font(13), DIM)
    rr(d, [px + pw - 20 - 88, py + ph - 52, px + pw - 20, py + ph - 14], 19, fill=ACCENT)
    tcenter(d, "保存", px + pw - 20 - 44, py + ph - 33, font(13), (10, 16, 24))
    tcenter(d, "点左卡左上角「移动盘」打开设置（方案 B）", Wd / 2, Hd - 20, font(11), DIM)
    return img


def compose_sizes():
    items = [("小", "small", "卡宽 36% · 盘径≈109dp · 按键≈41dp"),
             ("中", "mid", "卡宽 42% · 盘径≈128dp · 按键≈48dp"),
             ("大", "large", "卡宽 50% · 盘径≈152dp · 按键≈57dp")]
    pages = [(n, sub, render_portrait(mode="disc", size=k, show_caption=False)) for n, k, sub in items]
    pw, phh = 475, 1056
    gap = 40
    canvas = Image.new("RGB", (pw * 3 + gap * 4, phh + gap * 2 + 96), (8, 9, 12))
    cd = ImageDraw.Draw(canvas)
    for i, (name, sub, im) in enumerate(pages):
        x = gap + i * (pw + gap)
        canvas.paste(im.resize((pw, phh), Image.LANCZOS), (x, gap))
        cd.text((x + pw / 2, gap + phh + 24), name, font=ImageFont.truetype(FBD, 34), fill=TEXT, anchor="mm")
        cd.text((x + pw / 2, gap + phh + 64), sub, font=ImageFont.truetype(FB, 22), fill=DIM, anchor="mm")
    return canvas


out = "C:/Users/22453/Desktop/phoneKB/"
render_portrait("buttons", "mid", caption="方案 A · 移动盘=按键（原样式，随大小档位缩放）").save(out + "mock-gp2-a-buttons.png")
render_portrait("disc", "mid", caption="方案 A · 移动盘=圆盘（按住滑动换方向，松手回中）").save(out + "mock-gp2-a-disc.png")
render_portrait("disc", "mid", pressed="upright", caption="圆盘按住右上：扇区高亮，滑动可切到任意方向（含斜向）").save(out + "mock-gp2-a-disc-pressed.png")
compose_sizes().save(out + "mock-gp2-a-sizes.png")
render_popup().save(out + "mock-gp2-b-popup.png")
render_landscape("disc", "mid").save(out + "mock-gp2-l-disc.png")
render_landscape("buttons", "mid").save(out + "mock-gp2-l-buttons.png")
print("OK")
