"""Generate transparent launcher icons from loghi/icona_app.png."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(__file__).resolve().parent / "icona_app.png"


def near_black(r: int, g: int, b: int, a: int, thr: int = 28) -> bool:
    return a > 0 and r <= thr and g <= thr and b <= thr


def clear_black_background(im: Image.Image) -> Image.Image:
    """Flood-fill near-black from corners → transparent (keeps dark-blue sky)."""
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    stack = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
    seen: set[tuple[int, int]] = set()
    cleared = 0
    while stack:
        x, y = stack.pop()
        if (x, y) in seen:
            continue
        if x < 0 or y < 0 or x >= w or y >= h:
            continue
        seen.add((x, y))
        r, g, b, a = px[x, y]
        if not near_black(r, g, b, a):
            continue
        px[x, y] = (0, 0, 0, 0)
        cleared += 1
        stack.extend([(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)])
    print(f"cleared {cleared} black background pixels")
    return im


def main() -> None:
    im = clear_black_background(Image.open(SRC))
    master = Path(__file__).resolve().parent / "icona_app_transparent.png"
    im.save(master)
    print("saved", master)

    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    base = ROOT / "composeApp" / "src" / "androidMain" / "res"
    for folder, size in sizes.items():
        dest = base / folder / "ic_launcher.png"
        im.resize((size, size), Image.Resampling.LANCZOS).save(dest, optimize=True)
        print("wrote", dest, size)

    ios = ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset" / "AppIcon.png"
    im.resize((1024, 1024), Image.Resampling.LANCZOS).save(ios, optimize=True)
    print("wrote", ios)


if __name__ == "__main__":
    main()
