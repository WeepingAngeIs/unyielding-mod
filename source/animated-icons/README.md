# Supplied icon artwork

These are exact copies of the user-supplied artwork, retained for reproducible asset preparation.

| Source | Use | Timing / crop |
| --- | --- | --- |
| `mawonna.gif` | Existing animated Mawonna and Mawonna Flower portraits, preserved unchanged | 30 frames at 80 ms; portrait crop `(260,190,810,635)` |
| `mawonna-map.gif` | New Mawonna and Mawonna Flower full-body map animation | All 60 frames at 200 ms; 12-second loop |
| `inkyeon-map-right.gif` | Animated right-facing Inkyeon map icon | 20 frames at 180 ms; full-body union bounds |
| `inkyeon-portrait-right.png` | Static right-facing Inkyeon team portrait | Crop `(585,375,1110,770)` |
| `frilligoo-map.png` | Frilligoo static map artwork | Full figure; transparent canvas trimmed |

Mawonna's map and portrait use independent animation clocks: replacing the map never changes its approved portrait files, crop, 80 ms frame timing or 2.4-second loop. The new map retains all 60 source frames without frame dropping. Inkyeon's team portrait is deliberately static, per the user's final request. Its animated map remains unchanged. The portrait reference screenshot only guided framing; no screenshot text or background was copied.

Run `python tools/export_animated_icons.py` with Pillow installed to recreate the assets. The script verifies SHA-256 hashes before processing. `--only-inkyeon-portrait` updates just the static crop and manifest. Crops use source pixel coordinates `(left,top,right,bottom)`, preserve aspect ratio and alpha, and are centered inside a transparent 256×256 portrait. Map frames occupy 64×64 cells in a 512×512 RGBA atlas, with two pixels of padding and the original GIF frame durations. GIF frames are composited by Pillow's GIF decoder. Artwork is not generated or repainted.

The four friends and Frilligoo use a separate `friends_map.png` atlas. Run `python tools/export_friend_map_icons.py` to recreate it from the exact static PNGs in `Mawonna.zip` plus `frilligoo-map.png`; their source hashes and framing are recorded in `friend_map_icons.json`. Transparent canvas is trimmed to visible artwork (alpha above 1, plus two source pixels of edge padding), then fitted into 60×60 pixels per cell. The map-only mixin selects these icons directly, so a higher-priority E19 `pokemon.json` cannot hide them. All unrelated Pokemon continue through Xaero's own icon selection, and the native team portraits are unchanged.

The server's custom map icon scale is synchronized through `ClientData.customMapIconScale` and applied to all nine custom species, including Corvislight's original map artwork. It scales a copy of the final quad matrix around its existing center, retaining Xaero's UV sampling, fade, placement and shared pose matrix. It does not resize portraits or other Pokemon icons. Missing optional quest integration defaults to 1×; finite values are clamped to 0.25–4× and invalid values fall back to 1×.
