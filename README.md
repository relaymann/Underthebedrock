# Under The Bedrock

A standalone Paper 26.1.2 psychological / analog ARG horror plugin built around an endless-feeling void world, persistent exploration, strange structures, unexplained occurrences, and intelligent entities.

## Target platform

- Minecraft Java / Paper 26.1.2
- Java 25
- Gradle 9.7.1
- Nova 0.23.0 integration (required at runtime for Nova-authored content)
- BetterModel 3.5.1 integration (required at runtime for custom 3D entity models)

Nova and BetterModel are first-class integrations. The core remains responsible for world generation, progression, persistence, encounter logic, and AI; the integrations provide custom world objects and rendered entity models.

## Initial commands

- `/underthebedrock start` — create/activate the separate horror world.
- `/enter` — enter the horror world.
- `/leave` — return to the saved previous location.
- `/underthebedrock status` — inspect world and integration status.

## Build

Use JDK 25 and Gradle 9.7.1:

```bash
gradle clean build
```

The plugin jar is written to `build/libs/`.

## Runtime dependencies

Install matching Paper 26.1.2 builds of Nova 0.23.0 and BetterModel 3.5.1 in the server's `plugins/` directory. Their APIs are compile-only and are not shaded into this plugin. The plugin will fail startup with a clear message if either integration is missing or disabled.

## Design principles

- No pre-generated world border or fixed exploration radius; chunks generate on demand within Minecraft's coordinate limits.
- No synchronous bulk generation or mass block edits.
- Persistent chunks and player return locations.
- Rare, contextual horror rather than predictable jumpscare timers.
- Custom entities use BetterModel; Nova custom blocks/items are used for discoverable structures and evidence.
- Integration failures must be visible and actionable, never silently replaced with fake functionality.

See `docs/ARCHITECTURE.md` and `docs/DEVELOPMENT.md` for implementation details.
