"""Builds `drawable-nodpi/ic_launcher_monochrome.png`, the themed-icon layer, from the launcher artwork.

Why: Android 13+ draws "themed icons" from the adaptive icon's <monochrome> layer, of which it uses only the
ALPHA, tinted with the wallpaper's colours. Without that layer the launcher keeps the colour icon among
themed ones (lint: MonochromeLauncherIcon).

How: the foreground is four saturated squares (blue and red) with white lettering. Saturation becomes
opacity — the squares opaque, the lettering and the gaps cut out, the anti-aliased edges kept smooth — and
the result is scaled into the adaptive icon's safe zone (66 of its 108 dp) on the same 432 px canvas.

Deterministic: same input, same parameters, same bytes. Run from the repository root:

    python tools/make-monochrome-icon.py
"""
from pathlib import Path

from PIL import Image

SOURCE = Path("app/src/main/res/drawable-nodpi/ic_launcher_foreground.png")
TARGET = Path("app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png")
SATURATION_FULL = 0.60  # the squares measure 0.67-0.73: from this saturation on, fully opaque
SAFE_ZONE_PX = 264  # 66 dp at the 4x density of a 432 px (108 dp) layer


def main() -> None:
    artwork = Image.open(SOURCE).convert("RGBA")
    width, height = artwork.size
    pixels = artwork.load()
    alpha = Image.new("L", (width, height), 0)
    alpha_pixels = alpha.load()
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            saturation = (max(r, g, b) - min(r, g, b)) / 255.0
            alpha_pixels[x, y] = round(a * min(1.0, saturation / SATURATION_FULL))

    content = alpha.crop(alpha.getbbox())
    scale = SAFE_ZONE_PX / max(content.size)
    content = content.resize((round(content.width * scale), round(content.height * scale)), Image.LANCZOS)

    layer = Image.new("RGBA", content.size, (0, 0, 0, 255))
    layer.putalpha(content)
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    out.paste(layer, ((width - content.width) // 2, (height - content.height) // 2), layer)
    out.save(TARGET, optimize=True)
    print(f"{TARGET} {out.size}, content {content.size} in the safe zone")


if __name__ == "__main__":
    main()
