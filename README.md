# Under The Bedrock

Standalone Paper 26.1.2 psychological / analog ARG horror plugin with a persistent void world, individualized horror pacing, evidence discovery, BetterModel apparitions, and custom exploration equipment.

## Target platform
- Minecraft Java / Paper 26.1.2
- Java 25 / Gradle 9.7.1
- Nova 0.23.0 / BetterModel 3.5.1

## Commands
- /underthebedrock start — create/activate the horror world.
- Players are automatically placed at the horror-world entrance when they join while the world is active.
- Deaths return players to the horror world; Reconstructors provide personal respawn anchors.
- /enter — manually return to the horror-world entrance if needed. There is no player-facing command to return to the old world.
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

Abilities include entity scans, temporary night vision and barriers, one-hit ward protection, healing/status recovery, evidence and structure detection, persistent portable storage, proximity alarms, death-location recall, a one-use return device, and an 18-second Speed II burst. The very rare Charm of the Forgotten grants Speed III while held, while Mining Fatigue II and Weakness II linger for 30 seconds after unequipping in either hand. Horn and Null Fragment increase Null's attention; the compass can mislead. The Reconstructor is crafted from five common custom supplies: Distorted Compass, Emergency Lantern, Stabilizer Injection, Field Medkit, and Signal Beacon. It binds the exact location where it is activated as your persistent personal respawn point, then destroys itself. On death in Under The Bedrock, you respawn at that saved point; until you craft one, the world entrance is the default. Reconstructors are craft-only and are never rolled directly in supply drops. Cooldowns persist in item-cooldowns.yml, portable storage, death echoes, and Reconstructor respawn points persist separately. The previous-world return-location system and `/leave` command have been removed; Under The Bedrock is the intended play space.

## ARG and world systems
Ten ordered clues form the original personal evidence chain. The first six investigate the anomaly; the final four uncover the Forgotten, the erased creator who tried to remove Null and was erased first. The larger story is now **The Disappearance**: decades before the player returns, the ancient civilization was functioning normally. Null initiated a mass erasure that removed residents from records and from the world, leaving homes, labs, transport, medical spaces, and workstations abandoned mid-task. The player was one of those residents. Their personal resident file identifies them by their current Minecraft account name, but Null’s ledger says they were erased. The player returns decades later because the removal completed in the record but failed to remove their physical anchor. The cause remains uncertain until the Forgotten’s unfinished protection system is investigated.

The persistent lore archive now contains four faction testimonies, seven original Forgotten/Null records, and seven additional Disappearance-era records: the population ledger, the player’s resident file, the last-day timeline, the Orphaned Identity case, the protection-system report, decades of post-incident silence, and the recognition event. Eight interactive Memory Shard recordings cover the civilization’s blueprint and first contact, the removal attempt, the player’s last shift, the final hour, the protection loop, and Null’s recognition of the returning subject. Pages and recordings reveal new passages as each player progresses. The late-game identity investigation reconstructs the first designation (NUL-0), while making clear that the designation is not Null’s true identity. The Orphaned Identity conclusion requires the personal file, last-day timeline, and protection-system evidence; Null’s full recognition requires that conclusion and all ten original clues. Null's chat presence is silent within 1,000 blocks of spawn. The earlier eighteen sites include the Archive Hall (Architects and Researchers), Keeper Sanctuary, Witness Refuge, and Erasure Vault. Six new civilization districts—residential blocks, Central Station, Medical Wing, Civilian Canteen, Archive Annex, and Power Substation—bring the total to twenty-four site types. The Keeper Sanctuary has a 4% chance per generated site to contain the Architect’s Seal. The final identity case file requires all four faction testimonies and the fourth recording before it can be opened. Structure sites are deterministic, generated only in loaded chunks, and restored hourly. Supply crates appear every 30 minutes and announce exact coordinates in chat. Horror memory, structure state, and supply-drop schedules persist across restarts.

## Lost Civilization update
The civilization layer adds six deterministic district types: residential blocks, a central station, a medical wing, a civilian canteen, an archive annex, and a power substation. Resident memory echoes (Mara, Elias, June, and Tomas) have player-specific dialogue and durable relationship discoveries. Their conversations evolve as the player reconstructs the final day and the orphaned identity. New locations contain personal files, population records, final service logs, medical records, maintenance reports, and protection-system evidence.

The station includes a traversable rail layout, minecart supplies, and occasional unmanned service-cart arrivals. A restrained environmental director escalates distant footsteps, doors that briefly open behind the player, redstone lights that flicker at later stages, signs that temporarily rewrite themselves, ash, smoke, and portal anomalies according to the player's discoveries rather than using constant jumpscares. Null reacts differently to resident evidence and the player's increasingly clear identity. All civilization sites persist in `civilization-sites.yml`; generation only touches already-loaded chunks near players. Discovering the Architect's Seal also stabilizes each Reconstructor for one additional activation; after Null recognizes the returning subject, activating one produces a warning that the anchor has been noticed.

## Build
Use JDK 25 and Gradle 9.7.1: `gradle clean test build`. JAR output: `build/libs/`. Install matching Nova 0.23.0 and BetterModel 3.5.1 server plugins.


## Entity ecosystem overhaul
The encounter roster contains 27 behavioral archetypes: the original Observer, Stalker, Wanderer, Mimic, Aggressor, and Deep Entity plus the Witness, Peeker, Forgotten Resident, Surveyor, Lurker, Doorframe, Ceiling Crawler, Echo, Pursuer, Crawling One, Heavy, Imitator, Misprint, Double, Absence, Excavator, Unperson, Original, Watcher, Drifter, and Crawler. Each has an unlock distance, aggression profile, and weighted selection rarity. The Uncommon tier receives a 1.25x selection multiplier, Rare remains 1.0x, and Extremely Rare receives 0.35x; these multipliers only affect which entity is selected after the unchanged global encounter roll succeeds. The Watcher observes without chasing, the Drifter roams and occasionally follows, and the Crawler becomes dangerous only in sufficiently dark areas. The existing Mimic now echoes occasional player footsteps and environmental sounds when out of sight. Lore-critical and dangerous entities unlock farther from the entrance; repeated sightings are down-weighted so a player's encounter history gently favors unfamiliar presences.

The encounter controller now tracks player-specific observation, stalking, searching, hunting, retreating, and disappearance states. Apparitions remember a player's last known location during an encounter, react differently when watched, and move in bounded steps only through already-loaded, passable space. The controller keeps each apparition hidden from other players, limits encounter duration, and cleans up shells when players leave or the world shuts down. All 27 model keys are independently configurable under `horror.entities.models`; they currently fall back to the registered `watcher` model until distinct BetterModel assets are supplied. The six occurrence severity tiers and six story chapters remain the pacing and progression layers; the expanded roster does not bypass them.


## Experience takeover and multiplayer horror expansion
The experience now auto-starts by default. When an administrator starts it, online players are transferred immediately; future joins and deaths are routed into the horror world. While active, portal travel and world changes are intercepted so players cannot casually leave the experience. A one-time migration backs up legacy inventories, armor, and ender chests to `legacy-inventory-backups.yml` before isolating them, with migration state stored in `player-isolation.yml`. This prevents old-world gear from bypassing the survival progression without silently destroying the previous inventory.

A new adaptive event director adds cooldown-governed, player-specific disturbances that escalate with exploration: remote footsteps, false local signals, archive notices, distant doors, identity mismatches, and late-stage whispers that can use the name of a nearby teammate while remaining visible only to the target player. Null's chat responses now react to grief, apologies, accusations, offers of help, fear, and recognition as well as clue keywords. These remain deterministic, evidence-gated responses; no external AI service or network call is required.

Investigation equipment now has crafting progression: Evidence Scanner, Motion Detector, Echo Recorder, and Anomaly Map each have dedicated recipes. Recipe registrations are removed cleanly when the plugin disables. The existing civilization districts, resident relationships, environmental distortions, and personalized apparition visibility provide the foundation for deeper group activities and larger world events.


## BetterModel apparition assets — first batch

The first 14 entity archetypes now have dedicated Blockbench Generic-model assets under `bettermodel/models/`. Each `.bbmodel` includes its own embedded pixel texture and distinct geometry. Player-like apparitions (Observer, Stalker, Wanderer, Mimic, Aggressor, Witness, Peeker, Lurker, Pursuer, and Watcher) use recognizable human silhouettes with deliberately unsettling proportions, faces, or posture. The Crawling One, Heavy, Doorframe, and Ceiling Crawler use nonhuman silhouettes.

### Install on the Minecraft server

1. Copy every `.bbmodel` file from `bettermodel/models/` into `plugins/BetterModel/models/` on the server.
2. Run `/bettermodel reload`.
3. Apply or distribute the resource pack generated by BetterModel so clients can render the models.
4. Restart Under The Bedrock or reload its configuration if the plugin is already running.

The plugin configuration maps these 14 archetypes to their own model IDs. The remaining 13 archetypes still use the configured `watcher` fallback until the second model batch is completed. These first-pass assets are static geometry; bespoke idle, walking, crawling, and disappearance animation clips are a separate follow-up pass.

BetterModel 3.5.1 imports Blockbench `.bbmodel` files from its `models` directory. Its reload command rebuilds the model registry and resource pack; clients must receive the generated pack to see the custom geometry.


### Mimic: Steve disguise and predator reveal

The Mimic now has two separate BetterModel assets:

- `mimic.bbmodel`: a deliberately ordinary Steve-like player disguise (classic blue shirt, indigo trousers, brown hair, normal player proportions) with subtle facial asymmetry and an unsettlingly fixed expression.
- `mimic_revealed.bbmodel`: a nonhuman predator form with a split skull/jaw, exposed neck, ribbed torso, elongated arms, claws, and protruding spines.

The controller keeps the Steve disguise while the Mimic observes and imitates footsteps. It reveals the predator form once player provocation reaches 12, or when it has closed to within 10 blocks after remaining unseen for at least two director checks. The model is swapped on the same player-specific carrier, with smoke/critical-hit particles and a layered scream/warden roar, then the Mimic enters its hunting state and tracks the player's current position. The old BetterModel tracker is closed after the replacement is created to prevent tracker leaks.

Install both files from `bettermodel/models/` into `plugins/BetterModel/models/`, reload BetterModel, and distribute the generated resource pack. The revealed form is a separate model asset; it must be registered as `mimic_revealed` for the transformation to occur.
