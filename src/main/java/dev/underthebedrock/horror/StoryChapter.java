package dev.underthebedrock.horror;

/**
 * Progression is deliberately paced across sustained exploration, evidence chains,
 * and meaningful encounters. A single lucky event should not skip whole chapters.
 */
public enum StoryChapter {
    ARRIVAL,
    ANOMALIES,
    SUSPICION,
    INVESTIGATION,
    NULL_INFLUENCE,
    DEEP_EXPLORATION;

    public static StoryChapter forPlayer(PlayerHorrorMemory memory) {
        long distance = memory.distanceTravelled();
        long encounters = memory.encounters();
        double provocation = memory.provocation();
        int clues = memory.discoveredClueCount();

        if (clues >= 8 || distance >= 25000 || (distance >= 16000 && encounters >= 18)) {
            return DEEP_EXPLORATION;
        }
        if (clues >= 5 || provocation >= 55 || (distance >= 12000 && encounters >= 8)) {
            return NULL_INFLUENCE;
        }
        if (clues >= 3 || distance >= 6000 || encounters >= 15) {
            return INVESTIGATION;
        }
        if (clues >= 2 || distance >= 2200 || encounters >= 7) {
            return SUSPICION;
        }
        if (distance >= 500 || encounters >= 2) {
            return ANOMALIES;
        }
        return ARRIVAL;
    }
}
