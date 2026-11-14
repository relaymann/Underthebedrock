package dev.underthebedrock.horror;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NullIntelligenceTest {
    @Test
    void residentEvidenceChangesNullsResponse() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();
        memory.discoverLore("resident_mara");
        String response = new NullIntelligence().respond(null, "Who lived here?", memory).orElseThrow();
        assertTrue(response.contains("They had names"));
    }

    @Test
    void orphanedIdentityMakesNullAcknowledgeTheFailedAnchorRemoval() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();
        memory.discoverLore("orphaned_identity");
        String response = new NullIntelligence().respond(null, "Why did you erase me?", memory).orElseThrow();
        assertTrue(response.contains("anchor"));
    }
    @Test
    void NullRespondsToPlayerGriefAndAttachmentRatherThanOnlyKeywordPrompts() {
        PlayerHorrorMemory memory = new PlayerHorrorMemory();
        memory.discoverLore("resident_mara");
        String grief = new NullIntelligence().respond(null, "I miss them", memory).orElseThrow();
        assertTrue(grief.contains("archive"));
        String accusation = new NullIntelligence().respond(null, "You are lying", memory).orElseThrow();
        assertTrue(accusation.contains("lie"));
    }
}
