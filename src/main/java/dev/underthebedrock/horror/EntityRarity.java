package dev.underthebedrock.horror;

/** Relative selection multipliers; these never change the global encounter roll chance. */
public enum EntityRarity {
    UNCOMMON(1.25),
    RARE(1.0),
    EXTREMELY_RARE(0.35);

    private final double selectionMultiplier;

    EntityRarity(double selectionMultiplier) {
        this.selectionMultiplier = selectionMultiplier;
    }

    public double selectionMultiplier() {
        return selectionMultiplier;
    }
}