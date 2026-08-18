"""Build deterministic Android adaptive-icon layers from the approved source artwork."""

from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageFilter


CANVAS = 432
SOURCE_CROP = (160, 152, 1344, 1336)


def smoothstep(value: np.ndarray, low: float, high: float) -> np.ndarray:
    value = np.clip((value - low) / (high - low), 0.0, 1.0)
    return value * value * (3.0 - 2.0 * value)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: build_launcher_layers.py SOURCE.png RES_DIR")

    source_path = Path(sys.argv[1])
    res_dir = Path(sys.argv[2])
    out_dir = res_dir / "drawable-nodpi"
    out_dir.mkdir(parents=True, exist_ok=True)

    source = Image.open(source_path).convert("RGB")
    crop = source.crop(SOURCE_CROP)
    pixels = np.asarray(crop, dtype=np.float32) / 255.0
    red, green, blue = pixels[..., 0], pixels[..., 1], pixels[..., 2]
    luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722

    # White/gray logo and warm yellow brush are well separated from the near-black plate.
    white_alpha = smoothstep(luminance, 0.20, 0.63)
    yellow_score = np.minimum(red - blue * 0.65, green - blue * 0.45)
    yellow_alpha = smoothstep(yellow_score, 0.10, 0.48)
    alpha = np.maximum(white_alpha, yellow_alpha)

    # Keep the original anti-aliased pixels; only derive transparency from the background.
    foreground = np.dstack((pixels, alpha))
    foreground = Image.fromarray(np.uint8(np.clip(foreground, 0, 1) * 255), "RGBA")
    bbox = foreground.getchannel("A").point(lambda value: 255 if value > 12 else 0).getbbox()
    if bbox is None:
        raise RuntimeError("foreground extraction produced an empty layer")
    foreground = foreground.crop(bbox)
    foreground_source = foreground.copy()
    # Keep the complete wordmark well inside Android's adaptive-icon safe zone.
    # The dark background must remain clearly visible around every side, like the source artwork.
    foreground.thumbnail((232, 161), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    layer.alpha_composite(foreground, ((CANVAS - foreground.width) // 2, (CANVAS - foreground.height) // 2))
    layer.save(out_dir / "ic_launcher_foreground.png", optimize=True)

    mono = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    mono.putalpha(layer.getchannel("A"))
    mono.save(out_dir / "ic_launcher_monochrome.png", optimize=True)

    # Rebuild the source plate as a full-bleed layer so launcher masks never expose its outer canvas.
    rng = np.random.default_rng(20260813)
    yy, xx = np.mgrid[0:CANVAS, 0:CANVAS]
    radius = np.sqrt(((xx - CANVAS / 2) / (CANVAS / 2)) ** 2 + ((yy - CANVAS / 2) / (CANVAS / 2)) ** 2)
    center = np.array([27.0, 30.0, 35.0])
    edge = np.array([12.0, 14.0, 17.0])
    mix = np.clip(radius * 0.72, 0, 1)[..., None]
    background = center * (1 - mix) + edge * mix
    background += rng.normal(0, 0.9, (CANVAS, CANVAS, 1))
    background_image = Image.fromarray(np.uint8(np.clip(background, 0, 255)), "RGB").filter(
        ImageFilter.GaussianBlur(0.25)
    )
    background_image.save(out_dir / "ic_launcher_background.png", optimize=True)

if __name__ == "__main__":
    main()
