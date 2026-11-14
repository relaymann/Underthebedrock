# Architecture

- Core plugin: commands, world lifecycle, persistence, horror progression, encounter scheduling, and AI.
- Void world: demand-driven chunk generation. Every requested chunk receives one bedrock layer at the configured Y coordinate and no natural terrain or structures.
- Nova: first-class API integration for custom world objects and interactive props.
- BetterModel: first-class public Bukkit API integration for Blockbench entity models.

## Occurrence director foundation

The director evaluates players independently every 12 seconds while they are in the horror world. It uses six severity levels: NORMAL, ODD, STRANGE, WEIRD, SCARY, and TERRIFYING. Weighted rarity, a minimum gap, a separate severe-event cooldown, and tension/provocation thresholds control pacing. Severe occurrences are deliberately rare; terrifying occurrences require substantial accumulated tension and player provocation.

The initial event set is audio- and perception-led. It does not spawn a hostile mob every time its timer fires. Chat provocation is applied on the server thread. The plugin persists tension, provocation, encounter counts, exploration distance, timestamps, and entity-archetype encounter counts in player-horror-memory.yml.

## Entity behavior profiles

- STALKER: watches at distance, relocates when observed, avoids direct attacks.
- AGGRESSOR: pursues after provocation, can retreat, and remembers escapes.
- OBSERVER: peripheral sightings and repositioning outside the player's view.
- MIMIC: imitates familiar cues and adapts to player language.
- WANDERER: neutral roaming and evidence placement, with contextual hostility.
- DEEP_ENTITY: rare, chapter-linked presence that can influence other entities.

These are behavior contracts, not claims that fully autonomous entity controllers are finished. BetterModel remains the rendering layer for their future model-backed implementations.

## World generation

The generator does not pre-generate a radius or run bulk block edits. Minecraft requests chunks as players explore. Generated chunks are saved by normal world storage and persist across restarts. No plugin-defined world border is configured; Minecraft's native coordinate limits still apply.

## Runtime integrations

Nova 0.23.0 and BetterModel 3.5.1 APIs are compile-only. Install both plugins separately on the Paper server. Their APIs are not shaded or redistributed.

## Persistence

World chunks use normal world storage. Active state is stored in config.yml. Player return locations are stored by UUID in player-locations.yml. Horror memory is stored by UUID in player-horror-memory.yml.

## Next implementation phase

1. Nova-authored structure and evidence catalog.
2. Model-backed entity controllers and per-entity memory.
3. ARG clue graph and chapter progression.
4. Multiplayer-specific perception and coordinated encounters.
5. Performance budgets, metrics, and admin diagnostics.
