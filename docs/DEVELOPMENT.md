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
