"""Generate legacy mipmap launcher icons from logo_steward.png."""
from __future__ import annotations

from pathlib import Path

try:
    from PIL import Image
except ImportError:
    raise SystemExit("Install Pillow: pip install pillow")

ROOT = Path(__file__).resolve().parents[1]
LOGO = ROOT / "app/src/main/res/drawable/logo_steward.png"
RES = ROOT / "app/src/main/res"
BG = (10, 28, 19, 255)  # #0A1C13 — matches logo_steward.png backdrop

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def compose(size: int) -> Image.Image:
    logo = Image.open(LOGO).convert("RGBA")
    canvas = Image.new("RGBA", (size, size), BG)
    # Smaller logo so icon + SMART-STEWARD text fit without cropping
    max_side = int(size * 0.62)
    logo.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
    x = (size - logo.width) // 2
    y = (size - logo.height) // 2
    canvas.paste(logo, (x, y), logo)
    return canvas


def main() -> None:
    if not LOGO.is_file():
        raise SystemExit(f"Missing logo: {LOGO}")
    for folder, px in SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        img = compose(px)
        img.save(out_dir / "ic_launcher.png", "PNG")
        img.save(out_dir / "ic_launcher_round.png", "PNG")
        print(f"Wrote {folder} ({px}px)")


if __name__ == "__main__":
    main()
