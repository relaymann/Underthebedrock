# Under The Bedrock

Standalone Paper 26.1.2 psychological / analog ARG horror plugin with a persistent void world, per-player horror pacing, and BetterModel apparitions.

## Target platform
- Minecraft Java / Paper 26.1.2
- Java 25 / Gradle 9.7.1
- Nova 0.23.0 / BetterModel 3.5.1

## Commands
- /underthebedrock start — create/activate the horror world.
- /enter — enter the horror world.
- /leave — return to the saved previous location.
- /underthebedrock status — inspect world and integration status.

## Current systems
- Six occurrence severity tiers with per-player pacing.
- Persistent tension, provocation, exploration distance, event history, and archetype encounter counts.
- Distance-gated encounter attempts: 512-block protected radius, quadratic chance growth, distance unlocks, and an eight-minute cooldown.
- Scheduled model-backed apparition controller: one active apparition per player, private visibility, no forced chunk loading, and initial Aggressor/Wanderer movement behaviors.
- Six archetypes: Observer, Stalker, Wanderer, Mimic, Aggressor, Deep Entity.
- Player-specific chapter estimation: Arrival, Anomalies, Suspicion, Investigation, Null Influence, Deep Exploration.
- Contextual Null chat reactions that evolve with chapter and provocation.
- Individualized multiplayer sightings; other players do not see a player's apparition shell.

## Model setup
The default config maps every archetype to BetterModel key `watcher` so one installed model can exercise the controller. Change `horror.entities.models` to your own keys. Missing models are logged and skipped.

This is the first integrated AI foundation, not a finished autonomous-agent simulation. Movement currently uses controlled teleports rather than pathfinding; entity identity/memory, explicit clue triggers, world-changing set pieces, and coordinated multiplayer mysteries remain follow-up work.

## Build
Use JDK 25 and Gradle 9.7.1: `gradle clean test build`. JAR output: `build/libs/`.

Install matching Paper builds of Nova 0.23.0 and BetterModel 3.5.1 separately; APIs are compile-only.
