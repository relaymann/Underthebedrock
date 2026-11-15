package dev.underthebedrock.horror;

/**
 * Encounter identities. Each entry is a behavioral contract, not just a model name.
 * Unlock distances keep dangerous and lore-critical presences away from the entrance.
 */
public enum EntityArchetype {
    STALKER("Stalker", 0.15, 1024, 3.5, "Watches from a distance and relocates when observed."),
    AGGRESSOR("Aggressor", 0.95, 3072, 0.8, "Escalates after provocation and pursues without perfect tracking."),
    OBSERVER("Observer", 0.05, 768, 4.0, "Appears at the edge of sight and avoids direct contact."),
    MIMIC("Mimic", 0.35, 2048, 1.8, "Copies player footsteps and environmental sounds from a misleading distance."),
    WANDERER("Wanderer", 0.25, 1536, 2.5, "Roams without a fixed target and leaves evidence."),
    DEEP_ENTITY("Deep Entity", 0.80, 6144, 0.12, "A rare presence that disturbs other encounters."),
    WITNESS("The Witness", 0.02, 768, 3.8, "Watches from far away; vanishes when deliberately approached."),
    PEEKER("The Peeker", 0.08, 896, 3.2, "Peeks from cover and retreats when noticed."),
    FORGOTTEN_RESIDENT("Forgotten Resident", 0.04, 1152, 2.8, "Repeats fragments of ordinary routines from before the disappearance."),
    SURVEYOR("The Surveyor", 0.12, 1280, 2.2, "Paces boundaries and becomes interested in evidence carriers."),
    LURKER("The Lurker", 0.22, 1792, 2.0, "Follows outside the player's direct view."),
    DOORFRAME("The Doorframe", 0.12, 2304, 1.7, "Appears in thresholds and changes position between rooms."),
    CEILING_CRAWLER("The Ceiling Crawler", 0.48, 2816, 0.8, "Uses elevated ambush positions and retreats into darkness."),
    ECHO("The Echo", 0.10, 2560, 1.6, "Repeats environmental cues and old movement sounds."),
    PURSUER("The Pursuer", 0.92, 3072, 0.65, "Searches for a target and chases only with a plausible lead."),
    CRAWLING_ONE("The Crawling One", 0.78, 3328, 0.55, "Uses short, fast ground-level bursts and retreats when exposed."),
    HEAVY("The Heavy", 0.72, 3584, 0.35, "Advances slowly and announces itself through heavy impacts."),
    IMITATOR("The Imitator", 0.42, 2048, 1.15, "Copies the posture and approach pattern of familiar presences."),
    MISPRINT("The Misprint", 0.18, 4096, 1.0, "Distorts nearby environmental cues without relying on direct attacks."),
    DOUBLE("The Double", 0.30, 4352, 0.65, "Creates the impression of a second version of the player."),
    ABSENCE("The Absence", 0.05, 4608, 0.8, "Suppresses the feeling of a living environment before it is seen."),
    EXCAVATOR("The Excavator", 0.88, 6144, 0.18, "An ancient presence associated with deep and damaged sites."),
    UNPERSON("The Unperson", 0.16, 7168, 0.22, "A person missing from every surviving identity record."),
    ORIGINAL("The Original", 0.10, 12288, 0.015, "A boundary-layer presence that even other entities avoid."),
    WATCHER("The Watcher", 0.03, 1024, 2.6, "Silently observes from the edge of sight, then vanishes when approached."),
    DRIFTER("The Drifter", 0.18, 1536, 2.1, "Wanders abandoned spaces and occasionally follows a player's trail."),
    CRAWLER("The Crawler", 0.72, 2816, 0.72, "Moves through dark spaces and becomes dangerous when approached.");

    private final String displayName;
    private final double aggression;
    private final double unlockDistance;
    private final double selectionWeight;
    private final String behavior;

    EntityArchetype(String displayName, double aggression, double unlockDistance, double selectionWeight, String behavior) {
        this.displayName = displayName;
        this.aggression = aggression;
        this.unlockDistance = unlockDistance;
        this.selectionWeight = selectionWeight;
        this.behavior = behavior;
    }

    public String displayName() { return displayName; }
    public double aggression() { return aggression; }
    public double unlockDistance() { return unlockDistance; }
    public double selectionWeight() { return selectionWeight; }
    public String behavior() { return behavior; }

    public EntityRarity rarity() {
        return switch (this) {
            case WATCHER, DRIFTER, CRAWLER, MIMIC -> EntityRarity.UNCOMMON;
            case DEEP_ENTITY, EXCAVATOR, UNPERSON, ORIGINAL -> EntityRarity.EXTREMELY_RARE;
            default -> EntityRarity.RARE;
        };
    }

    public boolean isPassive() {
        return switch (this) {
            case OBSERVER, WITNESS, PEEKER, FORGOTTEN_RESIDENT, SURVEYOR, ABSENCE, UNPERSON, ORIGINAL, WATCHER -> true;
            default -> false;
        };
    }

    public boolean isPursuer() {
        return switch (this) {
            case AGGRESSOR, PURSUER, CRAWLING_ONE, HEAVY, EXCAVATOR, CRAWLER -> true;
            default -> false;
        };
    }
}