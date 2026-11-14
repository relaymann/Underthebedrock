# Architecture

- Core plugin: commands, world lifecycle, persistence, horror progression, encounter scheduling, and AI.
- Void world: demand-driven chunk generation. Every requested chunk receives one bedrock layer at the configured Y coordinate and no natural terrain or structures.
- Nova: first-class API integration for custom world objects and interactive props.
- BetterModel: first-class public Bukkit API integration for Blockbench entity models.

## Occurrence director foundation

The director evaluates players independently every 12 seconds while they are in the horror world. It uses six severity levels: NORMAL, ODD, STRANGE, WEIRD, SCARY, and TERRIFYING. Weighted rarity, a minimum gap, a separate severe-event cooldown, and tension/provocation thresholds control pacing. The initial event set is audio- and perception-led.

## Distance-driven entity encounter policy

The entrance area is protected: entity selection has exactly zero probability within 512 blocks of the world spawn. Beyond that radius, probability increases quadratically with horizontal distance and reaches a configurable maximum of 2.5% per director check at 8,192 blocks. This makes entities exceptionally uncommon near the beginning and progressively more likely during deep exploration. A successful encounter has an eight-minute per-player cooldown, persisted with player memory.

Archetypes unlock at different distances: Observer (768), Stalker (1,024), Wanderer (1,536), Mimic (2,048), Aggressor (3,072), and Deep Entity (6,144 blocks). Selection weights favor passive and observational entities. Aggressors and Deep Entities remain much rarer.

The OccurrenceDirector exposes trySelectEntityEncounter(player) as the mandatory selection gate for upcoming entity controllers. It returns no archetype until the player is deep enough, the probability roll succeeds, and the per-player cooldown has expired. It does not itself spawn model-backed entities; controllers must call this gate before spawning. The policy has unit tests for the protected radius, increasing chance, distance unlocks, and invalid values.

## Entity behavior profiles

- STALKER: watches at distance, relocates when observed, avoids direct attacks.
- AGGRESSOR: pursues after provocation, can retreat, and remembers escapes.
- OBSERVER: peripheral sightings and repositioning outside the player's view.
- MIMIC: imitates familiar cues and adapts to player language.
- WANDERER: neutral roaming and evidence placement, with contextual hostility.
- DEEP_ENTITY: rare, chapter-linked presence that can influence other entities.

These are behavior contracts, not claims that fully autonomous entity controllers are finished. BetterModel remains the rendering layer for their future model-backed implementations.

## World generation

The generator does not pre-generate a radius or run bulk block edits. Minecraft requests chunks as players explore. Generated chunks are saved by normal world storage. No plugin-defined world border is configured; Minecraft's native coordinate limits still apply.

## Runtime integrations

Nova 0.23.0 and BetterModel 3.5.1 APIs are compile-only. Install both plugins separately on the Paper server. Their APIs are not shaded or redistributed.

## Persistence

World chunks use normal world storage. Active state is stored in config.yml. Player return locations are stored by UUID in player-locations.yml. Horror memory is stored by UUID in player-horror-memory.yml.

## Next implementation phase

1. Connect the selection gate to model-backed entity controllers.
2. Nova-authored structure and evidence catalog.
3. Per-entity memory and behavior state machines.
4. ARG clue graph and chapter progression.
5. Multiplayer-specific perception and coordinated encounters.
6. Performance budgets, metrics, and admin diagnostics.
