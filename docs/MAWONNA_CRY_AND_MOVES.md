# Mawonna: gentle cry and expanded move access

Updated for the *actual* third evolution `mawile_custom:mawile_third_evolution` (Mawonna), rather than the earlier mistakenly edited `cobblemon:mawileunyielding` addon.

## Sound

- The existing cry animation previously used `pokemon.mawile.cry`. It now uses `mawile_custom:pokemon.mawile_third_evolution.cry`.
- `assets/mawile_custom/sounds.json` registers that event to `assets/mawile_custom/sounds/pokemon/mawile_third_evolution/mawonna_cry.ogg`.
- The Ogg is the soft 1.5-second cry created previously from the original Mawile audio (-21.16 LUFS, -6.21 dBTP, no clipped samples). Animation jaw motion and cry timing are preserved.
- Normal Mawile's own cry is untouched. The separate `MawileUnyielding_Cobblemon_Addon.zip` has been restored to its earlier repository version.

## Learnset

Added **101** distinct moves at level 1: 28 Fairy, 34 Steel, 33 Poison, and 6 additional Fang moves with other types. The Poison Fang attack is included within the Poison category, for seven Fang moves total. Existing TM, tutor, egg, legacy and unrelated level-up entries remain; duplicate higher-level unlocks were removed for the new level-1 moves. Active move slots are still limited to four, so use the move-relearn menu to swap from the level-1 pool. A few moves may require mechanics from other mods or may not function as ordinary usable moves on a specific Cobblemon version; verify in-game.

Battle-only Z-Moves and Dynamax/G-Max variants and dynamic Hidden Power type placeholders are not added as ordinary learnset moves. Move IDs are from [Pokémon Showdown's move registry](https://github.com/smogon/pokemon-showdown/blob/master/data/moves.ts) cross-referenced with [Cobblemon's species learnset syntax](https://cobblemon.com/en/guides).

### Fairy (28)

`alluringvoice`, `aromaticmist`, `babydolleyes`, `charm`, `craftyshield`, `dazzlinggleam`, `decorate`, `disarmingvoice`, `drainingkiss`, `fairylock`, `fairywind`, `fleurcannon`, `floralhealing`, `flowershield`, `geomancy`, `lightofruin`, `magicaltorque`, `mistyexplosion`, `mistyterrain`, `moonblast`, `moonlight`, `naturesmadness`, `playrough`, `sparklyswirl`, `spiritbreak`, `springtidestorm`, `strangesteam`, `sweetkiss`

### Steel (34)

`anchorshot`, `autotomize`, `behemothbash`, `behemothblade`, `bulletpunch`, `doomdesire`, `doubleironbash`, `flashcannon`, `geargrind`, `gearup`, `gigatonhammer`, `gyroball`, `hardpress`, `heavyslam`, `irondefense`, `ironhead`, `irontail`, `kingsshield`, `magnetbomb`, `makeitrain`, `metalburst`, `metalclaw`, `metalsound`, `meteormash`, `mirrorshot`, `shelter`, `shiftgear`, `smartstrike`, `spinout`, `steelbeam`, `steelroller`, `steelwing`, `sunsteelstrike`, `tachyoncutter`

### Poison (33)

`acid`, `acidarmor`, `acidspray`, `banefulbunker`, `barbbarrage`, `belch`, `clearsmog`, `coil`, `corrosivegas`, `crosspoison`, `direclaw`, `gastroacid`, `gunkshot`, `malignantchain`, `mortalspin`, `noxioustorque`, `poisonfang`, `poisongas`, `poisonjab`, `poisonpowder`, `poisonsting`, `poisontail`, `purify`, `shellsidearm`, `sludge`, `sludgebomb`, `sludgewave`, `smog`, `toxic`, `toxicspikes`, `toxicthread`, `venomdrench`, `venoshock`

### Other Fang moves (6)

`firefang`, `hyperfang`, `icefang`, `psychicfangs`, `superfang`, `thunderfang`

## Installation and testing

The repository now provides `Mawonna.zip` ready to download and install, with no renaming needed. Replace the old archive in **both** your world/server `datapacks/` and each client's enabled `resourcepacks/` (or client-server resource pack). Do not keep the older ZIP enabled alongside it. Restart the game or use F3+T to refresh resources; reload the server datapacks.

Test the isolated audio:

```mcfunction
/playsound mawile_custom:pokemon.mawile_third_evolution.cry neutral @s ~ ~ ~ 1 1
```

Test the species and open its move-relearn UI:

```mcfunction
/pokespawn mawile_custom:mawile_third_evolution
```

All ZIP entry CRCs and changed JSON were validated programmatically. The modified animation contains the new custom sound reference. Actual in-game playback and move-relearn UI were **not** tested in this environment.


## All named moves from level 1 (October 31, 2026)

The expanded learnset now has **841 named ordinary moves unlocked at level 1** (738 new unlocks). See the [complete move list and installation notes](MAWONNA_ALL_LEVEL_ONE_MOVES.md). The original Mawonna cry and other data are preserved.
