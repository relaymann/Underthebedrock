# Development rules

- Target Paper 26.1.2 and Java 25 unless the owner explicitly changes the target.
- Keep Nova and BetterModel as first-class integrations through their public APIs. Never depend on private internals.
- Never pre-generate a massive radius synchronously. Let chunk requests drive void-floor generation.
- Do not create an artificial world border. Minecraft's native coordinate limits are the only practical boundary.
- Keep chunk generation deterministic and cheap; do no disk, database, or network I/O in generation callbacks.
- Preserve the horror world as the intended play space; do not reintroduce a player-facing return-to-old-world command.
- Keep legacy inventory backups before one-time inventory isolation. Never silently destroy existing player inventory during migration.
- Keep horror events per-player and cooldown-governed; avoid global spam, forced chunk loads, or repeated jumpscares.
- After each feature batch, run a full build and both code and behavior/performance audits.
- Group changes into coherent commits. Never claim a build or in-game test passed unless it actually ran.


- Keep the experience-position, isolation, legacy-backup and facility-state YAML formats backward-compatible. Never overwrite an original legacy backup with an empty inventory.
- Save player positions at a measured cadence and on disconnect/shutdown; admin-start routing may place online players at the entrance, while reconnects should resume the saved location.
- Fake block changes must remain client-only; never mutate shared world blocks to create one player's hallucination.
- Persist facility state before consuming a player's resources. Keep generator repairs and safe-room upgrades atomic from the player's perspective.
- Keep behavior counters separate from clue/lore progression so routine player actions cannot bypass the ARG ending.
- Run entity discovery less frequently than movement. Keep movement bounded, loaded-chunk-only, configurable, and private to the intended player.
- Validate the feature branch through CI. Publish the installable `latest` release only from `main`.
