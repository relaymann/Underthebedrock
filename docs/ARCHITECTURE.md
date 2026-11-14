# Architecture

## Runtime
Core plugin manages world lifecycle, player memory, occurrence pacing, clue discovery, entity control, custom item behavior, structure generation, and timed supply drops.

## Custom equipment
CustomItemManager tags each equipment item with a namespaced persistent-data identity. The 25 item definitions include exploration, detection, defense, survival, investigation, utility, late-game equipment, and an Adrenaline Injector. Right-click abilities are scoped to the horror world. Cooldown timestamps, portable 27-slot inventories, and last-death coordinates persist in YAML. A one-hit Null Ward is stored on the player persistent data container. Tripwire alarms are checked once per second. Structure and supply-drop containers receive randomized custom equipment.

## Evidence and ARG progression
Six ordered clues form a personal evidence chain. Evidence sites are deterministic from world seed and grid cells and are only built in loaded chunks.

## Procedural structures and restoration
Fourteen deterministic structure archetypes are distance-gated and weighted. StructureManager persists site coordinates/type/reset time in structures.yml, never force-loads terrain, and protects player placements. Missing blocks and loot are restored on the configured hourly cycle.

## Timed supply drops
SupplyDropManager persists its next scheduled drop and total count. Every 30 minutes by default, it chooses a safe empty position near a player in the horror world, places a loot chest, and announces exact coordinates.

## Persistence and performance
Clue scans, structure scans, supply-drop checks, and alarm checks run on scheduled timers. Player return locations, horror memory, structure state, supply-drop schedule, item cooldowns, portable storage, and death echoes persist through YAML and player data containers.
