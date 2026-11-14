# Architecture

## Runtime
Core plugin manages commands, world lifecycle, player memory, occurrence pacing, and entity control. Void chunks are generated on demand with one bedrock floor layer. Nova provides custom-world-object integration; BetterModel renders model-backed entities.

## Occurrence director and encounter gate
The director evaluates players every configured 12 seconds. Six severity levels are weighted by tension, provocation, and cooldowns. TERRIFYING events additionally require late player progression. The entity gate protects the first 512 horizontal blocks from the world spawn, ramps probability quadratically to the configured maximum at 8,192 blocks, unlocks archetypes at staged distances, and persists an eight-minute successful-encounter cooldown.

## Model-backed apparitions
The controller checks at the director cadence, permits one apparition per player, checks configured BetterModel keys, chooses a point behind the player's view, and never force-loads a destination chunk. An invisible invulnerable ArmorStand shell receives the BetterModel renderer. Other players have that shell hidden, producing individualized sightings. Apparitions vanish when directly observed at close range or when the target leaves the active world / moves too far away. Aggressors approach only after provocation; Wanderers occasionally drift. This is intentionally a lightweight first AI pass, not full pathfinding or combat.

Configure model keys under `horror.entities.models`; defaults use `watcher`. Install the corresponding models in BetterModel before enabling encounters on a live server.

## Player memory, Null, and chapters
Player memory persists tension, provocation, distance travelled, occurrence history, and archetype encounter counts. StoryChapter is derived deterministically from that persisted history. Null chat reactions are private, chapter-aware, and probabilistic; player provocation influences later reactions. No external LLM or network call is made.

## Multiplayer
Each apparition is hidden from other players. Audio and chat occurrences are also delivered to the affected player only. Coordinated clue logic and shared entity memory are not implemented yet.

## Performance and persistence
Entity and occurrence checks run on a configurable scheduler cadence, with at most one active apparition per player. The controller checks chunk-loaded state and never force-loads chunks. World chunks use normal world storage; player locations and horror memory use YAML persistence. Entity shells are transient and removed on shutdown or target departure.

## Remaining work
1. Add pathfinding, line-of-sight sensors, hearing, and per-entity state.
2. Persist individual entity identities, escape locations, and learned player patterns.
3. Add clue graph triggers and irreversible environmental story set pieces.
4. Add coordinated multiplayer events and conflicting evidence.
5. Add admin telemetry and performance budgets.
