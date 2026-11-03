# -*- coding: utf-8 -*-
"""Generate the PhoneKB launcher icon from design/app-icon-source.jpg.

Design choice (v0.25.2): keep the outer metal frame and the rounded corners.
- The artwork is pasted into the adaptive FOREGROUND layer at 100% of the canvas.
  (The launcher zooms/crops the BACKGROUND layer, which used to eat the frame.)
- Background layer is transparent, so the rounded corner cut-outs of the artwork
  show the wallpaper instead of a flat plate.
- The artwork itself is masked with a rounded-rect alpha so the icon has its own
  rounded silhouette on launchers that do not mask (legacy / app info).
"""
import glob
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "design", "app-icon-source.jpg")

SIDE, CX, CY = 1070, 710, 680   # square crop around the dark tile in the source art
RADIUS_RATIO = 0.235            # corner radius of the artwork silhouette
VISIBLE_RATIO = 2.0 / 3.0       # central 72dp of the 108dp adaptive canvas is what shows

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

ADAPTIVE_XML = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@android:color/transparent" />\n'
    '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
    '</adaptive-icon>\n'
)

COLORS_XML = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<resources>\n'
    '    <color name="ic_launcher_background">#B4CBDE</color>\n'
    '</resources>\n'
)


def load_tile():
    im = Image.open(SRC).convert("RGB")
    w, h = im.size
    left = max(0, min(CX - SIDE // 2, w - SIDE))
    top = max(0, min(CY - SIDE // 2, h - SIDE))
    return im.crop((left, top, left + SIDE, top + SIDE))


def rounded_art(tile, size):
    """Tile scaled to size x size with a rounded-rect alpha channel."""
    art = tile.resize((size, size), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size * 4 - 1, size * 4 - 1), radius=int(size * 4 * RADIUS_RATIO), fill=255
    )
    art.putalpha(mask.resize((size, size), Image.LANCZOS))
    return art


def write_res(res_root, tile):
    for stale in glob.glob(os.path.join(res_root, "mipmap-*", "ic_launcher_bg.png")):
        os.remove(stale)
    for stale in glob.glob(os.path.join(res_root, "mipmap-*", "ic_launcher_round.png")):
        os.remove(stale)

    for density, size in LEGACY.items():
        outdir = os.path.join(res_root, "mipmap-" + density)
        os.makedirs(outdir, exist_ok=True)
        rounded_art(tile, size).save(os.path.join(outdir, "ic_launcher.png"), optimize=True)

    for density, canvas in FOREGROUND.items():
        outdir = os.path.join(res_root, "mipmap-" + density)
        os.makedirs(outdir, exist_ok=True)
        # Adaptive layers are drawn at 108dp but the launcher zooms them 1.5x, so only the
        # central 72dp (108 / 1.5) actually shows. Draw the artwork at that 72dp size so the
        # whole tile - metal frame included - fills the visible icon instead of being cropped.
        visible = int(round(canvas * VISIBLE_RATIO))
        art = rounded_art(tile, visible)
        layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        off = (canvas - visible) // 2
        layer.paste(art, (off, off), art)
        layer.save(os.path.join(outdir, "ic_launcher_foreground.png"), optimize=True)

    anydpi = os.path.join(res_root, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    with open(os.path.join(anydpi, "ic_launcher.xml"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(ADAPTIVE_XML)

    values = os.path.join(res_root, "values")
    os.makedirs(values, exist_ok=True)
    with open(os.path.join(values, "colors.xml"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(COLORS_XML)


def main():
    tile = load_tile()
    rounded_art(tile, 1024).save(os.path.join(ROOT, "design", "app-icon-1024.png"), optimize=True)
    write_res(os.path.join(ROOT, "app", "src", "main", "res"), tile)
    print("icons written: legacy 48-192px, adaptive foreground 108-432px, transparent background")


if __name__ == "__main__":
    main()
