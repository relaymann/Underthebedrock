package dev.underthebedrock.base;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaseUpgradeRulesTest {
    @Test void levelsAreBoundedToThree(){assertEquals(0,BaseUpgradeRules.clampLevel(-2));assertEquals(3,BaseUpgradeRules.clampLevel(9));}
    @Test void forgeGetsFasterWithEachLevel(){double base=1.35;assertTrue(BaseUpgradeRules.forgeMultiplier(base,1)<base);assertTrue(BaseUpgradeRules.forgeMultiplier(base,2)<BaseUpgradeRules.forgeMultiplier(base,1));assertTrue(BaseUpgradeRules.forgeMultiplier(base,3)<BaseUpgradeRules.forgeMultiplier(base,2));}
    @Test void farmCyclesSpeedUpFromFifteenToSixMinutes(){assertEquals(900_000L,BaseUpgradeRules.farmIntervalMillis(0));assertEquals(720_000L,BaseUpgradeRules.farmIntervalMillis(1));assertEquals(540_000L,BaseUpgradeRules.farmIntervalMillis(2));assertEquals(360_000L,BaseUpgradeRules.farmIntervalMillis(3));}
    @Test void foodPrinterProducesMoreAndCoolsDownFaster(){assertEquals(2,BaseUpgradeRules.foodPrinterOutput(0));assertEquals(8,BaseUpgradeRules.foodPrinterOutput(3));assertTrue(BaseUpgradeRules.foodPrinterCooldownMillis(3)<BaseUpgradeRules.foodPrinterCooldownMillis(0));}
    @Test void securityRangeAndResponseImprove(){assertTrue(BaseUpgradeRules.securityRadius(2,3)>BaseUpgradeRules.securityRadius(2,0));assertTrue(BaseUpgradeRules.securityAlertCooldownMillis(3)<BaseUpgradeRules.securityAlertCooldownMillis(0));}
}