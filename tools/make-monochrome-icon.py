"""Builds `drawable-nodpi/ic_launcher_monochrome.png`, the themed-icon layer, from the launcher artwork.

Why: Android 13+ draws "themed icons" from the adaptive icon's <monochrome> layer, of which it uses only the
ALPHA, tinted with the wallpaper's colours. Without that layer the launcher keeps the colour icon among
themed ones (lint: MonochromeLauncherIcon).

How: the foreground is four saturated squares (blue and red) with white lettering. Saturation becomes
opacity — the squares opaque, the lettering and the gaps cut out, the anti-aliased edges kept smooth — and
the result is scaled to 42 dp, centred on the same 432 px (108 dp) canvas.

Why 42 dp and not the 66 dp safe zone: a launcher shows only the central 72 dp of the layer. At 66 dp the
opaque squares filled the whole themed disc (seen on an API 34 emulator, 2026-11-22) — a heavy dark badge
among light ones. At 42 dp the logo sits on the tinted background like the other apps' glyphs, inside the
44 dp frame recommended for themed icons.

Deterministic: same input, same parameters, same bytes. Run from the repository root:

    python tools/make-monochrome-icon.py
"""
from pathlib import Path

from PIL import Image

SOURCE = Path("app/src/main/res/drawable-nodpi/ic_launcher_foreground.png")
TARGET = Path("app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png")
SATURATION_FULL = 0.60  # the squares measure 0.67-0.73: from this saturation on, fully opaque
LOGO_PX = 168  # 42 dp at the 4x density of a 432 px (108 dp) layer — see "Why 42 dp" above


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
    scale = LOGO_PX / max(content.size)
    content = content.resize((round(content.width * scale), round(content.height * scale)), Image.LANCZOS)

    layer = Image.new("RGBA", content.size, (0, 0, 0, 255))
    layer.putalpha(content)
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    out.paste(layer, ((width - content.width) // 2, (height - content.height) // 2), layer)
    out.save(TARGET, optimize=True)
    print(f"{TARGET} {out.size}, logo {content.size}")


if __name__ == "__main__":
    main()
