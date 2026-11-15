package dev.underthebedrock.horror;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerHorrorMemoryBehaviorTest {
    @Test
    void behaviorMemoryTracksRepeatedActionsWithoutAdvancingLoreProgression() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();

        assertEquals(1, memory.recordBehavior("destroyed_ancient_site"));
        assertEquals(2, memory.recordBehavior("destroyed_ancient_site"));
        assertTrue(memory.hasBehavior("destroyed_ancient_site"));
        assertEquals(2, memory.behaviorCount("destroyed_ancient_site"));
        assertEquals(0, memory.loreCount());
        assertEquals(0, memory.discoveredClueCount());
    }

    @Test
    void invalidBehaviorIdentifiersAreIgnored() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();

        assertEquals(0, memory.recordBehavior("../unsafe"));
        assertEquals(0, memory.behaviorCount("../unsafe"));
    }

    @Test
    void nullCanSummarizeWhatItRemembersAboutAnIndividualPlayer() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();
        memory.recordBehavior("helped_resident");

        String response = new NullIntelligence()
            .respond(null, "What do you remember about me?", memory)
            .orElseThrow();

        assertTrue(response.contains("listened to them"));
    }

    @Test
    void nullReactivelyRecognizesStolenArtifacts() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();
        memory.recordBehavior("stole_artifact");

        String response = new NullIntelligence()
            .respond(null, "What did I take from the archive?", memory)
            .orElseThrow();

        assertTrue(response.contains("last containers"));
    }
}
