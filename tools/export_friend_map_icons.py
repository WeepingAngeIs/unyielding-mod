"""Repack the four supplied static map PNGs into Xaero-compatible 64-pixel cells.

Uses the exact PNG artwork already shipped in Mawonna.zip. No image generation.
The final-quad mixin selects these cells directly, so other resource packs can
replace their own Pokemon definition file without hiding these custom icons.
"""
import hashlib
import json
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SPECIES = ("lunavee", "buzzeon", "creepyeon", "ghost_jirachi")
OUTPUT = ROOT / "companion/src/main/resources/assets/mawonna"


def main():
    atlas = Image.new("RGBA", (512, 512))
    manifest = {"cellSize": 64, "atlasSize": 512, "icons": {}}
    with ZipFile(ROOT / "Mawonna.zip") as pack:
        for cell, species in enumerate(SPECIES):
            source = f"assets/xaerominimap/entity/icon/sprite/mawonna/{species}.png"
            data = pack.read(source)
            image = Image.open(BytesIO(data)).convert("RGBA")
            if image.getchannel("A").getextrema() != (0, 255):
                raise ValueError(f"Expected transparent supplied icon: {source}")
            # The Jirachi source has a large empty canvas plus alpha=1 export
            # noise. Frame the visible character, preserving its edge pixels.
            visible = image.getchannel("A").point(lambda alpha: 255 if alpha > 1 else 0).getbbox()
            if visible is None:
                raise ValueError(f"Empty supplied icon: {source}")
            bounds = (max(0, visible[0]-2), max(0, visible[1]-2),
                      min(image.width, visible[2]+2), min(image.height, visible[3]+2))
            icon = ImageOps.contain(image.crop(bounds), (60, 60), Image.Resampling.LANCZOS)
            atlas.alpha_composite(icon, (cell * 64 + (64 - icon.width) // 2, (64 - icon.height) // 2))
            manifest["icons"][species] = {"cell": cell, "source": source,
                "sourceSha256": hashlib.sha256(data).hexdigest(), "sourceDimensions": list(image.size),
                "crop": list(bounds)}
    destination = OUTPUT / "textures/animated_icons/friends_map.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(destination)
    (OUTPUT / "friend_map_icons.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(destination), "species": SPECIES}))


if __name__ == "__main__":
    main()
