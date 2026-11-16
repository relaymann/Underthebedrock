package dev.underthebedrock.horror;

/**
 * A player's private progression through the world's awareness.
 * Stages unlock richer phenomena; they do not directly increase attack damage or spawn spam.
 */
public enum WorldRecognitionStage {
    UNNOTICED,
    OBSERVED,
    ACKNOWLEDGED,
    FOLLOWED,
    HUNTED,
    RECOGNIZED;

    public static WorldRecognitionStage calculate(int regions, int clues, int lore,
                                                   long encounters, long distance, double tension) {
        int score = Math.min(12, Math.max(0, regions))
                + Math.min(8, Math.max(0, clues)) * 2
                + Math.min(10, Math.max(0, lore))
                + (int) (Math.min(20, Math.max(0, encounters)) / 2)
                + (int) (Math.max(0, distance) / 4096)
                + (int) (Math.max(0, Math.min(100, tension)) / 25);
        if (score >= 30) return RECOGNIZED;
        if (score >= 21) return HUNTED;
        if (score >= 14) return FOLLOWED;
        if (score >= 8) return ACKNOWLEDGED;
        if (score >= 3) return OBSERVED;
        return UNNOTICED;
    }

    public static WorldRecognitionStage calculate(PlayerHorrorMemory memory) {
        return calculate(memory.visitedRegions().size(), memory.discoveredClueCount(),
                memory.loreCount(), memory.encounters(), memory.distanceTravelled(), memory.tension());
    }

    public int maximumPhenomenonIndex() {
        return switch (this) {
            case UNNOTICED -> 2;
            case OBSERVED -> 4;
            case ACKNOWLEDGED -> 7;
            case FOLLOWED -> 10;
            case HUNTED -> 13;
            case RECOGNIZED -> 15;
        };
    }
}
