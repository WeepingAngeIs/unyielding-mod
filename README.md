# Mawonna v17 Floral Kimono + Corvislight

The Mawonna model is the exact user-approved v17 Floral Kimono model in both player and Flower variants. The v16 model is no longer the build base.

## Installation

Download **Mawonna-v17-Corvislight-INSTALL.zip** from the latest successful GitHub Actions run. This ZIP is directly installable: opening it must show `pack.mcmeta`, `data/`, and `assets/` immediately. Do not install the separate test-reports download, or an older ZIP that contains another pack ZIP.

Put the same INSTALL ZIP in:
- the **server world's datapacks** folder (registers species, evolutions, stats, abilities), and
- the **client resourcepacks** folder (models, textures, animation, Pokédex text).

Remove older Mawonna v16/v17 combined packs from the active pack selection to avoid overrides. If the server supplies a world-specific resource pack, update that server-provided pack too; a higher-priority v16 server pack can override client v17 models.

After replacing the server datapack, fully restart the server for Cobblemon species registries. `/reload` alone is not the installation verification. Check `/datapack list enabled` and the server startup log.

## Commands

- `/pokespawn Covislight`: Corvislight (display name), using the requested short command spelling.
- `/pokespawn mawonna`: player Mawonna; HP 100 / Atk 120 / Def 120 / SpA 10 / SpD 100 / Speed 10; BST 460.
- `/pokespawn mawonna_flower`: powerful Flower/admin variant, original stats and two-stage ability.

These remain operator-controlled Cobblemon commands. The normal evolution can never produce the Flower variant.

Female Corvisquire and Mawile each need 1,000 Dark-type defeats tracked on the individual Pokémon. The requirements appear in their Pokédex descriptions and the custom section of the National Dex. Corvislight keeps its 595 BST spread and all 68 Fairy/Flying/wing attacks from level 1. Pupils remain still.

The custom Futakuchi-onna ability requires Mega Showdown, as in the original Mawonna pack. Player Mawonna lowers Attack and Special Attack by one stage; Flower retains the original two-stage handler.

## Cloud builds

All builds and heavy tests run on GitHub-hosted runners. The build pins the exact supplied v17 ZIP hash and verifies its 131-bone model and 26 kimono controls. Reports are a separate download so they cannot hide the installable datapack inside another ZIP.

The retained `source/pink-corvisquire/*v16*` filename refers to the **bird's animation revision**, not the Mawonna model. Mawonna is v17.

Old species IDs from earlier development packs changed; back up existing worlds before replacing those versions.

## Client loading regression check

GitHub also launches Minecraft 1.21.1 / NeoForge 21.1.249 / Cobblemon 1.8.4 under Xvfb, loads and reloads the installable pack, requires it to remain selected, and rejects model-loading errors. The original invalid object UV is tested against Cobblemon's actual Cube parser. This uses the required mods, not the entire client modpack.

Corvislight's authoring model keeps per-face UVs. The cloud exporter bakes these into the integer box-UV format accepted by Cobblemon, preserving texture detail with compensated geometry and root-bone pose scale (server species size is unchanged). Mawonna v17 files remain unchanged.
