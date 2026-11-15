package dev.underthebedrock.horror;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class EntityEncounterPolicyTest {
    private final EntityEncounterPolicy policy = new EntityEncounterPolicy(256, 3072, 0.18);

    @Test void startingAreaIsCompletelySafe() {
        assertEquals(0, policy.chanceAt(0));
        assertEquals(0, policy.chanceAt(256));
        assertTrue(policy.eligibleAt(511).isEmpty());
        assertTrue(policy.roll(256, new Random(1)).isEmpty());
    }

    @Test void chanceIncreasesWithDistanceAndHasACap() {
        assertTrue(policy.chanceAt(1024) > 0);
        assertTrue(policy.chanceAt(2048) > policy.chanceAt(1024));
        assertTrue(policy.chanceAt(4096) > policy.chanceAt(2048));
        assertTrue(policy.chanceAt(1024) >= 0.04);
        assertEquals(0.18, policy.chanceAt(3072), 0.000001);
        assertEquals(0.18, policy.chanceAt(100000), 0.000001);
    }

    @Test void rosterUnlocksAtItsConfiguredDistance() {
        assertFalse(policy.eligibleAt(767).contains(EntityArchetype.WITNESS));
        assertTrue(policy.eligibleAt(768).contains(EntityArchetype.WITNESS));
        assertFalse(policy.eligibleAt(3071).contains(EntityArchetype.PURSUER));
        assertTrue(policy.eligibleAt(3072).contains(EntityArchetype.PURSUER));
        assertFalse(policy.eligibleAt(12287).contains(EntityArchetype.ORIGINAL));
        assertTrue(policy.eligibleAt(12288).contains(EntityArchetype.ORIGINAL));
        assertEquals(24, EntityArchetype.values().length);
    }

    @Test void dangerousArchetypesUnlockFartherOut() {
        assertFalse(policy.eligibleAt(1024).contains(EntityArchetype.AGGRESSOR));
        assertFalse(policy.eligibleAt(6143).contains(EntityArchetype.DEEP_ENTITY));
        assertTrue(policy.eligibleAt(6144).contains(EntityArchetype.DEEP_ENTITY));
        assertTrue(policy.eligibleAt(3072).contains(EntityArchetype.AGGRESSOR));
    }

    @Test void invalidDistancesAreSafe() {
        assertEquals(0, policy.chanceAt(Double.NaN));
        assertEquals(0, policy.chanceAt(Double.NEGATIVE_INFINITY));
        assertEquals(0, policy.chanceAt(Double.POSITIVE_INFINITY));
        assertTrue(policy.eligibleAt(Double.NaN).isEmpty());
    }
}
