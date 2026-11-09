# Unyielding

Supporting mod and pack files for Cobblemon Unyielding.

## Installation

1. Download [Unyielding 1.1.7](https://github.com/WeepingAngeIs/Server-Jars/raw/refs/heads/main/releases/Unyielding-1.1.7.jar). This combined JAR includes the complete Mawonna assets, species data, ring, evolution rules and riding definitions.
2. Stop Minecraft and the server. Back up the world before updating.
3. Replace the previous Unyielding JAR and separate UnyieldingQuests or Cobble Currency Bridge JARs in the server's and every client's `mods` folder with the combined file. Remove any separate companion module already included in that file. Keep only one copy of each module installed.
4. Remove or disable the old `Mawonna.zip` and individual Lunavee, Buzzeon, Ghost Jirachi and Creepyeon addon packs from the world `datapacks` and each client's `resourcepacks`. The JAR supplies them now; old external packs can override the fixes. Update any server-distributed copy containing those old assets. Other resource packs can remain enabled.
5. Restart the server and clients. A resource-pack reload alone cannot install JAR changes.

The combined release requires Cobblemon 1.8.4, CobbleDollars 2.x, Lightman's Currency 2.3.0.5 or newer, and their required libraries on both server and clients. Keep those external mods installed. The separate `cu-rideable` JAR can remain if you use its native Pokemon riding support; it does not duplicate these nine custom entries. The ZIP remains available for separate pack workflows, but is unnecessary with the combined JAR.

## Builds and checks

This release's builds and game checks ran on GitHub-hosted runners, with a focused local compilation check to reduce repeated cloud runs. Downloads are published only after their required checks pass. The checks use a focused test environment; they do not cover the entire client modpack or sustained player-controlled flight.

## New command-only Pokemon

The combined pack includes the approved Lunavee, Buzzeon, Ghost Jirachi, and Creepyeon exports. With operator permission:

```text
/pokespawn lunavee
/pokespawn buzzeon
/pokespawn ghost_jirachi
/pokespawn creepyeon
```

Append `gender=male` or `gender=female` to select a sex. These four currently have no natural spawn or evolution rules. Their supplied exports have no separate shiny palettes; `shiny` retains the same artwork. Lunavee retains Normal typing and Eevee abilities; Creepyeon retains its supplied Synchronize/Magic Bounce and Espeon-derived learnset. Lunavee's Aura Wheel retains its normal species restriction. Original export notes and licenses are under `docs/imported-pokemon/`.

The friends' revised learnsets start with four moves and unlock later moves through leveling. See [the full schedules](docs/friends-move-progression.md). Existing Pokemon can retain already learned moves. Lunavee uses Eevee's normal scale; Buzzeon, Creepyeon and Ghost Jirachi have reduced world sizes. The zero-transition scale correction matches the earlier Corvislight fix. Buzzeon's ears and wings angle outward. Ghost Jirachi has a proper English name alias and corrected side-crystal angles, preserving the raised purple crown and original animation clips. Portraits are centered on faces. Lunavee's star scale keyframes are expanded into the vector format that Cobblemon accepts, so its animation group loads.

All nine custom species have riding seat locators. The combined release supplies their riding definitions. Rendering checks capture actual client portraits, profiles and flight poses and verify scale throughout pose changes. These are focused client checks, not a full modpack play session.
