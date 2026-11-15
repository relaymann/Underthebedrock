package dev.underthebedrock.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureDefinitionTest {
    @Test void everyStructureHasItsOwnDistanceGate() {
        for(StructureDefinition structure:StructureDefinition.values()){
            assertTrue(StructureDefinition.eligibleAt(structure.minimumDistance()).contains(structure));
            assertFalse(StructureDefinition.eligibleAt(structure.minimumDistance()-1).contains(structure));
        }
    }
    @Test void selectionIsDeterministicAndDoesNotStartInsideTheProtectedRadius() {
        assertEquals(StructureDefinition.select(9000,42L),StructureDefinition.select(9000,42L));
        assertTrue(StructureDefinition.select(511,42L).isEmpty());
    }
    @Test void allNineteenStructureTypesAreRegistered() {
        assertEquals(19,StructureDefinition.values().length);
        assertTrue(StructureDefinition.byId("base_constructor_lab").isPresent());
        assertTrue(StructureDefinition.byId("monolith").isPresent());
        assertTrue(StructureDefinition.byId("impossible_corridor").isPresent());
        assertTrue(StructureDefinition.byId("containment_facility").isPresent());
        assertTrue(StructureDefinition.byId("archive_hall").isPresent());
        assertTrue(StructureDefinition.byId("keeper_sanctuary").isPresent());
        assertTrue(StructureDefinition.byId("witness_refuge").isPresent());
        assertTrue(StructureDefinition.byId("erasure_vault").isPresent());
    }
    @Test void invalidDistancesCannotUnlockStructures() {
        assertTrue(StructureDefinition.eligibleAt(Double.NaN).isEmpty());
        assertTrue(StructureDefinition.eligibleAt(Double.POSITIVE_INFINITY).isEmpty());
        assertTrue(StructureDefinition.eligibleAt(-1).isEmpty());
    }
}
