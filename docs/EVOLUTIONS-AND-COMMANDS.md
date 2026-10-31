# Custom evolutions and commands

- `/pokespawn Covislight`: Corvislight, female Fairy/Flying bird. The exact short command spelling requested is Covislight; its display name remains Corvislight.
- `/pokespawn mawonna`: player Mawonna, 100/120/120/10/100/10 (HP/Atk/Def/SpA/SpD/Spe), BST 460. Futakuchi-onna lowers opposing Attack and Special Attack by one stage.
- `/pokespawn mawonna_flower`: powerful administrator version, original 155/165/200/5/150/30 stats and original two-stage ability. This is a separate species, never an evolution result.

These are Cobblemon's existing operator-controlled spawn commands. No new player permission is granted. The flower suffix distinguishes the powerful version; server permission plugins can still change access to commands.

Female Corvisquire and Mawile each must personally defeat 1,000 Dark-type Pokemon in battle. Counts belong to the individual Pokemon, not the player's entire team. The standard passive/level-up evolution check makes the evolution available once the requirement passes; use the evolution option. If it has not refreshed after the final win, level up once. Existing Corviknight evolution remains available.

Species additions append the evolution paths without replacing the base species. Target properties use type=dark. Pokédex descriptions document the requirements; the custom dex section is included in the National Dex. Future custom species should be registered in this section and given a requirement description.

Requires the installed Cobblemon 1.8.4-compatible data schema and Mega Showdown for the custom ability loader already used by Mawonna. Cloud checks validate files and execute the ability handler; they are not an in-game evolution test.

Compatibility: the old mawonna:pink_corvisquire and mawile_custom:mawile_third_evolution species registrations are replaced by the short command registrations. Back up existing worlds containing those older IDs before updating; there is no saved-Pokemon migration in this datapack.
