# Unyielding

Supporting mod and pack files for Cobblemon Unyielding.

## Installation

1. Download the [combined Unyielding mod](https://github.com/WeepingAngeIs/Server-Jars/raw/refs/heads/main/releases/Unyielding-1.1.3.jar) and [complete pack](https://github.com/WeepingAngeIs/unyielding-mod/raw/refs/heads/main/Mawonna.zip). The combined mod release is maintained in Server-Jars.
2. Stop Minecraft and the server. Back up the world before updating.
3. Replace the previous Unyielding JAR and separate UnyieldingQuests or Cobble Currency Bridge JARs in the server's and every client's `mods` folder with the combined file. Remove any separate companion module already included in that file. Keep only one copy of each module installed.
4. Replace the previous pack in the server/world `datapacks` folder and each client's enabled `resourcepacks` folder. Remove older duplicate copies from the enabled pack list. If the server distributes this pack, update that copy as well.
5. Restart the server and clients. A resource-pack reload alone cannot install JAR changes.

Version 1.1.3 requires Cobblemon 1.8.4, CobbleDollars 2.x, Lightman's Currency 2.3.0.5 or newer, and their required libraries on both server and clients. Keep those external mods installed.

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
