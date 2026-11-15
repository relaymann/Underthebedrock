# Architecture

## Runtime and world takeover
The horror world is the default game space. It auto-starts unless explicitly disabled, routes online and joining players into the experience, routes deaths to its spawn or a personal Reconstructor anchor, and blocks portal/world-change escapes while active. Legacy inventories and ender chests are backed up once before being cleared so old-world gear cannot trivialize the experience. Player progression, equipment, clues, and respawn anchors persist independently.

## Horror direction
OccurrenceDirector owns per-player tension, provocation, chapter pacing, and contextual Null chat. HorrorEntityController manages individualized apparition encounters. HorrorEventDirector adds a second, slower event layer with per-player cooldowns and story-stage escalation: remote movement, false local signals, archive anomalies, environmental sounds, and late-stage identity distortions.

## Civilization and progression
CivilizationManager generates deterministic abandoned districts in already-loaded chunks, maintains resident echoes and relationship discoveries, runs station service-cart events, and stages environmental changes from evidence progression. LoreManager owns faction records, identity case files, memory shards, and recognition gates. StructureManager and SupplyDropManager provide persistent exploration sites and timed resource drops. CustomItemManager owns persistent equipment, cooldowns, storage, and Reconstructor anchors.

## Persistence and performance
Player horror memory, lore discoveries, structures, supply schedules, item cooldowns, portable storage, death echoes, Reconstructor anchors, and one-time legacy inventory isolation are persisted in YAML/PDC. Scheduled scans avoid force-loading chunks. Temporary world effects restore only expected states so player edits and other plugins are not overwritten.


## Takeover, state isolation and recovery
WorldEntryListener owns automatic entry, lockdown, portal interception, respawn routing, and saved experience positions. Each player's last location is periodically stored in player-experience-positions.yml and restored when reconnecting; administrator-triggered starts may intentionally route currently-online players to the entrance. Inventory migration writes a full backup atomically before setting a prepared marker, clears the legacy inventory and state, then records completion. Existing backups are never replaced with an empty snapshot. Administrative shutdown temporarily disables lockdown, evacuates current occupants to another loaded world, and unloads the horror world without introducing a player-facing escape command.

## Facilities and survival progression
FacilityManager owns persistent generator repairs, grid state, powered terminal transmissions, claimable three-level safe rooms, and service elevators. Facility state is saved atomically in facility-state.yml. CustomItemManager provides recipes and loot for Insulated Wire, Ceramic Fuses and Resonance Power Cells, alongside the advanced Resonance Core research recipe. Safe rooms suppress apparition encounters and special disturbances while occupied.

## Behavior memory and multiplayer illusions
PlayerHorrorMemory keeps validated behavior counters separate from clue, lore and recording milestones. OccurrenceDirector records player actions with per-action cooldowns and handles Paper AsyncChatEvent before performing Bukkit operations on the main thread. NullIntelligence can summarize actions and respond to behavior-specific questions. HorrorEventDirector varies its selection against each player's event history and uses recipient-only messages, player visibility changes and client-only block changes for illusions. HorrorEntityController separates frequent movement updates from slower encounter discovery, remembers last-known player positions, bounds movement to loaded chunks, and reapplies apparition privacy on world entry.
