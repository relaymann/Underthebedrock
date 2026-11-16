# Architecture

## Runtime and world takeover
The horror world is the default game space. It auto-starts unless explicitly disabled, routes online and joining players into the experience, routes deaths to its spawn or a personal Reconstructor anchor, and blocks portal/world-change escapes while active. Legacy inventories and ender chests are backed up once before being cleared so old-world gear cannot trivialize the experience. Player progression, equipment, clues, and respawn anchors persist independently.

## Horror direction
OccurrenceDirector owns per-player tension, provocation, chapter pacing, and contextual Null chat. HorrorEntityController manages individualized apparition encounters. HorrorEventDirector adds a second, slower event layer with per-player cooldowns and story-stage escalation: remote movement, false local signals, archive anomalies, environmental sounds, and late-stage identity distortions.

## Civilization and progression
CivilizationManager generates deterministic abandoned districts in already-loaded chunks, maintains resident echoes and relationship discoveries, runs station service-cart events, and stages environmental changes from evidence progression. LoreManager owns faction records, identity case files, memory shards, and recognition gates. StructureManager and SupplyDropManager provide persistent exploration sites and timed resource drops. CustomItemManager owns persistent equipment, cooldowns, storage, and Reconstructor anchors.

## Persistence and performance
Player horror memory, lore discoveries, structures, supply schedules, item cooldowns, portable storage, death echoes, Reconstructor anchors, and one-time legacy inventory isolation are persisted in YAML/PDC. Scheduled scans avoid force-loading chunks. Temporary world effects restore only expected states so player edits and other plugins are not overwritten.

## Apparition visuals and movement
BetterModel is the only required visual integration. HorrorEntityController separates encounter selection/state decisions from per-tick bounded movement, rotates apparitions toward their targets smoothly, and keeps each apparition private to its intended player. Model assets own their articulated rigs and authored animation clips; structural validation checks rig integrity, loop seams, one-shot return poses, meaningful disappearance motion, and controller/asset duration agreement.


## The Null Enclosure
The Null Enclosure is a permanent progression landmark centered on the spawn at an approximately 1,500-block radius. Its perimeter wall is built only as naturally loaded chunks are encountered. The northern crossing contains three deterministic connected maze rooms, observation rooms, abandoned-attempt traces, a pre-chase silhouette, a sprint encounter with the first Null's Apostle, and a second Apostle that investigates movement and searches last-known positions. The final route-marking puzzle requires activating a power switch before the exit opens. Trial attempts and escape status are stored per player; death resets only the active attempt. Returning through the crossing does not restart the trial. Outer structure generation waits for a player's escape flag.
