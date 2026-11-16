package dev.underthebedrock.horror;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EntityEncounterPolicyTest {
    private static final class GuaranteedRoll extends Random {
        @Override
        public double nextDouble() {
            return 0.0;
        }
    }

    @Test
    void chanceIsZeroInsideSafeRadiusAndGrowsLinearly() {
        EntityEncounterPolicy policy = new EntityEncounterPolicy(512, 4096, 0.10);

        assertEquals(0.0, policy.chanceAt(512), 1.0E-9);
        assertEquals(0.05, policy.chanceAt(2304), 1.0E-9);
        assertEquals(0.10, policy.chanceAt(4096), 1.0E-9);
        assertEquals(0.10, policy.chanceAt(8192), 1.0E-9);
    }

    @Test
    void invalidDistancesNeverProduceEncounters() {
        EntityEncounterPolicy policy = new EntityEncounterPolicy(512, 4096, 0.10);

        assertEquals(0.0, policy.chanceAt(Double.NaN));
        assertEquals(0.0, policy.chanceAt(Double.POSITIVE_INFINITY));
        assertTrue(policy.eligibleAt(Double.NaN).isEmpty());
        assertTrue(policy.roll(Double.NaN, new GuaranteedRoll()).isEmpty());
    }

    @Test
    void noArchetypeCanRollBeforeItsUnlockDistance() {
        EntityEncounterPolicy policy = new EntityEncounterPolicy(512, 4096, 0.10);

        assertTrue(policy.eligibleAt(767.99).isEmpty());
        assertTrue(policy.roll(767.99, new GuaranteedRoll()).isEmpty());
    }

    @Test
    void rollSelectsOnlyUnlockedArchetypes() {
        EntityEncounterPolicy policy = new EntityEncounterPolicy(512, 4096, 0.10);

        EntityArchetype selected = policy.roll(768, new GuaranteedRoll()).orElseThrow();

        assertTrue(selected.unlockDistance() <= 768);
    }
}
