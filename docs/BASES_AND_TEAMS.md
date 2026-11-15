# Bases, Teams, and Artifact Forging

## Teams
- `/team create <name>` creates a persistent team.
- `/team invite <player>`, `/team accept`, `/team kick <player>`, and `/team leave` manage membership. Invitations expire after 24 hours.
- Direct player and player-fired projectile damage is cancelled between teammates.
- Teams contain real players only. No resident NPCs or simulated populations are introduced.

## Base Constructor discovery
Base Constructors have three discovery routes:
- **Abandoned Constructor Lab:** a rare, one-time research structure containing a guaranteed prototype cache. It does not regenerate its cache.
- **Abandoned Research Facility:** a 12% chance for the prototype to appear in one of its supply chests.
- **Airdrops:** a 2.5% bonus chance per successfully populated crate, configurable with `horror.supply-drops.base-constructor-chance`.

Use or drop the item within 32 blocks of an unclaimed abandoned settlement, research facility, bunker, or railway site. The Constructor Lab is a discovery location, not a construction site; its own warning directs players to take the prototype elsewhere. Each site can be converted once. Choose one of four handcrafted base layouts. Construction checks the entire footprint and loaded chunks before placing anything; the Constructor is consumed only after a successful build.

## Base profiles

| Base | Storage chests | Forge multiplier | Security tier |
|---|---:|---:|---:|
| Industrial Workshop | 1 | 0.68x | I |
| Research Facility | 1 | 1.00x | IV |
| Underground Bunker | 6 | 1.35x | III |
| Railway Outpost | 4 | 1.20x | II |

All four layouts also receive the same shared features: a concealed archive wing, research terminal, exploration map table, four security-camera stations, generator, artifact display alcove, and ambient environmental disturbances. Their shared features do not add extra storage chests.

Forge recipes use persistent finish timestamps. Jobs progress while the owner is offline and across server restarts. Effective durations are clamped to 1–24 hours. Workshop forging is fastest, the research facility is standard, the railway outpost is a little faster than the bunker, and the bunker is slowest.

## Base permissions
The owner has full access. Members of the owner's current team may enter by default. Their other permissions are individually disabled by default and can be granted or revoked:
- `enter`: cross the base perimeter
- `storage`: open base storage
- `forge`: use the artifact forge
- `build`: place and break blocks in the base
- `security`: use the security console and inspect camera/generator systems
- `research`: use the research terminal, map table, and hidden archive

Command: `/base permissions <teammate> <permission> <on|off>`.

## Generator fuel and upgrades
Install a generator with 2 Insulated Wire, 2 Ceramic Fuses, and 1 Resonance Power Cell using `/base power`. A full tank lasts up to four days (96 hours). Refuel by holding any item in the main hand and using `/base fuel`:
- Vanilla items add 4 hours by default.
- Custom artifact items add 24 hours by default.
- The tank cannot exceed 96 hours.

Fuel capacity and fuel values are configurable under `horror.base.generator`. `/base upgrade` spends 1 Resonance Power Cell and 4 Iron Ingots to upgrade shared base systems, up to Level III. Each level adds 1 hour to vanilla fuel and 2 hours to artifact fuel. These upgrades work the same for every base type. Use `/base abandon` followed by `/base abandon confirm` to leave a base behind as an abandoned structure and release ownership; the building and its stored items are not deleted, and the old site cannot be claimed again.

Powered bases run a perimeter scan; hostile mobs detected within range alert the owner and teammates granted security access. Detection range follows the base security tier: Research IV, Bunker III, Railway II, Workshop I.

## Reset and persistence
Teams, base ownership, permissions, generator fuel, upgrades, and forge jobs are stored in plugin YAML data. The existing full-reset workflow backs up plugin data and clears it on reset, so bases and teams reset with the world.
