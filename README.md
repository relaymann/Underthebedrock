# Under The Bedrock

Standalone Paper 26.1.2 psychological / analog ARG horror plugin with a persistent void world, individualized horror pacing, evidence discovery, and BetterModel apparitions.

## Target platform
- Minecraft Java / Paper 26.1.2
- Java 25 / Gradle 9.7.1
- Nova 0.23.0 / BetterModel 3.5.1

## Commands
- /underthebedrock start — create/activate the horror world.
- /enter — enter the horror world.
- /leave — return to the saved previous location.
- /evidence — inspect your personal evidence journal.
- /underthebedrock status — inspect world and integration status.

## ARG evidence system
Six discoverable clues form an ordered chain: First Signal, False Exit, Three Marks, Name Fragment, The Recording, and The Address. Evidence sites are deterministic per 1,024-block exploration cell, only placed in already-loaded chunks, and never force-load terrain. The first clue tier begins 768 blocks from spawn. Each clue has a distance threshold and requires the previous clue. Discoveries persist per player in player-horror-memory.yml.

Clues affect chapter progression and Null's dialogue. Null can refer to the exact last clue found, while later ambient events echo earlier evidence. Each evidence pedestal changes visually after discovery. The /evidence journal shows personal progress.

## Horror systems
- Six occurrence severity tiers with per-player pacing.
- Persistent tension, provocation, distance, event history, entity counts, and clue history.
- Distance-gated entity encounters with a 512-block protected radius and staged archetype unlocks.
- Scheduled BetterModel apparitions, one active per player, private visibility, and no forced chunk loading.
- Six archetypes and chapter-aware Null chat reactions.
- Individualized multiplayer sightings.

## Model setup
The default config maps all archetypes to BetterModel key `watcher`. Change `horror.entities.models` to installed model keys. Missing models are logged and skipped.

The AI remains an initial foundation: apparition movement uses controlled teleports rather than pathfinding. Coordinated multiplayer mysteries, advanced entity memory, and deeper clue-specific set pieces remain future work.

## Build
Use JDK 25 and Gradle 9.7.1: `gradle clean test build`. JAR output: `build/libs/`. Install matching Nova 0.23.0 and BetterModel 3.5.1 server plugins.
