# Architecture

- Core plugin: commands, world lifecycle, persistence, horror progression, encounter scheduling, and AI.
- Void world: demand-driven chunk generation. Every requested chunk receives one bedrock layer at the configured Y coordinate and no natural terrain or structures.
- Nova: first-class API integration for looking up and placing Nova-authored blocks and resolving custom items used by structures, evidence, and interactive props.
- BetterModel: first-class public Bukkit API integration for listing model assets and attaching Blockbench models to entity carriers.

## World generation

The generator does not pre-generate a radius or run bulk block edits. Minecraft requests chunks as players explore. Generated chunks are saved by normal world storage and persist across restarts. No plugin-defined world border is configured; Minecraft's native coordinate limits still apply.

## Runtime integrations

Nova 0.23.0 and BetterModel 3.5.1 APIs are compile-only. Install both plugins separately on the Paper server. They are declared as hard dependencies and are not shaded or redistributed. Nova custom content uses namespaced IDs; BetterModel models are stored in its model directory and referenced by model ID.

## Persistence

World chunks use normal world storage. Active state is stored in config.yml. Player return locations are stored by UUID in player-locations.yml.

## Planned systems

1. Nova-authored structure and evidence catalog.
2. Contextual occurrence scheduler with per-player and world-level cooldowns.
3. Persistent entity memory and behavior state machines.
4. ARG clue graph and chapter progression.
5. Performance budgets, metrics, and admin diagnostics.
