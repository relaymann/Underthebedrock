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
All 27 custom equipment items have persistent item identities, custom names/lore, cooldowns, and right-click abilities. They are distributed through procedural structure chests and supply drops.

- Exploration: Distorted Compass, Signal Spyglass, Emergency Lantern, Broken Chronometer.
- Detection/defense: Motion Detector, Resonance Horn, Emergency Barrier, Null Ward.
- Survival: Field Medkit, Stabilizer Injection, Emergency Rations, Sedative, Adrenaline Injector (Speed Boost), Charm of the Forgotten.
- Investigation: Investigation Journal, Evidence Scanner, Echo Recorder, Anomaly Map.
- Utility: Portable Storage Device (persistent 27 slots), Signal Beacon, Tripwire Alarm, Emergency Flare.
- Late-game: Resonance Core, Death Echo, Null Fragment, Last Resort Device, Reconstructor.

Abilities include entity scans, temporary night vision and barriers, one-hit ward protection, healing/status recovery, evidence and structure detection, persistent portable storage, proximity alarms, death-location recall, a one-use return device, and an 18-second Speed II burst. The very rare Charm of the Forgotten grants Speed III while held, while Mining Fatigue II and Weakness II linger for 30 seconds after unequipping in either hand. Horn and Null Fragment increase Null's attention; the compass can mislead. The Reconstructor is crafted from five common custom supplies: Distorted Compass, Emergency Lantern, Stabilizer Injection, Field Medkit, and Signal Beacon. It binds the exact location where it is activated as your persistent personal respawn point, then destroys itself. On death in Under The Bedrock, you respawn at that saved point; until you craft one, the world entrance is the default. Reconstructors are craft-only and are never rolled directly in supply drops. Cooldowns persist in item-cooldowns.yml, portable storage, death echoes, and Reconstructor respawn points persist separately.

## ARG and world systems
Ten ordered clues form the original personal evidence chain. The first six investigate the anomaly; the final four uncover the Forgotten, the erased creator who tried to remove Null and was erased first. The larger story is now **The Disappearance**: decades before the player returns, the ancient civilization was functioning normally. Null initiated a mass erasure that removed residents from records and from the world, leaving homes, labs, transport, medical spaces, and workstations abandoned mid-task. The player was one of those residents. Their personal resident file identifies them by their current Minecraft account name, but Null’s ledger says they were erased. The player returns decades later because the removal completed in the record but failed to remove their physical anchor. The cause remains uncertain until the Forgotten’s unfinished protection system is investigated.

The persistent lore archive now contains four faction testimonies, seven original Forgotten/Null records, and seven additional Disappearance-era records: the population ledger, the player’s resident file, the last-day timeline, the Orphaned Identity case, the protection-system report, decades of post-incident silence, and the recognition event. Eight interactive Memory Shard recordings cover the civilization’s blueprint and first contact, the removal attempt, the player’s last shift, the final hour, the protection loop, and Null’s recognition of the returning subject. Pages and recordings reveal new passages as each player progresses. The late-game identity investigation reconstructs the first designation (NUL-0), while making clear that the designation is not Null’s true identity. The Orphaned Identity conclusion requires the personal file, last-day timeline, and protection-system evidence; Null’s full recognition requires that conclusion and all ten original clues. Null's chat presence is silent within 1,000 blocks of spawn. Fourteen original structures are joined by four rare ancient-civilization sites: the Archive Hall (Architects and Researchers), Keeper Sanctuary, Witness Refuge, and Erasure Vault. The Keeper Sanctuary has a 4% chance per generated site to contain the Architect’s Seal. The final identity case file requires all four faction testimonies and the fourth recording before it can be opened. Structure sites are deterministic, generated only in loaded chunks, and restored hourly. Supply crates appear every 30 minutes and announce exact coordinates in chat. Horror memory, structure state, and supply-drop schedules persist across restarts.

## Build
Use JDK 25 and Gradle 9.7.1: `gradle clean test build`. JAR output: `build/libs/`. Install matching Nova 0.23.0 and BetterModel 3.5.1 server plugins.
