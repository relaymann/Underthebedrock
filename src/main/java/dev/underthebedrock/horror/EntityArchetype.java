package dev.underthebedrock.horror;

/** Behavioral contracts used by encounter selection and future model-backed entity controllers. */
public enum EntityArchetype {
    STALKER("Stalker", 0.15, "Watches from a distance, relocates when observed, rarely attacks."),
    AGGRESSOR("Aggressor", 0.95, "Hunts after provocation, can pursue, retreat, and remember escapes."),
    OBSERVER("Observer", 0.05, "Appears at the edge of sight and avoids direct contact."),
    MIMIC("Mimic", 0.35, "Copies familiar cues and uses learned player language deceptively."),
    WANDERER("Wanderer", 0.25, "Roams without a fixed target and leaves evidence behind."),
    DEEP_ENTITY("Deep Entity", 0.80, "A rare chapter-linked presence that alters the behavior of other entities.");

    private final String displayName;
    private final double aggression;
    private final String behavior;

    EntityArchetype(String displayName, double aggression, String behavior) {
        this.displayName = displayName;
        this.aggression = aggression;
        this.behavior = behavior;
    }

    public String displayName() { return displayName; }
    public double aggression() { return aggression; }
    public String behavior() { return behavior; }
}
