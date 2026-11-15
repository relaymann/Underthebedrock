# Under The Bedrock — World Canon and Systems

## The erasure

The world is not an underground society and it is not a living civilization simulator. Everyone who once lived here was erased. The only remaining people are the real players connected to the server. This is a permanent design rule.

## What remains

- Settlements, homes, stations, medical wings, archives, power stations, research facilities, bunkers, shrines, and other sites are abandoned archaeological spaces.
- No villagers, human NPC populations, active factions, settlement expansion, recruitment, or moving civilization services may spawn.
- Personal voices survive only as fixed recordings, memory shards, damaged documents, signs, and environmental traces. They are evidence of people who are gone, not living residents.
- Automated systems may continue malfunctioning long after their operators vanished. A light can flicker, a door can open, a timetable can update, or a terminal can repeat its last instruction, but none of these imply a surviving society.
- The player and other actual server players are the only remaining people. Multiplayer players must remain genuine players, not simulated residents.

## Connected horror systems

OccurrenceDirector is the per-player pacing and memory brain. It tracks exploration distance, tension, provocation, chapter progress, behavior, chat, and encounter cooldowns. Its adaptive event roll now increases gradually with player tension and provocation, with a configurable hard cap and the existing minimum occurrence gap still enforced. HorrorEventDirector schedules larger occurrences. HorrorEntityController runs player-specific encounters with observation, stalking, searching, hunting, retreat, and disappearance states. CivilizationManager supplies abandoned-site archaeology and environmental afterimages only; it also removes legacy resident echoes and old service carts from earlier versions. LoreManager preserves evidence and recordings per player.

The systems should coordinate through player memory and exploration context, avoid repetitive events, respect safe rooms and loaded-chunk boundaries, and escalate gradually rather than firing every scare at once.

## Full reset

/underthebedrock reset explains the consequences. /underthebedrock reset confirm schedules a graceful server shutdown. On the next startup, before managers load, the plugin backs up the world and plugin data to plugins/UnderTheBedrock/backups/reset-<UTC timestamp>/, unloads and removes the generated world, clears progression and generated-site state, and preserves config.yml plus the legacy inventory recovery files. The world is then regenerated from a clean state. The backup is not automatically deleted.
