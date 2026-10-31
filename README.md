# Mawile (Unyielding): Cobblemon addon

## Mawonna: October 31, 2026 sound and move update

**The third evolution is the separate file [`Mawonna`](Mawonna), not the `MawileUnyielding_Cobblemon_Addon.zip` below.**

- [Mawonna cry and the full Fairy, Steel, Poison and Fang move list](docs/MAWONNA_CRY_AND_MOVES.md).
- Install the `Mawonna` file as `Mawonna.zip` in both the server/world datapacks and enabled client resource packs.
- Regular Mawile keeps the original cry. The earlier wrong-target change to the other addon ZIP is reverted.


This addon adds a new, separate species, `cobblemon:mawileunyielding`. She's a cute humanoid Mawile girl in Hatterene colours (#A9D3F0, #F2A7C3, #C9A6E8, #6B4C9A, white) with a giant pastel jaw-ponytail. The original Mawile files are not touched.

## Archived notes: earlier wrong-target cry edit (reverted in the smaller ZIP)

The root [`MawileUnyielding_Cobblemon_Addon.zip`](MawileUnyielding_Cobblemon_Addon.zip) now includes a distinct **1.5-second cry**, edited from Cobblemon's original Mawile cry for this female-only evolution. It has a short opening and a gentler falling answer, softened upper frequencies, and a clean fade.

- [Listen / download the WAV master](source/audio/mawileunyielding_cry.wav)
- [Game-ready Ogg preview](source/audio/mawileunyielding_cry.ogg)
- [Sound notes, installation and reproduction](docs/MAWILE_CRY.md)

Replace the old addon ZIP with the root ZIP above, including each client's enabled resource-pack copy. Audio-file and pack-structure checks passed; Minecraft playback has not been tested here.

## Version assumption
- Target: **Minecraft 1.21.1, NeoForge, Cobblemon 1.6.1+**. Cobblemon 1.7.x and 1.8.x also run on MC 1.21.1 and use the same bedrock model, poser and resolver formats, so they should work too. This has not been verified in game.
- `pack.mcmeta` uses `pack_format` 48 (the 1.21.1 data format) and `supported_formats: [34, 48]`, so the same zip is accepted as a resource pack (format 34).
- The format templates come from standard Cobblemon 1.6 conventions. The upstream Mawile files were not fetched in this build step.

## Install
**Single player**
1. Copy `dist/MawileUnyielding_Cobblemon_Addon.zip` into `<world>/datapacks/`. This loads the species data.
2. Copy the **same zip** into `.minecraft/resourcepacks/` and enable it. This loads the model, textures, animations and name. (Cobblemon also loads `assets/` from datapacks on some versions. If the model shows up without this step, you can skip it.)
3. Run `/reload` or rejoin the world.

**Server**
- Put the zip in `<world>/datapacks/` on the server. Either have each client install the same zip as a resource pack, or serve it as the server resource pack (`resource-pack=` in `server.properties`).
- Alternatively, use Cobblemon's global datapack folder (`config/cobblemon/datapacks` or `datapacks/` in the instance root, depending on your Cobblemon version).

## Test
```
/pokespawn mawileunyielding
/pokespawn mawileunyielding shiny
```

## Model
- Height is about **1.41 blocks** (22.5 px including the jaw arc). Hitbox: 0.6 × 1.4.
- Bones: `mawileunyielding > body > {leg_left, leg_right, skirt, torso > {arm_left, arm_right, head > {eyelids, hair, ponytail, jaw_base > {jaw_upper, jaw_lower}}}}`
- The texture is 128×128 with box UVs. The shiny variant is a hue-rotated (mint/peach) copy.
- Animations: `ground_idle`, `ground_walk`, `sit` (a 6 s idle quirk), `sleep` (a kneeling nap with eyes closed), `blink`, `happy`, `battle_idle`, `faint`, `cry` (jaw chomp).
- Poser poses: standing (with blink, sit and happy quirks), walking, sleeping, battle-standing. It also has faint and cry hooks.

## File layout
```
pack/                       # unzipped pack source (identical to the zip contents)
  pack.mcmeta
  data/cobblemon/species/custom/mawileunyielding.json
  assets/cobblemon/bedrock/pokemon/{models,animations,posers,resolvers}/0303_mawileunyielding/
  assets/cobblemon/textures/pokemon/0303_mawileunyielding/mawileunyielding(_shiny).png
  assets/cobblemon/lang/en_us.json
source/mawileunyielding.bbmodel   # editable Blockbench source (Bedrock entity, embedded texture and animations)
scripts/build.py                  # regenerates everything, including the zip
docs/previews/                    # turnaround next to a 1.8-block player box, plus sit and sleep poses
dist/MawileUnyielding_Cobblemon_Addon.zip
```

## Placeholders
- Stats, types (Steel/Fairy), abilities, catch rate and the move list are copied or approximated from Mawile as **placeholders**.
- There are no spawn rules, so she can only be obtained with `/pokespawn`.
- The national dex number is 303, shared with Mawile.

## Completed and checked here
- All JSON files parse.
- Every animated bone exists in the geometry.
- The zip has `pack.mcmeta`, `data/` and `assets/` at its root.
- Previews were rendered with matplotlib.

## Needs in-game testing
**None of this has been tested in Minecraft.** Things to check:
- Rotation directions (Bedrock X/Y sign) for the walk, sit and sleep animations.
- The quirk syntax (`q.bedrock_quirk`) on your Cobblemon version.
- The eyelid blink offset.
- Portrait and profile framing.
- Texture seams on fractional-size cubes.
- Whether the lang file merges cleanly with Cobblemon's own `en_us.json`.
