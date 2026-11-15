# Under The Bedrock

Standalone Paper 26.1.2 psychological / analog ARG horror plugin with a persistent void world, individualized horror pacing, evidence discovery, BetterModel apparitions, and custom exploration equipment.

## Target platform
- Minecraft Java / Paper 26.1.2
- Java 25 / Gradle 9.7.1
- BetterModel 3.5.1

## Commands
- /underthebedrock start — create/activate the horror world.
- The experience auto-starts by default. Active lockdown routes joins and deaths into the horror world; reconnecting players resume their last saved experience location.
- `/underthebedrock stop` is an administrator-only shutdown that temporarily lifts lockdown, evacuates current occupants to another loaded world, then unloads the horror world. Players do not receive a return command.
- A one-time legacy-state backup records inventory, armor, offhand, ender chest, experience, game mode, flight, health, hunger, saturation, and potion effects before isolating the player.
- Deaths return players to the horror world; Reconstructors provide personal respawn anchors.
- /enter — manually return to the horror-world entrance if needed. There is no player-facing command to return to the old world.
- /evidence — inspect your personal evidence journal.
- /underthebedrock status — inspect world and BetterModel integration status.

## Custom equipment
All 30 custom equipment items have persistent item identities, custom names/lore, cooldowns, and right-click abilities. They are distributed through procedural structure chests and supply drops.

- Exploration: Distorted Compass, Signal Spyglass, Emergency Lantern, Broken Chronometer.
- Detection/defense: Motion Detector, Resonance Horn, Emergency Barrier, Null Ward.
- Survival: Field Medkit, Stabilizer Injection, Emergency Rations, Sedative, Adrenaline Injector (Speed Boost), Charm of the Forgotten.
- Investigation: Investigation Journal, Evidence Scanner, Echo Recorder, Anomaly Map.
- Utility: Portable Storage Device (persistent 27 slots), Signal Beacon, Tripwire Alarm, Emergency Flare.
- Late-game: Resonance Core, Death Echo, Null Fragment, Last Resort Device, Reconstructor.
- Facility resources: Insulated Wire, Ceramic Fuse, and Resonance Power Cell.

Abilities include entity scans, temporary night vision and barriers, one-hit ward protection, healing/status recovery, evidence and structure detection, persistent portable storage, proximity alarms, death-location recall, a one-use return device, and an 18-second Speed II burst. The very rare Charm of the Forgotten grants Speed III while held, while Mining Fatigue II and Weakness II linger for 30 seconds after unequipping in either hand. Horn and Null Fragment increase Null's attention; the compass can mislead. The Reconstructor is crafted from five common custom supplies: Distorted Compass, Emergency Lantern, Stabilizer Injection, Field Medkit, and Signal Beacon. It binds the exact location where it is activated as your persistent personal respawn point, then destroys itself. On death in Under The Bedrock, you respawn at that saved point; until you craft one, the world entrance is the default. Reconstructors are craft-only and are never rolled directly in supply drops. Cooldowns persist in item-cooldowns.yml, portable storage, death echoes, and Reconstructor respawn points persist separately. The previous-world return-location system and `/leave` command have been removed; Under The Bedrock is the intended play space.

## ARG and world systems
Ten ordered clues form the original personal evidence chain. The first six investigate the anomaly; the final four uncover the Forgotten, the erased creator who tried to remove Null and was erased first. The larger story is now **The Disappearance**: decades before the player returns, the ancient civilization was functioning normally. Null initiated a mass erasure that removed residents from records and from the world, leaving homes, labs, transport, medical spaces, and workstations abandoned mid-task. The player was one of those residents. Their personal resident file identifies them by their current Minecraft account name, but Null’s ledger says they were erased. The player returns decades later because the removal completed in the record but failed to remove their physical anchor. The cause remains uncertain until the Forgotten’s unfinished protection system is investigated.

The persistent lore archive now contains four faction testimonies, seven original Forgotten/Null records, and seven additional Disappearance-era records: the population ledger, the player’s resident file, the last-day timeline, the Orphaned Identity case, the protection-system report, decades of post-incident silence, and the recognition event. Eight interactive Memory Shard recordings cover the civilization’s blueprint and first contact, the removal attempt, the player’s last shift, the final hour, the protection loop, and Null’s recognition of the returning subject. Pages and recordings reveal new passages as each player progresses. The late-game identity investigation reconstructs the first designation (NUL-0), while making clear that the designation is not Null’s true identity. The Orphaned Identity conclusion requires the personal file, last-day timeline, and protection-system evidence; Null’s full recognition requires that conclusion and all ten original clues. Null's chat presence is silent within 1,000 blocks of spawn. The earlier eighteen sites include the Archive Hall (Architects and Researchers), Keeper Sanctuary, Witness Refuge, and Erasure Vault. Six new civilization districts—residential blocks, Central Station, Medical Wing, Civilian Canteen, Archive Annex, and Power Substation—bring the total to twenty-four site types. The Keeper Sanctuary has a 4% chance per generated site to contain the Architect’s Seal. The final identity case file requires all four faction testimonies and the fourth recording before it can be opened. Structure sites are deterministic, generated only in loaded chunks, and restored hourly. Supply crates appear every 30 minutes and announce exact coordinates in chat. Horror memory, structure state, and supply-drop schedules persist across restarts.

## Lost Civilization update
The civilization layer adds six deterministic district types: residential blocks, a central station, a medical wing, a civilian canteen, an archive annex, and a power substation. Resident memory echoes (Mara, Elias, June, and Tomas) have player-specific dialogue and durable relationship discoveries. Their conversations evolve as the player reconstructs the final day and the orphaned identity. New locations contain personal files, population records, final service logs, medical records, maintenance reports, and protection-system evidence.

The station includes a traversable rail layout, minecart supplies, and occasional unmanned service-cart arrivals. A restrained environmental director escalates distant footsteps, doors that briefly open behind the player, redstone lights that flicker at later stages, signs that temporarily rewrite themselves, ash, smoke, and portal anomalies according to the player's discoveries rather than using constant jumpscares. Null reacts differently to resident evidence and the player's increasingly clear identity. All civilization sites persist in `civilization-sites.yml`; generation only touches already-loaded chunks near players. Discovering the Architect's Seal also stabilizes each Reconstructor for one additional activation; after Null recognizes the returning subject, activating one produces a warning that the anchor has been noticed.

## Build
Use JDK 25 and Gradle 9.7.1: `gradle clean test build`. JAR output: `build/libs/`. Install BetterModel 3.5.1. Nova is not required and no Nova Java agent argument is needed.


## Entity ecosystem overhaul
The encounter roster contains 24 behavioral archetypes: the original Observer, Stalker, Wanderer, Mimic, Aggressor, and Deep Entity plus the Witness, Peeker, Forgotten Resident, Surveyor, Lurker, Doorframe, Ceiling Crawler, Echo, Pursuer, Crawling One, Heavy, Imitator, Misprint, Double, Absence, Excavator, Unperson, and Original. Each has an unlock distance, aggression profile, and weighted selection rarity. Lore-critical and dangerous entities unlock farther from the entrance; repeated sightings are down-weighted so a player's encounter history gently favors unfamiliar presences.

The encounter controller now tracks player-specific observation, stalking, searching, hunting, retreating, and disappearance states. Apparitions remember a player's last known location during an encounter, react differently when watched, and move in bounded steps only through already-loaded, passable space. The controller keeps each apparition hidden from other players, limits encounter duration, and cleans up shells when players leave or the world shuts down. All 24 model keys are independently configurable under `horror.entities.models`; they currently fall back to the registered `watcher` model until distinct BetterModel assets are supplied. The six occurrence severity tiers and six story chapters remain the pacing and progression layers; the expanded roster does not bypass them.


## Experience takeover and multiplayer horror expansion
The experience now auto-starts by default. When an administrator starts it, online players are transferred immediately; future joins and deaths are routed into the horror world. While active, portal travel and world changes are intercepted so players cannot casually leave the experience. A one-time migration backs up legacy inventories, armor, and ender chests to `legacy-inventory-backups.yml` before isolating them, with migration state stored in `player-isolation.yml`. This prevents old-world gear from bypassing the survival progression without silently destroying the previous inventory.

A new adaptive event director adds cooldown-governed, player-specific disturbances that escalate with exploration: remote footsteps, false local signals, archive notices, distant doors, identity mismatches, and late-stage whispers that can use the name of a nearby teammate while remaining visible only to the target player. Null's chat responses now react to grief, apologies, accusations, offers of help, fear, and recognition as well as clue keywords. These remain deterministic, evidence-gated responses; no external AI service or network call is required.

Investigation equipment now has crafting progression: Evidence Scanner, Motion Detector, Echo Recorder, and Anomaly Map each have dedicated recipes. Facility resources add another progression tier: Insulated Wire and Ceramic Fuses are craftable, Resonance Power Cells combine rare signal-reactive materials, and Resonance Cores can be assembled from investigation equipment and a Power Cell. Facility components can also appear in exploration loot. All recipes are removed cleanly when the plugin disables.

## Takeover, facilities and individual intelligence
Each player's last Under The Bedrock location is saved in `player-experience-positions.yml` on a rolling interval, on quit, and during shutdown. Inventory migration uses a durable `prepared` / `complete` marker in `player-isolation.yml`: the original backup is atomically written first and is never replaced by a later empty inventory. If the process stops partway through migration, the original backup remains intact and clearing can safely resume.

The six civilization districts now expose interactive systems. The Power Substation accepts crafted components to restore the shared grid. Powered archive terminals reveal an additional protection-loop transmission. The Medical Wing contains claimable and upgradeable safe rooms with three reinforcement levels; their protection radius grows with investment, and apparition encounters and special disturbances are suppressed while players are inside. Central Station retains its rail network and service-cart arrivals and now has a working two-level service lift. Terminals, lifts, generator controls, and safe-room controls are persistent sign interactions. Facility state is saved atomically in `facility-state.yml`.

Null's persistent player memory now tracks behavior separately from clue/lore progression: investigating evidence, breaking ancient sites, taking artifacts, helping residents, researching equipment, restoring power, building shelters, and encountering multiplayer distortions. Null can later summarize or react to those actions, and behavior counters survive reconnects without accidentally advancing the main ARG clue chain.

The event director varies its selection against each player's event history. Later-stage events include recipient-only false join/leave messages, fake teammate warnings, temporary teammate disappearances, client-only blackouts and room distortions, and emergency broadcasts. Visual illusions use per-player block changes and never alter shared world blocks. Apparition discovery runs on a slower cadence than movement; movement uses configurable frequent ticks, bounded steps, last-known player positions, loaded-chunk checks, and per-player visibility that is reapplied when somebody enters the horror world. Claimed safe rooms suppress nearby apparition encounters and special disturbances.

Memory Shards already produce personal versions of recordings based on each player's discoveries, so two players can receive different information from the same recording. Clue and resident discoveries remain personal while physical civilization facilities remain shared. Null's reactions and the deterministic horror systems do not require an external AI service.
