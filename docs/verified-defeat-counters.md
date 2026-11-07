# Verified evolution defeat counters

Migration starts fresh. Never import the old native defeat totals, since their battle source is unknown. Preserve unrelated Pokemon data.

Native visible integer features appear in the right-hand Pokemon summary sidebar, using Gimmighoul's UI mechanism. Mawile/public Mawonna: 0-666. Corvisquire/Corvislight, Sliggoo/Fairy Sliggoo and Sylveon/Inkyeon: 0-1000. New keys default to zero.

The integrated Unyielding extension subscribes to Cobblemon's faint event and credits only the identified individual attacker. Wild and NPC opponents count. Human PvP, opposing human players in mixed battles, bench members and unidentified attackers never receive credit. Original and battle-copy progress stay synchronized, with duplicate faint events ignored.

Evolution requirements use the verified native features. The cloud server checks native feature assignment, capped counters, battle-event fixtures, evolution thresholds and save/load persistence. The client smoke test loads and reloads the final combined JAR and pack. Battle fixtures use real Cobblemon event/model objects with synthetic actors, not a replay with human clients.
