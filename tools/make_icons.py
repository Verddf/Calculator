"""Membuat semua ikon aplikasi dari art/logo-source.png (logo terang di atas latar hitam).

Hasil:
  res/drawable-<dpi>/ic_launcher_fg.png    lapisan depan ikon adaptif (Android 8+)
  res/drawable-<dpi>/ic_launcher_mono.png  versi satu warna untuk ikon bertema (Android 13+)
  res/mipmap-<dpi>/ic_launcher.png         ikon untuk Android 6-7.1

Jalankan:  python3 tools/make_icons.py [folder_pratinjau]
"""
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
RES = os.path.join(ROOT, "res")
SOURCE = os.path.join(ROOT, "art", "logo-source.png")

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
CANVAS_DP = 108.0       # kanvas ikon adaptif
SAFE_RADIUS_DP = 32.5   # zona aman ikon adaptif berjari-jari 33dp
THRESHOLD = 20          # piksel lebih terang dari ini dianggap bagian logo
TARGET_MEDIAN = 175     # kecerahan tengah logo setelah diperkecil
MAX_GAIN = 8.0


def load_logo():
    img = Image.open(SOURCE).convert("L")
    w, h = img.size
    cx, cy = w / 2.0, h / 2.0
    # Jarak terjauh piksel logo dari pusat, agar logo pas di zona aman.
    px = img.load()
    rmax = 0.0
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if px[x, y] > THRESHOLD:
                d = math.hypot(x - cx, y - cy)
                if d > rmax:
                    rmax = d
    return img, rmax + 4


def render(img, rmax, canvas_px, radius_px):
    """Logo diperkecil dan dipusatkan pada kanvas canvas_px, lalu dicerahkan."""
    k = radius_px / rmax
    n = max(1, int(round(img.size[0] * k)))
    small = img.resize((n, n), Image.BOX)
    canvas = Image.new("L", (canvas_px, canvas_px), 0)
    off = (canvas_px - n) // 2
    canvas.paste(small, (off, off))
    # Huruf-huruf ASCII yang tipis jadi redup saat diperkecil; cerahkan agar tetap terbaca.
    values = sorted(v for v in canvas.tobytes() if v > 3)
    if values:
        median = values[len(values) // 2]
        gain = min(MAX_GAIN, TARGET_MEDIAN / float(max(median, 1)))
        canvas = canvas.point(lambda v: min(255, int(round(v * gain))))
    return canvas


def save_layers(img, rmax):
    for name, scale in DENSITIES.items():
        canvas_px = int(round(CANVAS_DP * scale))
        lum = render(img, rmax, canvas_px, SAFE_RADIUS_DP * scale)
        folder = os.path.join(RES, "drawable-" + name)
        os.makedirs(folder, exist_ok=True)
        # Lapisan depan: putih keabuan dengan transparansi dari kecerahan logo.
        fg = Image.new("RGBA", lum.size, (236, 236, 236, 0))
        fg.putalpha(lum)
        fg.save(os.path.join(folder, "ic_launcher_fg.png"), optimize=True)
        mono = Image.new("RGBA", lum.size, (255, 255, 255, 0))
        mono.putalpha(lum)
        mono.save(os.path.join(folder, "ic_launcher_mono.png"), optimize=True)


def legacy_icon(img, rmax, size):
    big = size * 4
    body_margin = big * 2 / 48.0
    body = big - 2 * body_margin
    shape = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(shape).rounded_rectangle(
        [body_margin, body_margin, big - body_margin, big - body_margin],
        radius=body * 0.24, fill=(0, 0, 0, 255))
    out = shape.resize((size, size), Image.LANCZOS)
    # Logo dirender langsung di ukuran akhir supaya kecerahannya pas.
    lum = render(img, rmax, size, size * (44 / 48.0) * 0.46)
    out.paste(Image.new("RGBA", (size, size), (236, 236, 236, 255)), (0, 0), lum)
    return out


def save_legacy(img, rmax):
    for name, scale in DENSITIES.items():
        size = int(round(48 * scale))
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        legacy_icon(img, rmax, size).save(os.path.join(folder, "ic_launcher.png"), optimize=True)


def mask_shape(kind, size):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if kind == "circle":
        d.ellipse([0, 0, size - 1, size - 1], fill=255)
    elif kind == "squircle":
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.32, fill=255)
    else:
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.12, fill=255)
    return m


def adaptive_preview(img, rmax, size, kind):
    """Meniru tampilan launcher: kanvas 108dp, yang terlihat 72dp di tengah."""
    scale = size / 72.0
    canvas_px = int(round(CANVAS_DP * scale))
    lum = render(img, rmax, canvas_px, SAFE_RADIUS_DP * scale)
    full = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 255))
    full.paste(Image.new("RGBA", full.size, (236, 236, 236, 255)), (0, 0), lum)
    inset = (canvas_px - size) // 2
    visible = full.crop((inset, inset, inset + size, inset + size))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(visible, (0, 0), mask_shape(kind, size))
    return out


def preview(img, rmax, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    sheet = Image.new("RGBA", (980, 560), (0, 0, 0, 255))
    d = ImageDraw.Draw(sheet)
    d.rectangle([0, 0, 980, 280], fill=(222, 214, 200, 255))      # wallpaper terang
    d.rectangle([0, 280, 980, 560], fill=(38, 52, 70, 255))       # wallpaper gelap
    for row, top in enumerate((24, 304)):
        x = 24
        for kind in ("circle", "squircle", "rounded"):
            icon = adaptive_preview(img, rmax, 200, kind)
            sheet.paste(icon, (x, top), icon)
            x += 216
        for s in (144, 96, 48):
            icon = adaptive_preview(img, rmax, s, "circle")
            sheet.paste(icon, (x, top + (200 - s) // 2), icon)
            x += s + 16
    lg = legacy_icon(img, rmax, 96)
    sheet.paste(lg, (860, 24), lg)
    sheet.save(os.path.join(out_dir, "icon_preview.png"))
    # Lapisan depan apa adanya (xxxhdpi), untuk cek tekstur huruf.
    fg = Image.open(os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_fg.png"))
    flat = Image.new("RGBA", fg.size, (0, 0, 0, 255))
    flat.paste(fg, (0, 0), fg)
    flat.save(os.path.join(out_dir, "icon_fg_xxxhdpi.png"))


def main():
    img, rmax = load_logo()
    save_layers(img, rmax)
    save_legacy(img, rmax)
    if len(sys.argv) > 1:
        preview(img, rmax, sys.argv[1])


if __name__ == "__main__":
    main()
