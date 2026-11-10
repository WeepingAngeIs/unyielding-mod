"""Prepare the user's exact artwork for native Cobblemon portraits and Xaero maps.

Requires Pillow. No artwork generation, retouching, or background replacement.
Use --only-inkyeon-portrait to update the static crop without touching either map.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageOps, ImageSequence

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "source/animated-icons"
DEFAULT_OUTPUT = ROOT / "companion/src/main/resources/assets/mawonna"
INKYEON_CROP = (585, 375, 1110, 770)
MAWONNA_CROP = (260, 190, 810, 635)
INKYEON_PORTRAIT = "textures/animated_icons/inkyeon/portrait_static.png"
SOURCES = {
    "mawonna.gif": "bf61023531274f765e3605af558ecf94bf992f52a86452fba1da1074c662e66b",
    "mawonna-map.gif": "ab56d238641f316bc720695035fa08fb14edab47de647b4e844037d92827d153",
    "inkyeon-map-right.gif": "afb5242755a169be38607a5aec9bec36d8f41128fc3d3ee0296a0fde2573a4d1",
    "inkyeon-portrait-right.png": "9101c60ad18bc9da7c01638339501c62b2a6eb13e97b345b99389ac54001a05e",
}


def verify_sources() -> None:
    for filename, expected in SOURCES.items():
        actual = hashlib.sha256((SOURCE / filename).read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError(f"Source artwork changed: {filename}: {actual}")


def contain_rgba(image: Image.Image, size: int, padding: int = 0) -> Image.Image:
    image = ImageOps.contain(image, (size - 2 * padding,) * 2, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size))
    canvas.alpha_composite(image, ((size - image.width) // 2, (size - image.height) // 2))
    return canvas


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if image.mode != "RGBA" or image.getchannel("A").getextrema() != (0, 255):
        raise ValueError(f"Expected transparent artwork: {path}")
    image.save(path)


def export_animation(key: str, filename: str, output: Path, *, portrait: bool = False, emit_map: bool = True) -> dict:
    source = Image.open(SOURCE / filename)
    frames = [frame.convert("RGBA") for frame in ImageSequence.Iterator(source)]
    source.seek(0)
    durations = [int(frame.info["duration"]) for frame in ImageSequence.Iterator(source)]
    if not frames or len(frames) > 64 or any(duration <= 0 for duration in durations):
        raise ValueError("Animation must contain 1–64 frames with positive durations")
    bounds = [frame.getbbox() for frame in frames]
    union = (min(b[0] for b in bounds), min(b[1] for b in bounds),
             max(b[2] for b in bounds), max(b[3] for b in bounds))
    atlas = Image.new("RGBA", (512, 512))
    for index, frame in enumerate(frames):
        tile = contain_rgba(frame.crop(union), 64, padding=2)
        atlas.alpha_composite(tile, ((index % 8) * 64, (index // 8) * 64))
        if portrait:
            cropped = contain_rgba(frame.crop(MAWONNA_CROP), 256)
            save(cropped, output / f"textures/animated_icons/{key}/portrait_{index:03}.png")
    if emit_map:
        save(atlas, output / f"textures/animated_icons/{key}_map.png")
    entry = {"frames": len(frames), "durationsMs": durations, "totalMs": sum(durations),
             "sourceDimensions": list(source.size), "sourceSha256": SOURCES[filename],
             "sourceFile": f"source/animated-icons/{filename}", "mapBounds": list(union)}
    if portrait:
        entry["portraitCrop"] = list(MAWONNA_CROP)
    return entry


def export_static_portrait(output: Path, entry: dict) -> None:
    image = Image.open(SOURCE / "inkyeon-portrait-right.png").convert("RGBA")
    save(contain_rgba(image.crop(INKYEON_CROP), 256), output / INKYEON_PORTRAIT)
    # Remove only the 20 obsolete generated portrait frames from the earlier test.
    # The animated map atlas and supplied source files are never removed.
    for index in range(20):
        (output / f"textures/animated_icons/inkyeon/portrait_{index:03}.png").unlink(missing_ok=True)
    entry.update({"portraitFile": INKYEON_PORTRAIT, "portraitCrop": list(INKYEON_CROP),
                  "portraitSourceDimensions": list(image.size),
                  "portraitSourceFile": "source/animated-icons/inkyeon-portrait-right.png",
                  "portraitSourceSha256": SOURCES["inkyeon-portrait-right.png"]})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--only-inkyeon-portrait", action="store_true")
    args = parser.parse_args()
    verify_sources()
    manifest_path = args.output / "animated_icons.json"
    if args.only_inkyeon_portrait:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    else:
        mawonna_portrait = export_animation("mawonna", "mawonna.gif", args.output, portrait=True, emit_map=False)
        mawonna_map = export_animation("mawonna", "mawonna-map.gif", args.output)
        mawonna_map["portraitAnimation"] = mawonna_portrait
        manifest = {"version": 1, "cellSize": 64, "columns": 8, "atlasSize": 512,
                    "animations": {
                        "mawonna": mawonna_map,
                        "inkyeon": export_animation("inkyeon", "inkyeon-map-right.gif", args.output),
                    }}
    export_static_portrait(args.output, manifest["animations"]["inkyeon"])
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "mawonnaPortrait": "30-frame animation",
                      "inkyeonPortrait": "static PNG", "mapAnimations": "60/20 frames"}))


if __name__ == "__main__":
    main()
