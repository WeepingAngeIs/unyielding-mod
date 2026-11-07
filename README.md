# Mawonna v17, Corvislight, Fairy Sliggoo and Inkyeon

Use the tested [Mawonna.zip](https://github.com/WeepingAngeIs/unyielding-mod/raw/refs/heads/main/Mawonna.zip) in both the server datapacks folder and client resourcepacks folder. This update also requires the matching [Unyielding-Mawonna.jar](https://github.com/WeepingAngeIs/unyielding-mod/raw/refs/heads/main/Unyielding-Mawonna.jar) on the server and every client. Replace the old Unyielding JAR with this combined JAR in each mods folder, then restart. Do not keep both versions installed. It extends the exact user-approved Unyielding 1.0.8 JAR and preserves all existing module contents, including its crafting restrictions. A resource-pack reload alone cannot install Java changes. Avoid enabling older duplicate Mawonna packs.

## Spawn commands and shinies

- `/pokespawn mawonna`: public Mawonna, approved v17 Floral Kimono model.
- `/pokespawn mawonna_flower`: powerful Flower/admin variant, same v17 model; never a normal evolution.
- `/pokespawn covislight`: Corvislight (the spawn identifier retains this spelling).
- `/pokespawn fairy_sliggoo`: Fairy Sliggoo.
- `/pokespawn inkyeon`: Inkyeon.

Append `shiny` to any command, for example `/pokespawn inkyeon shiny`. Spawn commands require the server's normal operator permissions. Mawile Unyielding is removed; ordinary Mawile remains.

## Evolution progress

All custom defeat counters start fresh with this update. Unknown old totals are not imported. Only verified Dark-type knockouts by the individual Pokémon count. Wild and NPC battles count; human PvP never counts. Defeats are shown in the right-hand Pokémon summary sidebar using Cobblemon's native species-feature bars.

| Evolution | Requirements |
| --- | --- |
| Mawile → public Mawonna | 666 verified Dark defeats, 49 distinct approved substantial meals, then marriage to its owner using a Mawilite Vow Ring |
| Female Corvisquire → Corvislight | 1,000 verified Dark defeats |
| Sliggoo → Fairy Sliggoo | 1,000 verified Dark defeats |
| Sylveon → Inkyeon | 1,000 verified Dark defeats and exactly 0 friendship |

Mawile also shows its distinct-meal progress in the summary sidebar. Repeat dishes and renamed foods do not add credit. Closely related ingredient variations share a dish credit. The reviewed whitelist has 85 food items representing 72 distinct dishes from Farmer's Delight, My Nether's Delight, Twilight's Delight and End's Delight. Raw ingredients, simple sides, drinks and snacks do not qualify. Servers need the corresponding food mods and their dependencies to obtain enough dishes; the full Unyielding client already includes them.

The ring is `mawonna:mawilite_vow_ring`. It has no crafting recipe or loot entry; a server operator can supply it using `/give @s mawonna:mawilite_vow_ring`, or configure a shop later. The ring is required even when both counters are complete. The marriage advancement is **There's No Laws Against the Pokemon**, with description **fall in love, marry your Pokemon**.

## Base stats

| Pokémon | HP | Atk | Def | Sp. Atk | Sp. Def | Speed | BST |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Public Mawonna | 100 | 120 | 120 | 10 | 100 | 10 | 460 |
| Mawonna Flower | 155 | 165 | 200 | 5 | 150 | 30 | 705 |
| Corvislight | 110 | 10 | 80 | 120 | 150 | 125 | 595 |
| Fairy Sliggoo | 200 | 10 | 120 | 120 | 110 | 15 | 575 |
| Inkyeon | 101 | 145 | 90 | 10 | 105 | 146 | 597 |

Public Mawonna learns regular Fairy, Steel, Dark, Ghost and fang moves, excluding Z/Max moves, 1-PP moves and internal placeholders. Flower knows the named move catalog including Z/Max and 1-PP moves, excluding internal placeholders. Futakuchi's Grasp replaces Sheer Force and lowers a damaging contact attacker's Attack by one stage, subject to normal battle protections.

Fairy Sliggoo learns regular Dragon/Fairy, tail, wing and shell moves plus Leech Seed. Inkyeon learns regular Fairy/Dark, tail, sound, liquid-themed and speed-themed moves. Its abilities are Synchronize, Inky Love (contact lowers the attacker’s Speed), and hidden Tainted Charm (guaranteed Cute Charm activation against eligible opposite-gender contact attackers, with normal infatuation immunities).

The pack includes the approved exported models, textures and animation clips. Inkyeon's browser-preview collision physics and droplet/puddle simulation are not a Minecraft physics implementation; those preview-only effects are not claimed as live game features.

## Cloud builds and testing

All builds and heavy tests run on GitHub-hosted runners. Root download artifacts are replaced only after the cloud build, real server checks and real client load/reload checks pass. Approved Mawonna v17 geometry is protected against accidental replacement. Check the workflow reports for exact coverage and runtime limitations; the smoke environment is not the entire 467-mod client.
