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
}
