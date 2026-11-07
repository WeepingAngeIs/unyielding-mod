# Custom evolutions and commands

- `/pokespawn Covislight`: Corvislight, female Fairy/Flying bird. The exact short command spelling requested is Covislight; its display name remains Corvislight.

This is Cobblemon's existing operator-controlled spawn command. No new player permission is granted. Server permission plugins can still change access to commands.

Female Corvisquire must personally defeat 1,000 Dark-type Pokemon in battle. Counts belong to the individual Pokemon, not the player's entire team. The standard passive/level-up evolution check makes the evolution available once the requirement passes; use the evolution option. If it has not refreshed after the final win, level up once. Existing Corviknight evolution remains available.

Species additions append the evolution paths without replacing the base species. Target properties use type=dark. Pokédex descriptions document the requirements; the custom dex section is included in the National Dex. Future custom species should be registered in this section and given a requirement description.

Requires the installed Cobblemon 1.8.4-compatible data schema and Mega Showdown for the custom ability loader. Cloud checks validate files and execute the ability handler; they are not an in-game evolution test.

Back up existing worlds before updating species registrations.
