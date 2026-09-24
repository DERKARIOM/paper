#!/usr/bin/env python3
"""Generate the Paper launcher icons from the official logo (branding/paper_logo.png).

The logo is NOT redrawn: every output is a resampled crop of the provided artwork.

Outputs (app/src/main/...):
  res/mipmap-<density>/ic_launcher.webp             legacy square icon (whole rounded-square logo)
  res/mipmap-<density>/ic_launcher_foreground.webp  adaptive foreground (108 dp canvas)
  res/mipmap-<density>/ic_launcher_monochrome.webp  adaptive monochrome layer (themed icons, 13+)
  ic_launcher-playstore.png                         512 px store icon (full square, store masks it)
And the store listing (fastlane/metadata/android/en-US/images/):
  icon.png                                          same 512 px store icon
  featureGraphic.png                                1024x500 banner: the full logo (mark + wordmark)
The adaptive background is the solid logo blue (@color/ic_launcher_background).

Geometry: the logo's rounded square is mapped to the 72 dp visible area of the 108 dp adaptive
canvas, so the glyph keeps its original proportions/position and stays inside the 66 dp safe
zone (max radius ~30.6 dp). Outside the rounded square the foreground is filled with the logo
blue, identical to the background layer, so every launcher mask shape shows a seamless icon.

Usage: python3 scripts/branding/generate_launcher_icons.py  (requires Pillow + numpy)
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "branding" / "paper_logo.png"
RES = ROOT / "app" / "src" / "main" / "res"

BLUE = (24, 95, 165)            # logo background blue  (#185FA5)
WHITE = (255, 255, 255)         # page
LINE = (133, 183, 235)          # scan line             (#85B7EB)
DOT = (181, 212, 244)           # scan line end points  (#B5D4F4)

# density -> (legacy icon px [48 dp], adaptive layer px [108 dp])
DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}


def crop_logo_mark() -> Image.Image:
    """Exact crop of the rounded-square mark (the blue shape and its content) from the logo."""
    img = Image.open(SRC).convert("RGBA")
    a = np.asarray(img).astype(int)
    blue = (np.abs(a[:, :, :3] - np.array(BLUE)).max(axis=2) < 30) & (a[:, :, 3] > 0)
    ys, xs = np.nonzero(blue)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    mark = img.crop(box)
    if mark.width != mark.height:
        raise SystemExit(f"unexpected non-square mark {mark.size}")
    return mark


def adaptive_layer(mark: Image.Image, px: int, fill) -> Image.Image:
    """Place the mark on a 108 dp canvas so that the mark spans the 72 dp visible area."""
    canvas = Image.new("RGBA", (px, px), fill)
    size = round(px * 72 / 108)
    scaled = mark.resize((size, size), Image.LANCZOS)
    off = (px - size) // 2
    canvas.alpha_composite(scaled, (off, off))
    return canvas


def monochrome_mark(mark: Image.Image) -> Image.Image:
    """White silhouette of the glyph: page and end points opaque, scan line and blue transparent.

    Alpha is derived from the logo pixels themselves (soft edges preserved): each pixel is
    projected on the segment between its two nearest palette colours and the alpha of those
    colours is interpolated.
    """
    a = np.asarray(mark).astype(float)
    rgb = a[:, :, :3]
    palette = np.array([BLUE, WHITE, LINE, DOT], dtype=float)
    target = np.array([0.0, 1.0, 0.0, 1.0])
    d = np.linalg.norm(rgb[:, :, None, :] - palette[None, None, :, :], axis=3)
    order = np.argsort(d, axis=2)
    i0, i1 = order[:, :, 0], order[:, :, 1]
    p0, p1 = palette[i0], palette[i1]
    seg = p1 - p0
    t = np.clip(((rgb - p0) * seg).sum(axis=2) / np.maximum((seg * seg).sum(axis=2), 1e-6), 0, 1)
    alpha = target[i0] * (1 - t) + target[i1] * t
    alpha *= a[:, :, 3] / 255.0  # outside the rounded square
    out = np.zeros_like(a)
    out[:, :, :3] = 255
    out[:, :, 3] = np.round(alpha * 255)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def main() -> None:
    mark = crop_logo_mark()
    mono = monochrome_mark(mark)
    for dens, (legacy_px, layer_px) in DENSITIES.items():
        out = RES / f"mipmap-{dens}"
        out.mkdir(parents=True, exist_ok=True)
        mark.resize((legacy_px, legacy_px), Image.LANCZOS).save(
            out / "ic_launcher.webp", lossless=True)
        adaptive_layer(mark, layer_px, BLUE + (255,)).save(
            out / "ic_launcher_foreground.webp", lossless=True)
        adaptive_layer(mono, layer_px, (0, 0, 0, 0)).save(
            out / "ic_launcher_monochrome.webp", lossless=True)
    # Store icon: full square (Google Play applies its own rounded mask); corners filled with blue.
    store = Image.new("RGBA", (512, 512), BLUE + (255,))
    store.alpha_composite(mark.resize((512, 512), Image.LANCZOS))
    store.save(ROOT / "app" / "src" / "main" / "ic_launcher-playstore.png")
    listing = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"
    store.save(listing / "icon.png")
    # Feature graphic: the complete provided logo, uniformly scaled, centred on white.
    logo = Image.open(SRC).convert("RGBA")
    logo = logo.crop(logo.getbbox())
    banner = Image.new("RGBA", (1024, 500), (255, 255, 255, 255))
    scale = min(1024 * 0.8 / logo.width, 500 * 0.6 / logo.height)
    logo = logo.resize((round(logo.width * scale), round(logo.height * scale)), Image.LANCZOS)
    banner.alpha_composite(logo, ((1024 - logo.width) // 2, (500 - logo.height) // 2))
    banner.convert("RGB").save(listing / "featureGraphic.png")
    print("mark", mark.size, "-> launcher icons generated")


if __name__ == "__main__":
    main()
