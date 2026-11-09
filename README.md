# Unyielding

Supporting mod and pack files for Cobblemon Unyielding.

## Installation

1. Download the current combined Unyielding mod from [Server-Jars](https://github.com/WeepingAngeIs/Server-Jars). The next combined release includes the complete Mawonna assets and data inside the JAR; follow that release's installation notes.
2. Stop Minecraft and the server. Back up the world before updating.
3. Replace the previous Unyielding JAR and separate UnyieldingQuests or Cobble Currency Bridge JARs in the server's and every client's `mods` folder with the combined file. Remove any separate companion module already included in that file. Keep only one copy of each module installed.
4. Replace the previous pack in the server/world `datapacks` folder and each client's enabled `resourcepacks` folder. Remove older duplicate copies from the enabled pack list. If the server distributes this pack, update that copy as well.
5. Restart the server and clients. A resource-pack reload alone cannot install JAR changes.

The combined release requires Cobblemon 1.8.4, CobbleDollars 2.x, Lightman's Currency 2.3.0.5 or newer, and their required libraries on both server and clients. Keep those external mods installed. `Unyielding-Mawonna.jar` in this repository is an older companion test artifact; use the current Server-Jars release for installation.

## Builds and checks

Builds and runtime checks run on GitHub-hosted runners. The download files are updated only after the cloud build, server checks and client load/reload checks pass. Those checks use a focused test environment; they do not cover the entire client modpack.

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
