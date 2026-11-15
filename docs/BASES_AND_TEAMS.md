# Bases, Teams, and Artifact Forging

## Teams
- `/team create <name>` creates a persistent team.
- `/team invite <player>`, `/team accept`, `/team kick <player>`, and `/team leave` manage membership.
- Direct player damage and player-shot projectile damage are cancelled between teammates.
- Teams contain real players only. No resident NPCs or simulated populations are introduced.

## Base Constructor
A Base Constructor is a rare airdrop bonus. The default chance is 2.5% per successfully populated crate and is configurable with `horror.supply-drops.base-constructor-chance`.
Use or drop the item within 32 blocks of a registered abandoned settlement, research facility, bunker, or railway site. Choose one of four handcrafted base layouts. Construction is refused if the footprint is obstructed or its chunks are not already loaded. The item is consumed only after successful construction.

## Base profiles
| Base | Storage chests | Forge multiplier | Security tier |
|---|---:|---:|---:|
| Industrial Workshop | 1 | 0.68x | I |
| Research Facility | 1 | 1.00x | IV |
| Underground Bunker | 6 | 1.35x | III |
| Railway Outpost | 4 | 1.20x | II |

Forge recipes use persistent finish timestamps. Jobs progress while the owner is offline and across server restarts. The effective duration is clamped to 1–24 hours. Workshop forging is fastest, the research facility is standard, the railway outpost is a little faster than the bunker, and the bunker is slowest.

## Base permissions
The owner has full access. Members of the owner's current team may enter by default. Their other permissions are individually disabled by default and can be granted or revoked:
- `enter`: cross the base perimeter
- `storage`: open base storage
- `forge`: use the artifact forge
- `build`: place and break blocks in the base
- `security`: use the security console and inspect generator state
- `research`: use the research terminal

Command: `/base permissions <teammate> <permission> <on|off>`.

## Power and security
The generator is restored with 2 Insulated Wire, 2 Ceramic Fuses, and 1 Resonance Power Cell using `/base power`. Restoration consumes the materials only after all requirements have been verified. Powered bases run a perimeter scan; a hostile mob detected within range alerts the owner and teammates granted security access. Detection radius follows the base security tier (Research IV, Bunker III, Railway II, Workshop I).

## Reset and persistence
Teams, base ownership, permissions, and forge jobs are stored in plugin YAML data. The existing full-reset workflow backs up plugin data and clears it on reset, so bases and teams reset with the world.
