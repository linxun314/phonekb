# -*- coding: utf-8 -*-
"""Media page redesign mockups: scheme A (two-row media keys) and B (single row)."""
from PIL import Image, ImageDraw, ImageFont

K = 2.75  # px per dp
BG = (15, 17, 21)
SURF = (23, 27, 34)
SURF2 = (31, 37, 48)
KEY = (38, 45, 58)
ACCENT = (76, 141, 255)
TEXT = (233, 236, 242)
DIM = (152, 161, 179)
OK = (61, 220, 132)

FONT = "C:/Windows/Fonts/msyh.ttc"
FONTB = "C:/Windows/Fonts/msyhbd.ttc"
_fc = {}


def f(size, bold=False):
    k = (size, bold)
    if k not in _fc:
        _fc[k] = ImageFont.truetype(FONTB if bold else FONT, int(round(size * K)))
    return _fc[k]


def P(v):
    return int(round(v * K))


def rr(d, box, r, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = [P(v) for v in box]
    d.rounded_rectangle([x0, y0, x1, y1], radius=P(r), fill=fill, outline=outline, width=int(width))


def txt(d, xy, s, size, fill, anchor="mm", bold=False):
    d.text((P(xy[0]), P(xy[1])), s, font=f(size, bold), fill=fill, anchor=anchor)


def tri(d, cx, cy, w, h, dirx):
    pts = [(cx + dirx * w / 2, cy), (cx - dirx * w / 2, cy - h / 2), (cx - dirx * w / 2, cy + h / 2)]
    d.polygon([(P(a), P(b)) for a, b in pts], fill=TEXT)


def bar(d, cx, cy, w, h):
    d.rounded_rectangle([P(cx - w / 2), P(cy - h / 2), P(cx + w / 2), P(cy + h / 2)], radius=P(1.5), fill=TEXT)


def ic_prev(d, cx, cy, s):
    bar(d, cx - s * 0.62, cy, s * 0.16, s * 0.94)
    tri(d, cx + s * 0.12, cy, s * 0.72, s * 0.94, -1)


def ic_next(d, cx, cy, s):
    bar(d, cx + s * 0.62, cy, s * 0.16, s * 0.94)
    tri(d, cx - s * 0.12, cy, s * 0.72, s * 0.94, 1)


def ic_play(d, cx, cy, s):
    tri(d, cx - s * 0.10, cy, s * 0.62, s * 0.92, 1)
    bar(d, cx + s * 0.30, cy, s * 0.16, s * 0.92)
    bar(d, cx + s * 0.54, cy, s * 0.16, s * 0.92)


def ic_rew(d, cx, cy, s):
    tri(d, cx - s * 0.24, cy, s * 0.56, s * 0.86, -1)
    tri(d, cx + s * 0.26, cy, s * 0.56, s * 0.86, -1)


def ic_ff(d, cx, cy, s):
    tri(d, cx - s * 0.26, cy, s * 0.56, s * 0.86, 1)
    tri(d, cx + s * 0.24, cy, s * 0.56, s * 0.86, 1)


def ic_vol(d, cx, cy, s, kind="up"):
    body = [(cx - s * 0.72, cy - s * 0.16), (cx - s * 0.34, cy - s * 0.16), (cx + s * 0.02, cy - s * 0.52),
            (cx + s * 0.02, cy + s * 0.52), (cx - s * 0.34, cy + s * 0.16), (cx - s * 0.72, cy + s * 0.16)]
    d.polygon([(P(a), P(b)) for a, b in body], fill=TEXT)
    if kind == "up":
        d.arc([P(cx + s * 0.02), P(cy - s * 0.46), P(cx + s * 0.60), P(cy + s * 0.46)], -70, 70, fill=TEXT, width=P(1.5))
        d.arc([P(cx + s * 0.02), P(cy - s * 0.78), P(cx + s * 0.92), P(cy + s * 0.78)], -70, 70, fill=TEXT, width=P(1.5))
    elif kind == "down":
        d.arc([P(cx + s * 0.02), P(cy - s * 0.46), P(cx + s * 0.60), P(cy + s * 0.46)], -70, 70, fill=TEXT, width=P(1.5))
    else:
        bar(d, cx + s * 0.62, cy, s * 0.62, s * 0.16)
        d.line([P(cx + s * 0.66), P(cy - s * 0.30), P(cx + s * 1.02), P(cy + s * 0.30)], fill=TEXT, width=P(1.5))
        d.line([P(cx + s * 1.02), P(cy - s * 0.30), P(cx + s * 0.66), P(cy + s * 0.30)], fill=TEXT, width=P(1.5))


def ic_cc(d, cx, cy, s):
    rr(d, (cx - s * 0.86, cy - s * 0.52, cx + s * 0.86, cy + s * 0.52), s * 0.22, outline=TEXT, width=P(1.4))
    txt(d, (cx, cy + 0.02 * s), "CC", 11, TEXT, bold=True)


def ic_sun(d, cx, cy, s, kind="up"):
    r = s * 0.30
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], outline=TEXT, width=P(1.6))
    import math
    for i in range(8):
        a = math.radians(i * 45)
        x0, y0 = cx + math.cos(a) * r * 1.55, cy + math.sin(a) * r * 1.55
        x1, y1 = cx + math.cos(a) * r * 2.15, cy + math.sin(a) * r * 2.15
        d.line([P(x0), P(y0), P(x1), P(y1)], fill=TEXT, width=P(1.6))
    if kind == "up":
        bar(d, cx + s * 1.05, cy, s * 0.62, s * 0.18)
        d.line([P(cx + s * 0.74), P(cy - s * 0.31), P(cx + s * 1.34), P(cy - s * 0.31)], fill=TEXT, width=P(1.8))
    else:
        bar(d, cx + s * 1.05, cy, s * 0.62, s * 0.18)


def ic_back(d, cx, cy, s):
    d.line([P(cx + s * 0.66), P(cy), P(cx - s * 0.62), P(cy)], fill=TEXT, width=P(2.2))
    d.line([P(cx - s * 0.62), P(cy), P(cx - s * 0.10), P(cy - s * 0.48)], fill=TEXT, width=P(2.2))
    d.line([P(cx - s * 0.62), P(cy), P(cx - s * 0.10), P(cy + s * 0.48)], fill=TEXT, width=P(2.2))


def ic_home(d, cx, cy, s):
    d.line([P(cx - s * 0.82), P(cy - s * 0.04), P(cx), P(cy - s * 0.72), P(cx + s * 0.82), P(cy - s * 0.04)],
           fill=TEXT, width=P(2.2), joint="curve")
    d.line([P(cx - s * 0.60), P(cy - s * 0.10), P(cx - s * 0.60), P(cy + s * 0.66), P(cx + s * 0.60), P(cy + s * 0.66),
            P(cx + s * 0.60), P(cy - s * 0.10)], fill=TEXT, width=P(2.2), joint="curve")


def ic_menu(d, cx, cy, s):
    for i, dy in enumerate((-0.44, 0.0, 0.44)):
        d.line([P(cx - s * 0.72), P(cy + s * dy), P(cx + s * 0.72), P(cy + s * dy)], fill=TEXT, width=P(2.2))


def ic_power(d, cx, cy, s):
    r = s * 0.66
    d.arc([P(cx - r), P(cy - r * 0.9), P(cx + r), P(cy + r * 0.9)], 118, 62, fill=TEXT, width=P(2.2))
    d.line([P(cx), P(cy - s * 0.86), P(cx), P(cy - s * 0.12)], fill=TEXT, width=P(2.2))

def ic_recents(d, cx, cy, s):
    rr(d, (cx - s * 0.86, cy - s * 0.62, cx + s * 0.34, cy + s * 0.18), s * 0.16, outline=TEXT, width=P(1.9))
    rr(d, (cx - s * 0.34, cy - s * 0.18, cx + s * 0.86, cy + s * 0.62), s * 0.16, outline=TEXT, width=P(1.9))


def ic_shot(d, cx, cy, s):
    rr(d, (cx - s * 0.88, cy - s * 0.56, cx + s * 0.88, cy + s * 0.62), s * 0.20, outline=TEXT, width=P(1.9))
    d.ellipse([P(cx - s * 0.46), P(cy - s * 0.30), P(cx + s * 0.16), P(cy + s * 0.32)], outline=TEXT, width=P(1.9))
    d.rectangle([P(cx + s * 0.34), P(cy - s * 0.52), P(cx + s * 0.62), P(cy - s * 0.34)], fill=TEXT)


ICONS = {
    "volup": lambda d, x, y, s: ic_vol(d, x, y, s, "up"),
    "voldn": lambda d, x, y, s: ic_vol(d, x, y, s, "down"),
    "mute": lambda d, x, y, s: ic_vol(d, x, y, s, "mute"),
    "cc": ic_cc,
    "sunup": lambda d, x, y, s: ic_sun(d, x, y, s, "up"),
    "sundn": lambda d, x, y, s: ic_sun(d, x, y, s, "down"),
    "back": ic_back,
    "home": ic_home,
    "menu": ic_menu,
    "recents": ic_recents,
    "shot": ic_shot,
    "power": ic_power,
    "prev": ic_prev,
    "next": ic_next,
    "play": ic_play,
    "rew": ic_rew,
    "ff": ic_ff,
}


def chrome(d, W, H, landscape):
    if not landscape:
        txt(d, (14, 12), "9:31", 13, TEXT, anchor="lm", bold=True)
        d.rounded_rectangle([P(W - 46), P(7), P(W - 16), P(19)], radius=P(3), outline=DIM, width=P(1))
        d.rectangle([P(W - 44), P(9), P(W - 26), P(17)], fill=OK)
    else:
        d.rounded_rectangle([P(W - 46), P(10), P(W - 16), P(22)], radius=P(3), outline=DIM, width=P(1))
        d.rectangle([P(W - 44), P(12), P(W - 26), P(20)], fill=OK)
    if landscape:
        y = 30
        rr(d, (14, 8, 58, 52), 12, fill=SURF2)
        ic_power(d, 36, 30, 11)
        d.ellipse([P(66), P(26), P(76), P(36)], fill=OK)
        txt(d, (82, 22), "已连接 · iQOO Pad2", 16, TEXT, anchor="lm", bold=True)
        tabs = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
        x = 268
        for t in tabs:
            w = 66
            if t == "媒体":
                rr(d, (x, 14, x + w, 46), 12, fill=SURF2)
            txt(d, (x + w / 2, 30), t, 13, TEXT if t == "媒体" else DIM, bold=(t == "媒体"))
            x += w + 6
        xr = W - 14
        for t in ["帮助", "伴侣", "旋转"]:
            w = 56
            xr -= w
            rr(d, (xr, 14, xr + w, 46), 12, fill=SURF2)
            txt(d, (xr + w / 2, 30), t, 14, TEXT)
            xr -= 6
        return
    rr(d, (16, 30, 60, 74), 12, fill=SURF2)
    ic_power(d, 38, 52, 11)
    d.ellipse([P(68), P(48), P(78), P(58)], fill=OK)
    txt(d, (86, 42), "已连接", 17, TEXT, anchor="lm", bold=True)
    txt(d, (86, 64), "iQOO Pad2", 12, DIM, anchor="lm")
    xr = W - 16
    for t in ["帮助", "伴侣", "旋转"]:
        w = 58
        xr -= w
        rr(d, (xr, 34, xr + w, 70), 12, fill=SURF2)
        txt(d, (xr + w / 2, 52), t, 14, TEXT)
        xr -= 8
    tabs = ["键盘", "触控板", "媒体", "手柄", "双手", "单手"]
    iw = (W - 32 - 5 * 6) / 6.0
    x = 16
    for t in tabs:
        if t == "媒体":
            rr(d, (x, 86, x + iw, 124), 12, fill=SURF2)
        txt(d, (x + iw / 2, 105), t, 14, TEXT if t == "媒体" else DIM, bold=(t == "媒体"))
        x += iw + 6


def blank_and_handle(d, W, H, dockTop, share):
    cy = (124 + dockTop) / 2 - 10
    txt(d, (W / 2, cy), "上方留空 · 拖动下方分隔条调整功能区高度", 12, DIM)
    txt(d, (W / 2, cy + 22), "（功能区贴着屏幕底部，方便拇指操作）", 11, DIM)
    hy = dockTop - 26
    txt(d, (12, hy), "拖动调整功能区", 11, DIM, anchor="lm")
    d.rounded_rectangle([P(W / 2 - 38), P(hy - 3), P(W / 2 + 38), P(hy + 3)], radius=P(3), fill=DIM)
    txt(d, (W - 12, hy), "功能区 70% / 留空 30%", 11, DIM, anchor="rm")


def toolpad(d, x, y, w, h=44):
    rr(d, (x, y, x + w, y + h), 14, fill=SURF, outline=ACCENT, width=P(1.4))
    txt(d, (x + w / 2, y + h / 2), "触控板", 15, ACCENT, bold=True)


def key(d, x, y, w, h, icon=None, label=None, sz=13, radius=14, fill=KEY):
    rr(d, (x, y, x + w, y + h), radius, fill=fill)
    cx, cy = x + w / 2, y + h / 2
    if icon:
        ICONS[icon](d, cx, cy, sz)
    if label:
        txt(d, (cx, cy + (sz + 11 if icon else 0)), label, 13 if not icon else 10, TEXT if not icon else DIM)


def circle_key(d, cx, cy, r, icon, label=None):
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], fill=KEY)
    ICONS[icon](d, cx, cy, r * 0.52)
    if label:
        txt(d, (cx, cy + r + 11), label, 9.5, DIM)


def media_card_A(d, x, y, w, target=None):
    h1, h2 = 62, 44
    h = 10 + h1 + 8 + h2 + 10
    top_extra = 0
    if target:
        extra = max(0, target - h)
        h1 += min(extra * 0.5, 34)
        h2 += min(extra * 0.3, 20)
        h = target
        inner = 10 + h1 + 8 + h2 + 10
        top_extra = max(0, (h - inner) / 2)
    rr(d, (x, y, x + w, y + h), 18, fill=SURF)
    ix = x + 10
    iw = w - 20
    y1 = y + 10 + top_extra
    g = 8
    kw = (iw - 2 * g) / 3
    key(d, ix, y1, kw, h1, "prev", sz=15)
    key(d, ix + kw + g, y1, kw, h1, "play", sz=15)
    key(d, ix + 2 * (kw + g), y1, kw, h1, "next", sz=15)
    y2 = y1 + h1 + 8
    kw2 = (iw - g) / 2
    rr(d, (ix, y2, ix + kw2, y2 + h2), 12, fill=SURF2)
    ic_rew(d, ix + kw2 * 0.30, y2 + h2 / 2, 9)
    txt(d, (ix + kw2 * 0.62, y2 + h2 / 2), "−10s", 14, TEXT, bold=True)
    rr(d, (ix + kw2 + g, y2, ix + iw, y2 + h2), 12, fill=SURF2)
    ic_ff(d, ix + kw2 + g + kw2 * 0.30, y2 + h2 / 2, 9)
    txt(d, (ix + kw2 + g + kw2 * 0.62, y2 + h2 / 2), "+10s", 14, TEXT, bold=True)
    return h


def media_card_B(d, x, y, w, target=None):
    h1 = 62
    h = 10 + h1 + 10
    top_extra = 0
    if target:
        h1 = min(target - 20, 96)
        h = target
        top_extra = max(0, (h - (10 + h1 + 10)) / 2)
    rr(d, (x, y, x + w, y + h), 18, fill=SURF)
    ix = x + 10
    iw = w - 20
    g = 7
    kw = (iw - 4 * g) / 5
    icons = [("prev", None), ("play", None), ("next", None), (None, "−10s"), (None, "+10s")]
    for i, (ic, lb) in enumerate(icons):
        cx = ix + i * (kw + g)
        if ic:
            key(d, cx, y + 10 + top_extra, kw, h1, ic, sz=13 if ic != "play" else 14)
        else:
            rr(d, (cx, y + 10 + top_extra, cx + kw, y + h1 + 10 + top_extra), 14, fill=KEY)
            txt(d, (cx + kw / 2, y + 10 + top_extra + h1 / 2), lb, 13, TEXT, bold=True)
    return h


def func_card(d, x, y, w, h, labels=True):
    rr(d, (x, y, x + w, y + h), 18, fill=SURF)
    ix = x + 10
    iw = w - 20
    row1 = [("volup", "音量+"), ("voldn", "音量−"), ("mute", "静音"), ("sundn", "亮度−"), ("sunup", "亮度+")]
    row2 = [("back", "返回"), ("home", "主页"), ("recents", "多任务"), ("shot", "截屏"), ("menu", "菜单")]
    rh = (h - 20) / 2
    for ri, row in enumerate((row1, row2)):
        cy = y + 10 + rh * ri + rh / 2
        for ci, (ic, lb) in enumerate(row):
            cx = ix + iw / 5 * (ci + 0.5)
            r = min(rh * 0.40, iw / 5 * 0.44)
            circle_key(d, cx, cy, r, ic, lb if labels else None)
    return h


def render_portrait(scheme, path):
    W, H = 393, 873
    im = Image.new("RGB", (P(W), P(H)), BG)
    d = ImageDraw.Draw(im)
    chrome(d, W, H, False)
    contentH = 430
    dockTop = H - 20 - contentH
    blank_and_handle(d, W, H, dockTop, 0)
    x = 10
    w = W - 20
    y = dockTop
    toolpad(d, x, y, w)
    y += 44 + 12
    if scheme == "A":
        mh = 10 + 62 + 8 + 44 + 10
    else:
        mh = 10 + 62 + 10
    if scheme == "A":
        media_card_A(d, x, y, w)
    else:
        media_card_B(d, x, y, w)
    y += mh + 12
    fh = H - 20 - y
    func_card(d, x, y, w, fh)
    if scheme == "A":
        c1 = "方案 A（推荐）· 媒体键分两排：上一段 / 播放暂停 / 下一段"
        c2 = "第二排 −10s / +10s 长条长按连发；下面 音量 / 亮度 / 返回 主页 多任务 截屏 菜单"
    else:
        c1 = "方案 B（紧凑）· 媒体键压成一行 5 个：上一段 / 播放 / 下一段 / −10s / +10s"
        c2 = "整页更矮留白更多；下面 音量 / 亮度 / 返回 主页 多任务 截屏 菜单"
    txt(d, (W / 2, H - 16), c1, 9, DIM)
    txt(d, (W / 2, H - 6), c2, 9, DIM)
    im.save(path)
    return path


def render_landscape(scheme, path):
    W, H = 873, 393
    im = Image.new("RGB", (P(W), P(H)), BG)
    d = ImageDraw.Draw(im)
    chrome(d, W, H, True)
    contentTop = 64
    contentBottom = H - 14
    x = 12
    w = W - 24
    y = contentTop
    ch = contentBottom - y
    if scheme == "A":
        leftW = w * 0.60
        media_card_A(d, x, y, leftW, ch)
        func_card(d, x + leftW + 12, y, w - leftW - 12, ch)
        cap = "横屏 · 方案 A：左边媒体键两排，右边 2 行 5 列（音量 / 亮度 / 返回 主页 多任务 截屏 菜单）"
    else:
        leftW = w * 0.58
        media_card_B(d, x, y, leftW, ch)
        func_card(d, x + leftW + 12, y, w - leftW - 12, ch)
        cap = "横屏 · 方案 B：媒体键一行 5 个，右边同样 2 行 5 列"
    txt(d, (W / 2, H - 10), cap, 9, DIM)
    im.save(path)
    return path


if __name__ == "__main__":
    print(render_portrait("A", "mock-media-A-p.png"))
    print(render_portrait("B", "mock-media-B-p.png"))
    print(render_landscape("A", "mock-media-A-l.png"))