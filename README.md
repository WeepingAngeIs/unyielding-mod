# Unyielding

Supporting mod and pack files for Cobblemon Unyielding.

## Installation

1. Download [Unyielding 1.1.8](https://github.com/WeepingAngeIs/Server-Jars/raw/refs/heads/main/releases/curseforge-1.1.8/Unyielding-1.1.8.jar). This combined JAR includes the complete Mawonna assets, species data, ring, evolution rules, riding definitions and animated portraits/map icons.
2. Stop Minecraft and the server. Back up the world before updating.
3. Replace the previous Unyielding JAR and separate UnyieldingQuests or Cobble Currency Bridge JARs in the server's and every client's `mods` folder with the combined file. Remove any separate companion module already included in that file. Keep only one copy of each module installed.
4. Remove or disable the old `Mawonna.zip` and individual Lunavee, Buzzeon, Ghost Jirachi and Creepyeon addon packs from the world `datapacks` and each client's `resourcepacks`. The JAR supplies them now; old external packs can override the fixes. Update any server-distributed copy containing those old assets. Other resource packs can remain enabled.
5. Restart the server and clients. A resource-pack reload alone cannot install JAR changes.

The combined release requires Cobblemon 1.8.4, CobbleDollars 2.x, Lightman's Currency 2.3.0.5 or newer, and their required libraries on both server and clients. Keep those external mods installed. The separate `cu-rideable` JAR can remain if you use its native Pokemon riding support; it does not duplicate these nine custom entries. The ZIP remains available for separate pack workflows, but is unnecessary with the combined JAR.

## Builds and checks

Version 1.1.8 uses the local TEST profile for inventory and asset checks and GitHub Actions for heavy builds and game checks. Downloads are published only after the required checks pass. The checks use isolated server/client environments; they do not cover the entire client modpack, sustained player-controlled flight or a live minimap radar session. See [build instructions](docs/settings-release.md) and [release checks](https://github.com/WeepingAngeIs/unyielding-mod/actions/workflows/spin-release.yml).

## Mawile's marriage ring

Use `/give @s mawonna:mawilite_vow_ring` to get the **Mawilite Vow Ring** for testing or to stock an NPC shop. Each Mawile needs 49 different foods with at least 8 Diversity and 666 personal Dark-type defeats. Hold a new qualifying food near a wild or owned Mawile to get its attention, then drop it. Hunger and recipe source do not determine credit. Spice of Life Onion supplies the food score. Use the ring on your own Mawile outside battle, then sneak and use it to exchange vows.

## Custom Pokémon

The combined pack includes the approved Lunavee, Buzzeon, Ghost Jirachi, and Creepyeon exports. With operator permission:

```text
/pokespawn lunavee
/pokespawn buzzeon
/pokespawn ghost_jirachi
/pokespawn creepyeon
```

Append `gender=male` or `gender=female` to select a sex. Eevee can also evolve into Creepyeon, Buzzeon or Lunavee through their defeat milestones. Their supplied exports have no separate shiny palettes; `shiny` retains the same artwork. Lunavee retains Normal typing and Eevee abilities; Creepyeon retains Synchronize/Magic Bounce and now has all eligible Ghost and Normal moves. Lunavee's Aura Wheel retains its normal species restriction. Original export notes and licenses are under `docs/imported-pokemon/`.

All four friends' learnsets are available from level one: Lunavee 102, Buzzeon 55, Ghost Jirachi 119, and Creepyeon 269. No level progression or teaching item is required for these lists; the normal four equipped battle-move slots and individual move restrictions still apply. See [the full lists](docs/friends-move-progression.md). Lunavee uses Eevee's normal scale; Buzzeon, Creepyeon and Ghost Jirachi have reduced world sizes. The zero-transition scale correction matches the earlier Corvislight fix. Buzzeon's ears and wings angle outward. Ghost Jirachi now uses the original Jirachi model and animations in purple; its belly eye opens strictly below half HP and closes again when healed to half or above. Lunavee's star scale keyframes use the vector format accepted by Cobblemon.

All nine custom species have riding seat locators. The combined release supplies their riding definitions. Rendering checks capture actual client portraits, profiles and flight poses and verify scale throughout pose changes. These are focused client checks, not a full modpack play session.

Frilligoo (saved species ID `fairy_sliggoo`) is Dragon/Fairy and uses the original Sliggoo eyes and mouth, with the added drawn-on smile removed. Inkyeon uses Sylveon's original four-leg walking controller, with calm rear joints while standing/in battle and no custom knee-bending motion. All latest Mawonna sleeve/hand, cry, and loop fixes from the approved ZIP are embedded.

Mawonna and command-only Mawonna Flower share the supplied animated face portrait and full-body map icon. Inkyeon uses a still, transparent face portrait from the clean supplied artwork and the right-facing GIF for her animated map icon. The new portrait images replace the team portrait only; the 3D models remain available in other views. Map animation integrates with Xaero Minimap 26.5.0; Xaero is optional. Normal and shiny forms use the same supplied portrait artwork.

Admin Mawonna Flower has 862 level-one moves after removing Z/Max/G-Max, one-PP and malformed/internal entries. Its separate admin stats and command-only obtain method remain intact: `/pokespawn mawonna_flower`. Public Mawonna is unchanged by that move cleanup.

## Live server controls

Operators use `/config` (or `/quests settings`). Choose **Config values -> Map icon size** for the 0.25-4x custom-map-icon scale. The main settings list adds home/back/warp/RTP/TPA/nickname/recording/nearby-player switches, admin inventory controls, and optional OPAC admin claim bypass (default OFF). Day/Night/Weather shortcuts appear in operator inventories. [Settings and installation](https://github.com/WeepingAngeIs/Server-Jars/blob/main/settings-update/RELEASE-NOTES.md).

Mawonna now uses the new 60-frame map GIF while preserving all 30 previous portrait frames/timing. Frilligoo uses the new supplied map artwork. Other portraits and models are unchanged.
