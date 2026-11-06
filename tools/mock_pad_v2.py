
import sys, os
sys.path.insert(0, r"C:\Users\22453\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python-packages")
from PIL import Image, ImageDraw, ImageFont

# === Color palette (matching original Ui.java exactly) ===
BG       = (0x0F, 0x11, 0x15)
SURFACE  = (0x17, 0x1B, 0x22)
SURFACE_2= (0x1F, 0x25, 0x30)
KEY      = (0x26, 0x2D, 0x3A)
KEY_PRESSED = (0x3B, 0x49, 0x68)
ACCENT   = (0x4C, 0x8D, 0xFF)
TEXT     = (0xE9, 0xEC, 0xF2)
TEXT_DIM = (0x98, 0xA1, 0xB3)
OK       = (0x3D, 0xDC, 0x84)
WARN     = (0xFF, 0xB0, 0x20)

FONT_PATH = "C:/Windows/Fonts/msyh.ttc"
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"

def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_PATH, size)

def blend(a, b, t):
    return tuple(int(a[i]*(1-t) + b[i]*t) for i in range(3))

def rounded_rect(draw, xy, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = [int(v) for v in xy]
    r = min(int(radius), (x1-x0)//2, (y1-y0)//2)
    if r <= 0:
        draw.rectangle([x0,y0,x1,y1], fill=fill, outline=outline, width=width)
        return
    draw.rounded_rectangle([x0,y0,x1,y1], radius=r, fill=fill, outline=outline, width=width)

def center_text(draw, text, cx, cy, fnt, fill):
    bbox = draw.textbbox((0, 0), text, font=fnt)
    tw, th = bbox[2]-bbox[0], bbox[3]-bbox[1]
    draw.text((int(cx - tw//2), int(cy - th//2 - bbox[1])), text, font=fnt, fill=fill)

def draw_chevron(draw, cx, cy, half_w, lift, direction, color, width):
    # direction: -1 = up chevron, 1 = down chevron
    if direction < 0:
        pts = [(cx-half_w, cy), (cx, cy-lift), (cx+half_w, cy)]
    else:
        pts = [(cx-half_w, cy), (cx, cy+lift), (cx+half_w, cy)]
    draw.line(pts, fill=color, width=width, joint="curve")

# ============================================================
# MOCKUP 1: iPad Keyboard Page — Landscape (1024dp → 2048px @K=2)
# ============================================================
W, H = 2048, 1536
K = 2.0

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

# --- Status bar (top chrome) ---
status_h = int(40 * K)
# Power button
pwr_x, pwr_y = int(14*K), int(10*K)
pwr_s = int(30*K)
rounded_rect(d, [pwr_x, pwr_y, pwr_x+pwr_s, pwr_y+pwr_s], int(10*K), fill=SURFACE_2)
pcx, pcy = pwr_x + pwr_s//2, pwr_y + pwr_s//2
d.line([pcx, pcy-int(9*K), pcx, pcy-int(3*K)], fill=TEXT, width=max(1,int(2.2*K)))
d.arc([pcx-int(8*K), pcy-int(8*K), pcx+int(8*K), pcy+int(8*K)], start=220, end=320, fill=TEXT, width=max(1,int(2.2*K)))

# Status dot + text
dot_x = pwr_x + pwr_s + int(12*K)
dot_cy = pwr_y + pwr_s//2
d.ellipse([dot_x-int(5*K), dot_cy-int(5*K), dot_x+int(5*K), dot_cy+int(5*K)], fill=WARN)
f_status = font(int(14*K), bold=True)
d.text((dot_x + int(12*K), pwr_y + int(1*K)), "等待平板连接", font=f_status, fill=TEXT)
f_detail = font(int(10*K))
d.text((dot_x + int(12*K), pwr_y + int(20*K)), "已注册 HID，请在 iPad 蓝牙设置中配对", font=f_detail, fill=TEXT_DIM)

# Tab pills
tab_names = ["键盘", "触控板", "媒体"]
tab_x = int(320*K)
tab_y = pwr_y + int(3*K)
tab_h = int(32*K)
tab_gap = int(8*K)
active_tab = 0
f_tab = font(int(13*K))
for i, name in enumerate(tab_names):
    bbox = d.textbbox((0,0), name, font=f_tab)
    tw = bbox[2] - bbox[0]
    pad_x = int(16*K)
    pill_w = tw + pad_x*2
    if i == active_tab:
        rounded_rect(d, [tab_x, tab_y, tab_x+pill_w, tab_y+tab_h], int(12*K), fill=SURFACE_2)
        d.text((tab_x + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT)
    else:
        d.text((tab_x + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT_DIM)
    tab_x += pill_w + tab_gap

# --- Keyboard area ---
kb_pad = int(6*K)
kb_gap = int(5*K)
kb_radius = int(8*K)

# Toolbar
toolbar_h = int(44*K)
toolbar_r = int(10*K)
tb_y = status_h + kb_pad
tb_items = [("切换布局", 1.05, False), ("触控板", 1.0, True)]
tb_total_w = W - kb_pad*2
tb_weights = sum(w for _, w, _ in tb_items)
tb_unit = (tb_total_w - kb_gap*(len(tb_items)-1)) / tb_weights
tb_x = float(kb_pad)
for label, weight, is_action in tb_items:
    kw = int(tb_unit * weight)
    rounded_rect(d, [tb_x, tb_y, tb_x+kw, tb_y+toolbar_h], toolbar_r, fill=SURFACE_2)
    if is_action:
        rounded_rect(d, [tb_x, tb_y, tb_x+kw, tb_y+toolbar_h], toolbar_r, outline=ACCENT, width=max(1,int(1.5*K)))
    if not is_action:
        center_text(d, label, tb_x+kw//2, tb_y+toolbar_h//2 - int(8*K), font(int(14*K)), TEXT)
        center_text(d, "当前：iPad布局", tb_x+kw//2, tb_y+toolbar_h//2 + int(8*K), font(int(9*K)), TEXT_DIM)
    else:
        center_text(d, label, tb_x+kw//2, tb_y+toolbar_h//2, font(int(14*K)), ACCENT)
    tb_x += kw + kb_gap

# Chip row 1 (Cmd+C/V/X/A/Z)
chip_h = int(36*K)
chip_y = tb_y + toolbar_h + kb_gap*2
chips1 = ["⌘C", "⌘V", "⌘X", "⌘A", "⌘Z"]
chip_n = len(chips1)
chip_usable = W - kb_pad*2 - kb_gap*(chip_n-1)
chip_w = chip_usable // chip_n
f_chip = font(int(11*K))
for i, label in enumerate(chips1):
    cx = kb_pad + i*(chip_w + kb_gap)
    rounded_rect(d, [cx, chip_y, cx+chip_w, chip_y+chip_h], kb_radius, fill=SURFACE_2)
    center_text(d, label, cx+chip_w//2, chip_y+chip_h//2, f_chip, TEXT_DIM)

# Chip row 2 (Cmd+Tab/H/Space/Shift+3)
chip2_y = chip_y + chip_h + kb_gap
chips2 = [("⌘Tab", 1.25), ("⌘H", 1.0), ("⌘空格", 1.35), ("⌘⇧3", 1.5)]
chip2_total_w = sum(w for _, w in chips2)
chip2_usable = W - kb_pad*2 - kb_gap*(len(chips2)-1)
chip2_unit = chip2_usable / chip2_total_w
cx2 = float(kb_pad)
for label, w in chips2:
    cw = int(chip2_unit * w)
    rounded_rect(d, [cx2, chip2_y, cx2+cw, chip2_y+chip_h], kb_radius, fill=SURFACE_2)
    center_text(d, label, cx2+cw//2, chip2_y+chip_h//2, f_chip, TEXT_DIM)
    cx2 += cw + kb_gap

# Key rows
key_top = chip2_y + chip_h + kb_gap*2
key_bottom = H - int(30*K)
key_area_h = key_bottom - key_top

rows_data = [
    (["1","2","3","4","5","6","7","8","9","0","-"], [1.0]*11, 0.9),
    (["q","w","e","r","t","y","u","i","o","p","⌫"], [1.0]*10+[1.2], 1.0),
    (["a","s","d","f","g","h","j","k","l",";","'","↵"], [1.0]*11+[1.6], 1.0),
    (["⇧","z","x","c","v","b","n","m",",",".","/"], [1.5]+[1.0]*10, 1.0),
    (["Esc","Tab","⌘","Space","←","↑","↓","→"], [1.0,1.0,1.0,4.0,1.1,1.0,1.0,1.1], 1.0),
]

row_weights_list = [rw for _, _, rw in rows_data]
total_rw = sum(row_weights_list)
row_gap_total = kb_gap * (len(rows_data)-1)
unit_h = (key_area_h - row_gap_total) / total_rw

f_key = font(int(16*K))
f_key_sm = font(int(12*K))
f_key_mod = font(int(13*K), bold=True)
f_key_arrow = font(int(18*K))

ry = float(key_top)
for ri, (labels, weights, rw) in enumerate(rows_data):
    rh = int(unit_h * rw)
    n = len(labels)
    usable = W - kb_pad*2 - kb_gap*(n-1)
    total_w = sum(weights)
    u = usable / total_w
    kx = float(kb_pad)
    for ki, (label, w) in enumerate(zip(labels, weights)):
        kw = int(u * w)
        is_mod = label in ("⇧", "⌘", "Esc", "Tab")
        is_special = label in ("⌫", "↵", "Space")
        is_arrow = label in ("←", "↑", "↓", "→")
        
        if is_mod or is_special:
            fill_c = SURFACE_2
        else:
            fill_c = KEY
        
        rounded_rect(d, [kx, ry, kx+kw, ry+rh], kb_radius, fill=fill_c)
        
        if is_arrow:
            fc = f_key_arrow
            tc = TEXT
        elif len(label) > 2:
            fc = f_key_sm
            tc = TEXT_DIM if is_mod or is_special else TEXT
        elif is_mod:
            fc = f_key_mod
            tc = TEXT_DIM
        elif is_special:
            fc = f_key_sm
            tc = TEXT_DIM
        else:
            fc = f_key
            tc = TEXT
        
        if label == "Space":
            tc = TEXT_DIM
        
        center_text(d, label, kx+kw//2, ry+rh//2, fc, tc)
        kx += kw + kb_gap
    ry += rh + kb_gap

# Footer
f_footer = font(int(9*K))
center_text(d, "PhoneKB Pad v0.2.0 · 蓝牙 HID · 键盘 / 触控板 / 媒体（iPad 版）", W//2, H - int(14*K), f_footer, TEXT_DIM)

out1 = r"C:\Users\22453\Desktop\phoneKB\mock-pad-kb-landscape.png"
img.save(out1)
print(f"Saved {out1} ({os.path.getsize(out1)} bytes)")

# ============================================================
# MOCKUP 2: iPad Touchpad Page — Landscape
# ============================================================
W2, H2 = 2048, 1536
img2 = Image.new("RGB", (W2, H2), BG)
d2 = ImageDraw.Draw(img2)

# Status bar (same as above)
d2.ellipse([dot_x-int(5*K), dot_cy-int(5*K), dot_x+int(5*K), dot_cy+int(5*K)], fill=OK)
# Redraw power button
rounded_rect(d2, [pwr_x, pwr_y, pwr_x+pwr_s, pwr_y+pwr_s], int(10*K), fill=SURFACE_2)
d2.line([pcx, pcy-int(9*K), pcx, pcy-int(3*K)], fill=TEXT, width=max(1,int(2.2*K)))
d2.arc([pcx-int(8*K), pcy-int(8*K), pcx+int(8*K), pcy+int(8*K)], start=220, end=320, fill=TEXT, width=max(1,int(2.2*K)))
d2.text((dot_x + int(12*K), pwr_y + int(1*K)), "已连接 · iPad", font=f_status, fill=TEXT)
d2.text((dot_x + int(12*K), pwr_y + int(20*K)), "HID 已注册，延迟 p50≈8ms", font=f_detail, fill=TEXT_DIM)

# Tab pills (touchpad active)
tab_x2 = int(320*K)
for i, name in enumerate(tab_names):
    bbox = d2.textbbox((0,0), name, font=f_tab)
    tw = bbox[2] - bbox[0]
    pad_x = int(16*K)
    pill_w = tw + pad_x*2
    if i == 1:  # touchpad active
        rounded_rect(d2, [tab_x2, tab_y, tab_x2+pill_w, tab_y+tab_h], int(12*K), fill=SURFACE_2)
        d2.text((tab_x2 + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT)
    else:
        d2.text((tab_x2 + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT_DIM)
    tab_x2 += pill_w + tab_gap

# Touchpad layout
tp_margin = int(12*K)
tp_gap = int(10*K)
left_w = int(66*K)
scroll_w = int(58*K)
tp_top = status_h + tp_margin
tp_bottom = H2 - int(30*K) - tp_margin

# Left pills
pill_h = (tp_bottom - tp_top - tp_gap*2) // 3
pills = [("主屏", 0), ("切App", 1), ("搜索", 2)]
for i, (label, idx) in enumerate(pills):
    py = tp_top + i*(pill_h + tp_gap)
    rounded_rect(d2, [tp_margin, py, tp_margin+left_w, py+pill_h], int(14*K), fill=SURFACE_2)
    center_text(d2, label, tp_margin+left_w//2, py+pill_h//2, font(int(12*K)), TEXT)

# Main pad area
pad_left = tp_margin + left_w + tp_gap
pad_right = W2 - tp_margin - scroll_w - tp_gap
rounded_rect(d2, [pad_left, tp_top, pad_right, tp_bottom], int(16*K), fill=SURFACE)
# Center dot
pcx2 = (pad_left + pad_right)//2
pcy2 = (tp_top + tp_bottom)//2
d2.ellipse([pcx2-int(6*K), pcy2-int(32*K)-int(6*K), pcx2+int(6*K), pcy2-int(32*K)+int(6*K)], fill=TEXT_DIM)
center_text(d2, "触控区", pcx2, pcy2 - int(4*K), font(int(14*K)), TEXT)
center_text(d2, "单指移动 · 轻点=左键 · 双指滑动=滚动", pcx2, pcy2 + int(22*K), font(int(11*K)), TEXT_DIM)

# Scroll column
scr_left = W2 - tp_margin - scroll_w
rounded_rect(d2, [scr_left, tp_top, W2-tp_margin, tp_bottom], int(12*K), fill=SURFACE_2)
scr_cx = (scr_left + W2 - tp_margin)//2
scr_mid = (tp_top + tp_bottom)//2
# Up chevron
draw_chevron(d2, scr_cx, tp_top + int(30*K), int(8*K), int(8*K), -1, TEXT_DIM, max(1,int(2.5*K)))
# Down chevron
draw_chevron(d2, scr_cx, tp_bottom - int(30*K), int(8*K), int(8*K), 1, TEXT_DIM, max(1,int(2.5*K)))

# Sensitivity capsule (bottom of pad area)
sens_y = tp_bottom + int(6*K)
sens_text = "灵敏度 1.7×"
f_sens = font(int(10*K))
sbbox = d2.textbbox((0,0), sens_text, font=f_sens)
sw = sbbox[2]-sbbox[0] + int(20*K)
sh = int(24*K)
sx = pad_left + ((pad_right - pad_left) - sw)//2
rounded_rect(d2, [sx, sens_y, sx+sw, sens_y+sh], int(12*K), fill=SURFACE_2)
center_text(d2, sens_text, sx+sw//2, sens_y+sh//2, f_sens, TEXT_DIM)

# Footer
center_text(d2, "PhoneKB Pad v0.2.0 · 蓝牙 HID · 键盘 / 触控板 / 媒体（iPad 版）", W2//2, H2 - int(14*K), f_footer, TEXT_DIM)

out2 = r"C:\Users\22453\Desktop\phoneKB\mock-pad-tp-landscape.png"
img2.save(out2)
print(f"Saved {out2} ({os.path.getsize(out2)} bytes)")

# ============================================================
# MOCKUP 3: iPad Media Page — Landscape
# ============================================================
W3, H3 = 2048, 1536
img3 = Image.new("RGB", (W3, H3), BG)
d3 = ImageDraw.Draw(img3)

# Status bar
rounded_rect(d3, [pwr_x, pwr_y, pwr_x+pwr_s, pwr_y+pwr_s], int(10*K), fill=SURFACE_2)
d3.line([pcx, pcy-int(9*K), pcx, pcy-int(3*K)], fill=TEXT, width=max(1,int(2.2*K)))
d3.arc([pcx-int(8*K), pcy-int(8*K), pcx+int(8*K), pcy+int(8*K)], start=220, end=320, fill=TEXT, width=max(1,int(2.2*K)))
d3.ellipse([dot_x-int(5*K), dot_cy-int(5*K), dot_x+int(5*K), dot_cy+int(5*K)], fill=OK)
d3.text((dot_x + int(12*K), pwr_y + int(1*K)), "已连接 · iPad", font=f_status, fill=TEXT)
d3.text((dot_x + int(12*K), pwr_y + int(20*K)), "HID 已注册", font=f_detail, fill=TEXT_DIM)

# Tab pills (media active)
tab_x3 = int(320*K)
for i, name in enumerate(tab_names):
    bbox = d3.textbbox((0,0), name, font=f_tab)
    tw = bbox[2] - bbox[0]
    pad_x = int(16*K)
    pill_w = tw + pad_x*2
    if i == 2:
        rounded_rect(d3, [tab_x3, tab_y, tab_x3+pill_w, tab_y+tab_h], int(12*K), fill=SURFACE_2)
        d3.text((tab_x3 + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT)
    else:
        d3.text((tab_x3 + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT_DIM)
    tab_x3 += pill_w + tab_gap

# Media page: landscape = media 60% + function 40%
med_margin = int(12*K)
med_gap = int(10*K)
med_top = status_h + med_margin
med_bottom = H3 - int(30*K) - med_margin
content_w = W3 - med_margin*2
content_h = med_bottom - med_top

# Split: left 60% media, right 40% function
split_x = med_margin + int(content_w * 0.6)
split_gap = med_gap

# Left: media card
med_card_r = int(16*K)
rounded_rect(d3, [med_margin, med_top, split_x - split_gap//2, med_bottom], med_card_r, fill=SURFACE)

# Media controls inside left card
inner = int(14*K)
card_l = med_margin + inner
card_r = split_x - split_gap//2 - inner
card_t = med_top + inner
card_b = med_bottom - inner

# Top row: prev / play-pause / next (3 equal cells)
cell_gap = int(8*K)
cell_w = (card_r - card_l - cell_gap*2) // 3
cell_h = int(80*K)
cell_y = card_t + ((card_b - card_t) - cell_h*2 - cell_gap) // 2 - cell_h//2

media_labels = ["⏮", "⏯", "⏭"]
for i, label in enumerate(media_labels):
    cx = card_l + i*(cell_w + cell_gap)
    rounded_rect(d3, [cx, cell_y, cx+cell_w, cell_y+cell_h], int(12*K), fill=SURFACE_2)
    center_text(d3, label, cx+cell_w//2, cell_y+cell_h//2, font(int(28*K)), TEXT)

# Bottom row: -10s / +10s (2 wide keys)
skip_y = cell_y + cell_h + cell_gap
skip_h = int(64*K)
skip_w = (card_r - card_l - cell_gap) // 2
for i, label in enumerate(["⏪ −10s", "⏩ +10s"]):
    sx = card_l + i*(skip_w + cell_gap)
    rounded_rect(d3, [sx, skip_y, sx+skip_w, skip_y+skip_h], int(12*K), fill=SURFACE_2)
    center_text(d3, label, sx+skip_w//2, skip_y+skip_h//2, font(int(16*K)), TEXT)

# Right: function card (2x5 round keys with labels)
func_l = split_x + split_gap//2
func_r = W3 - med_margin
rounded_rect(d3, [func_l, med_top, func_r, med_bottom], med_card_r, fill=SURFACE)

func_inner = int(14*K)
fl = func_l + func_inner
fr = func_r - func_inner
ft = med_top + func_inner
fb = med_bottom - func_inner

# 2 rows x 5 cols of round keys
func_keys = [
    [("🔊+", "音量+"), ("🔊−", "音量−"), ("🔇", "静音"), ("📷", "截屏"), ("🔲", "多任务")],
    [("☀−", "亮度−"), ("☀+", "亮度+"), ("←", "返回"), ("🏠", "主页"), ("☰", "菜单")]
]

cols = 5
rows_f = 2
fgap = int(8*K)
fw = (fr - fl - fgap*(cols-1)) // cols
fh = (fb - ft - fgap*(rows_f-1)) // rows_f

for ri, row in enumerate(func_keys):
    for ci, (icon, label) in enumerate(row):
        fx = fl + ci*(fw + fgap)
        fy = ft + ri*(fh + fgap)
        # Circle
        cr = min(fw, fh)//2 - int(4*K)
        ccx = fx + fw//2
        ccy = fy + fh//2 - int(8*K)
        d3.ellipse([ccx-cr, ccy-cr, ccx+cr, ccy+cr], fill=SURFACE_2)
        center_text(d3, icon, ccx, ccy, font(int(16*K)), TEXT)
        # Label below
        center_text(d3, label, ccx, fy + fh - int(6*K), font(int(9*K)), TEXT_DIM)

# Footer
center_text(d3, "PhoneKB Pad v0.2.0 · 蓝牙 HID · 键盘 / 触控板 / 媒体（iPad 版）", W3//2, H3 - int(14*K), f_footer, TEXT_DIM)

out3 = r"C:\Users\22453\Desktop\phoneKB\mock-pad-media-landscape.png"
img3.save(out3)
print(f"Saved {out3} ({os.path.getsize(out3)} bytes)")

# ============================================================
# MOCKUP 4: iPad Keyboard Page — Portrait (768x1024 → 1536x2048)
# ============================================================
W4, H4 = 1536, 2048
img4 = Image.new("RGB", (W4, H4), BG)
d4 = ImageDraw.Draw(img4)

# Status bar
pwr_x4, pwr_y4 = int(14*K), int(10*K)
pwr_s4 = int(30*K)
rounded_rect(d4, [pwr_x4, pwr_y4, pwr_x4+pwr_s4, pwr_y4+pwr_s4], int(10*K), fill=SURFACE_2)
pcx4, pcy4 = pwr_x4 + pwr_s4//2, pwr_y4 + pwr_s4//2
d4.line([pcx4, pcy4-int(9*K), pcx4, pcy4-int(3*K)], fill=TEXT, width=max(1,int(2.2*K)))
d4.arc([pcx4-int(8*K), pcy4-int(8*K), pcx4+int(8*K), pcy4+int(8*K)], start=220, end=320, fill=TEXT, width=max(1,int(2.2*K)))
dot_x4 = pwr_x4 + pwr_s4 + int(12*K)
dot_cy4 = pwr_y4 + pwr_s4//2
d4.ellipse([dot_x4-int(5*K), dot_cy4-int(5*K), dot_x4+int(5*K), dot_cy4+int(5*K)], fill=WARN)
d4.text((dot_x4 + int(12*K), pwr_y4 + int(1*K)), "等待平板连接", font=f_status, fill=TEXT)
d4.text((dot_x4 + int(12*K), pwr_y4 + int(20*K)), "已注册 HID", font=f_detail, fill=TEXT_DIM)

# Tab pills
tab_x4 = int(200*K)
for i, name in enumerate(tab_names):
    bbox = d4.textbbox((0,0), name, font=f_tab)
    tw = bbox[2] - bbox[0]
    pad_x = int(16*K)
    pill_w = tw + pad_x*2
    if i == 0:
        rounded_rect(d4, [tab_x4, tab_y, tab_x4+pill_w, tab_y+tab_h], int(12*K), fill=SURFACE_2)
        d4.text((tab_x4 + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT)
    else:
        d4.text((tab_x4 + pad_x, tab_y + (tab_h - (bbox[3]-bbox[1]))//2 - bbox[1]), name, font=f_tab, fill=TEXT_DIM)
    tab_x4 += pill_w + tab_gap

# Keyboard (portrait)
kb_pad4 = int(6*K)
kb_gap4 = int(5*K)
kb_radius4 = int(8*K)

# Toolbar (portrait: narrower)
toolbar_h4 = int(44*K)
tb_y4 = status_h + kb_pad4
tb_items4 = [("切换布局", 1.05, False), ("触控板", 1.0, True)]
tb_total_w4 = W4 - kb_pad4*2
tb_weights4 = sum(w for _, w, _ in tb_items4)
tb_unit4 = (tb_total_w4 - kb_gap4*(len(tb_items4)-1)) / tb_weights4
tb_x4 = float(kb_pad4)
for label, weight, is_action in tb_items4:
    kw = int(tb_unit4 * weight)
    rounded_rect(d4, [tb_x4, tb_y4, tb_x4+kw, tb_y4+toolbar_h4], toolbar_r, fill=SURFACE_2)
    if is_action:
        rounded_rect(d4, [tb_x4, tb_y4, tb_x4+kw, tb_y4+toolbar_h4], toolbar_r, outline=ACCENT, width=max(1,int(1.5*K)))
    if not is_action:
        center_text(d4, label, tb_x4+kw//2, tb_y4+toolbar_h4//2 - int(8*K), font(int(14*K)), TEXT)
        center_text(d4, "当前：iPad布局", tb_x4+kw//2, tb_y4+toolbar_h4//2 + int(8*K), font(int(9*K)), TEXT_DIM)
    else:
        center_text(d4, label, tb_x4+kw//2, tb_y4+toolbar_h4//2, font(int(14*K)), ACCENT)
    tb_x4 += kw + kb_gap4

# Chips row 1
chip_y4 = tb_y4 + toolbar_h4 + kb_gap4*2
for i, label in enumerate(chips1):
    cx = kb_pad4 + i*(chip_w + kb_gap4)
    # Recalculate chip_w for portrait
    chip_usable4 = W4 - kb_pad4*2 - kb_gap4*(chip_n-1)
    cw4 = chip_usable4 // chip_n
    cx4 = kb_pad4 + i*(cw4 + kb_gap4)
    rounded_rect(d4, [cx4, chip_y4, cx4+cw4, chip_y4+chip_h], kb_radius4, fill=SURFACE_2)
    center_text(d4, label, cx4+cw4//2, chip_y4+chip_h//2, f_chip, TEXT_DIM)

# Chips row 2
chip2_y4 = chip_y4 + chip_h + kb_gap4
cx2_4 = float(kb_pad4)
chip2_usable4 = W4 - kb_pad4*2 - kb_gap4*(len(chips2)-1)
chip2_unit4 = chip2_usable4 / chip2_total_w
for label, w in chips2:
    cw = int(chip2_unit4 * w)
    rounded_rect(d4, [cx2_4, chip2_y4, cx2_4+cw, chip2_y4+chip_h], kb_radius4, fill=SURFACE_2)
    center_text(d4, label, cx2_4+cw//2, chip2_y4+chip_h//2, f_chip, TEXT_DIM)
    cx2_4 += cw + kb_gap4

# Key rows (portrait)
key_top4 = chip2_y4 + chip_h + kb_gap4*2
key_bottom4 = H4 - int(30*K)
key_area_h4 = key_bottom4 - key_top4

unit_h4 = (key_area_h4 - kb_gap4*(len(rows_data)-1)) / total_rw

ry4 = float(key_top4)
for ri, (labels, weights, rw) in enumerate(rows_data):
    rh = int(unit_h4 * rw)
    n = len(labels)
    usable = W4 - kb_pad4*2 - kb_gap4*(n-1)
    total_w = sum(weights)
    u = usable / total_w
    kx = float(kb_pad4)
    for ki, (label, w) in enumerate(zip(labels, weights)):
        kw = int(u * w)
        is_mod = label in ("⇧", "⌘", "Esc", "Tab")
        is_special = label in ("⌫", "↵", "Space")
        is_arrow = label in ("←", "↑", "↓", "→")
        
        if is_mod or is_special:
            fill_c = SURFACE_2
        else:
            fill_c = KEY
        
        rounded_rect(d4, [kx, ry4, kx+kw, ry4+rh], kb_radius4, fill=fill_c)
        
        if is_arrow:
            fc = f_key_arrow
            tc = TEXT
        elif len(label) > 2:
            fc = f_key_sm
            tc = TEXT_DIM if is_mod or is_special else TEXT
        elif is_mod:
            fc = f_key_mod
            tc = TEXT_DIM
        elif is_special:
            fc = f_key_sm
            tc = TEXT_DIM
        else:
            fc = f_key
            tc = TEXT
        
        if label == "Space":
            tc = TEXT_DIM
        
        center_text(d4, label, kx+kw//2, ry4+rh//2, fc, tc)
        kx += kw + kb_gap4
    ry4 += rh + kb_gap4

# Footer
center_text(d4, "PhoneKB Pad v0.2.0 · 蓝牙 HID · 键盘 / 触控板 / 媒体（iPad 版）", W4//2, H4 - int(14*K), f_footer, TEXT_DIM)

out4 = r"C:\Users\22453\Desktop\phoneKB\mock-pad-kb-portrait.png"
img4.save(out4)
print(f"Saved {out4} ({os.path.getsize(out4)} bytes)")

print("All 4 mockups generated successfully!")
