# Architecture

## Runtime
Core plugin manages world lifecycle, commands, player memory, occurrence pacing, clue discovery, and entity control. Void chunks generate on demand with one bedrock floor. Nova provides custom-world-object integration; BetterModel renders model-backed apparitions.

## Evidence and ARG progression
Six ordered clues form a personal evidence chain: First Signal (768 blocks), False Exit (1,536), Three Marks (2,560), Name Fragment (4,096), The Recording (6,144), and The Address (8,192). Each clue requires the previous clue. Sites are deterministic from world seed and 1,024-block grid cells, so players can revisit known coordinates; the manager only builds a site if its chunk is already loaded. No chunk is force-loaded for site generation.

Each site is a small blackstone/crying-obsidian pedestal with a persistent-data-tagged sign. Right-click records the clue for that player, saves their history, and changes the pedestal core to amethyst. Other players may discover the same site independently. /evidence reports personal progress. Evidence is stored alongside player horror memory, preserving compatibility with existing memory files.

## Procedural structures and regeneration
StructureDefinition provides fourteen distance-gated, weighted archetypes from campsites and cabins to research facilities, containment sites, impossible corridors, sealed gateways, and the Monolith. StructureManager selects one deterministic site per eligible exploration cell, persists its coordinates/type/reset time in structures.yml, and only touches chunks already loaded by players. It never force-loads chunks and refuses initial placement over occupied footprints. Player placements inside registered footprints are persisted as protected coordinates; breaking those player blocks releases the protection. At the one-hour default interval, missing blocks are reconstructed and container inventories are refilled with a seed/cycle-derived loot roll.

## Player chapters and Null memory
Chapter calculation incorporates clue count as well as exploration distance, occurrences, and provocation. Null's response logic can recall the player's most recent clue by its specific content. Later environmental occurrences echo discovered evidence, so the mystery can refer back to earlier discoveries. No external LLM or network request is made.

## Occurrence director and entities
The director evaluates players every configured 12 seconds. Six severity levels are weighted by tension, provocation, and cooldowns. Entity encounter selection protects the first 512 horizontal blocks, ramps probability quadratically to its cap at 8,192 blocks, unlocks archetypes by distance, and persists successful encounter cooldowns.

The apparition controller allows one active model per player, uses BetterModel, checks chunk-loaded state, and never force-loads chunks. Other players have each apparition hidden. Aggressors approach after provocation; Wanderers drift. Full navigation, hearing, and perception sensors remain future work.

## Performance and persistence
Clue scans run every five seconds by default and inspect only nine nearby grid cells per player. Sites are built only in loaded chunks and only over air blocks. Entity and occurrence checks use scheduled intervals. Player return locations and horror memory use YAML persistence. Apparition shells are transient and removed on shutdown or target departure.

## Remaining roadmap
1. Add entity pathfinding, line-of-sight sensors, hearing, and durable individual identities.
2. Add more clue-specific set pieces and story-state world mutations.
3. Add coordinated multiplayer clues and conflicting evidence.
4. Add admin telemetry and configurable performance budgets.
