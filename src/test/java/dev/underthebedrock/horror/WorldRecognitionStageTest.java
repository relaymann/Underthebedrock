package dev.underthebedrock.horror;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRecognitionStageTest {
    @Test
    void freshPlayerBeginsUnnoticed() {
        assertEquals(WorldRecognitionStage.UNNOTICED,
                WorldRecognitionStage.calculate(0, 0, 0, 0, 0, 0));
    }

    @Test
    void explorationAndEvidenceGraduallyUnlockRecognition() {
        assertEquals(WorldRecognitionStage.OBSERVED,
                WorldRecognitionStage.calculate(3, 0, 0, 0, 0, 0));
        assertEquals(WorldRecognitionStage.ACKNOWLEDGED,
                WorldRecognitionStage.calculate(4, 2, 0, 0, 0, 0));
        assertEquals(WorldRecognitionStage.FOLLOWED,
                WorldRecognitionStage.calculate(8, 3, 0, 0, 0, 0));
    }

    @Test
    void deepProgressionUnlocksHuntingAndRecognition() {
        assertEquals(WorldRecognitionStage.HUNTED,
                WorldRecognitionStage.calculate(8, 4, 2, 8, 8192, 75));
        assertEquals(WorldRecognitionStage.RECOGNIZED,
                WorldRecognitionStage.calculate(12, 8, 10, 20, 81920, 100));
    }

    @Test
    void stageScoreIsBoundedAndNeverTreatsNegativeProgressAsPositive() {
        assertEquals(WorldRecognitionStage.UNNOTICED,
                WorldRecognitionStage.calculate(-10, -10, -10, -10, -10, -10));
        assertEquals(WorldRecognitionStage.RECOGNIZED,
                WorldRecognitionStage.calculate(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 100));
    }

    @Test
    void eachStageUnlocksMorePhenomena() {
        int previous = -1;
        for (WorldRecognitionStage stage : WorldRecognitionStage.values()) {
            int current = stage.maximumPhenomenonIndex();
            assertTrue(current > previous);
            previous = current;
        }
    }
}
