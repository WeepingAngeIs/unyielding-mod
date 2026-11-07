# Mawonna v17, Corvislight, Mawile Unyielding and Fairy Sliggoo

Download [Mawonna.zip](https://github.com/WeepingAngeIs/unyielding-mod/raw/refs/heads/main/Mawonna.zip). This single complete pack is used in the server datapacks folder and client resourcepacks folder. Update both copies and restart the server. Servers that supply this ZIP as their resource pack should keep the same URL. Avoid enabling older duplicate packs.

## Commands

- `/pokespawn mawonna`: player Mawonna, approved v17 Floral Kimono model.
- `/pokespawn mawonna_flower`: powerful Flower/admin Mawonna, same v17 model.
- `/pokespawn Covislight`: Corvislight.
- `/pokespawn mawileunyielding`: Mawile Unyielding.
- `/pokespawn fairy_sliggoo`: Fairy Sliggoo.

Fairy Sliggoo is Fairy/Dragon with HP 200, Attack 10, Defense 120, Special Attack 120, Special Defense 110, Speed 15 (BST 575). It is a final evolution of Sliggoo after that individual defeats 1,000 Dark-type Pokemon. Its native abilities and move list are retained with Fairy Wind added at level 1. The normal Goodra evolution remains available. Its four Cupid wings support flight, with all 15 original Sliggoo animations and two new flight animations. No player riding is added.

Mawile and female Corvisquire also require 1,000 Dark defeats for their custom evolutions. Requirements appear in the Pokedex. The Flower variant is never obtained through normal evolution.

## Cloud builds and testing

All pack builds and heavy tests run on GitHub-hosted runners. The workflow preserves the complete shipped pack, including Mawile Unyielding, approved Mawonna v17 geometry, Corvislight's fixed poses and riding. It checks Sliggoo stats, types, evolution, all 17 animations, samples its 3D preview, boots a real Cobblemon server to test all five spawn commands, and loads/reloads the models in a real Minecraft client. Runtime checks use the required mods, not the full client modpack.

Only after every job passes does the workflow replace root `Mawonna.zip`. Reports and an installable pack are also available in Actions. Do not use older Actions artifacts as the current complete pack.
