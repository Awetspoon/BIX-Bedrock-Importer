from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ICO_SOURCE = ROOT / "artwork" / "launcher_icon" / "BIX icon.ico"
FOREGROUND_SOURCE = ROOT / "artwork" / "launcher_icon" / "bix_launcher_foreground.png"
RES = ROOT / "app" / "src" / "main" / "res"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

BACKGROUND = (10, 18, 20, 255)
BACKGROUND_CENTER = (25, 58, 38, 255)


def centered_layer(source: Image.Image, size: int, scale: float) -> Image.Image:
    layer_size = round(size * scale)
    resized = source.resize((layer_size, layer_size), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset = (size - layer_size) // 2
    layer.alpha_composite(resized, (offset, offset))
    return layer


def radial_background(size: int) -> Image.Image:
    background = Image.new("RGBA", (size, size), BACKGROUND)
    pixels = background.load()
    center = (size - 1) / 2
    radius = max(center, 1)

    for y in range(size):
        for x in range(size):
            distance = min(((x - center) ** 2 + (y - center) ** 2) ** 0.5 / radius, 1.0)
            blend = (1.0 - distance) ** 1.7
            pixels[x, y] = tuple(
                round(BACKGROUND[channel] + (BACKGROUND_CENTER[channel] - BACKGROUND[channel]) * blend)
                for channel in range(4)
            )
    return background


def write_icons() -> None:
    icon = Image.open(ICO_SOURCE).convert("RGBA")
    foreground_source = Image.open(FOREGROUND_SOURCE).convert("RGBA")

    for density, factor in DENSITIES.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)

        legacy_size = round(48 * factor)
        square = icon.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
        square.save(directory / "ic_launcher.png", optimize=True)

        round_mask = Image.new("L", (legacy_size, legacy_size), 0)
        ImageDraw.Draw(round_mask).ellipse((0, 0, legacy_size - 1, legacy_size - 1), fill=255)
        round_icon = square.copy()
        round_icon.putalpha(round_mask)
        round_icon.save(directory / "ic_launcher_round.png", optimize=True)

        adaptive_size = round(108 * factor)
        adaptive_foreground = centered_layer(foreground_source, adaptive_size, 0.75)
        adaptive_foreground.save(directory / "ic_launcher_foreground.png", optimize=True)


if __name__ == "__main__":
    write_icons()
