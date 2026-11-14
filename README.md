# Under The Bedrock

Standalone Paper 26.1.2 psychological / analog ARG horror plugin with a persistent void world, individualized horror pacing, evidence discovery, BetterModel apparitions, and custom exploration equipment.

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

## Custom equipment
All 25 custom equipment items have persistent item identities, custom names/lore, cooldowns, and right-click abilities. They are distributed through procedural structure chests and supply drops.

- Exploration: Distorted Compass, Signal Spyglass, Emergency Lantern, Broken Chronometer.
- Detection/defense: Motion Detector, Resonance Horn, Emergency Barrier, Null Ward.
- Survival: Field Medkit, Stabilizer Injection, Emergency Rations, Sedative, Adrenaline Injector (Speed Boost).
- Investigation: Investigation Journal, Evidence Scanner, Echo Recorder, Anomaly Map.
- Utility: Portable Storage Device (persistent 27 slots), Signal Beacon, Tripwire Alarm, Emergency Flare.
- Late-game: Resonance Core, Death Echo, Null Fragment, Last Resort Device.

Abilities include entity scans, temporary night vision and barriers, one-hit ward protection, healing/status recovery, evidence and structure detection, persistent portable storage, proximity alarms, death-location recall, a one-use return device, and an 18-second Speed II burst. Horn and Null Fragment increase Null's attention; the compass can mislead. Cooldowns persist in item-cooldowns.yml, portable storage and death echoes persist separately.

## ARG and world systems
Six ordered clues form a personal evidence chain. Fourteen deterministic structure archetypes are selected by exploration distance and rarity, generated only in loaded chunks, and restored hourly. Supply crates appear every 30 minutes and announce exact coordinates in chat. Horror memory, structure state, and supply-drop schedules persist across restarts.

## Build
Use JDK 25 and Gradle 9.7.1: `gradle clean test build`. JAR output: `build/libs/`. Install matching Nova 0.23.0 and BetterModel 3.5.1 server plugins.
