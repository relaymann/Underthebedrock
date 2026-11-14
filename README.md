# Under The Bedrock

A standalone Paper 26.1.2 psychological / analog ARG horror plugin built around an endless-feeling void world, persistent exploration, strange structures, unexplained occurrences, and intelligent entities.

## Target platform
- Minecraft Java / Paper 26.1.2
- Java 25
- Gradle 9.7.1
- Nova 0.23.0
- BetterModel 3.5.1

## Commands
- /underthebedrock start — create/activate the separate horror world.
- /enter — enter the horror world.
- /leave — return to the saved previous location.
- /underthebedrock status — inspect world and integration status.

## Horror progression
- Six occurrence severity tiers and a per-player Occurrence Director.
- Persistent player tension, provocation, exploration distance, and encounter history.
- Chat-based provocation that influences later encounters.
- Six entity behavior profiles: Stalker, Aggressor, Observer, Mimic, Wanderer, and Deep Entity.
- Distance-gated entity selection: a 512-block protected entrance radius, progressively increasing encounter odds, and archetypes that unlock farther from spawn.

The entity policy and its unit tests are implemented. Actual autonomous entity controllers and model-backed spawning remain the next implementation phase.

## Build
Use JDK 25 and Gradle 9.7.1: gradle clean test build. The plugin jar is written to build/libs/.

## Runtime dependencies
Install matching Paper 26.1.2 builds of Nova 0.23.0 and BetterModel 3.5.1 in the server plugins directory. Their APIs are compile-only and are not shaded into this plugin.

See docs/ARCHITECTURE.md and docs/DEVELOPMENT.md for implementation details.
