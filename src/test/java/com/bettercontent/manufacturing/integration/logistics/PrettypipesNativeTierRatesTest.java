package com.bettercontent.manufacturing.integration.logistics;

import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain arithmetic only: no Pretty Pipes registry, block entity, loader or world is started. */
class PrettypipesNativeTierRatesTest {
    @Test void referenceMaterialPreservesEachNativeModuleTier() {
        assertEquals(20, PrettypipesRates.ratedExtractionInterval(20, 1));
        assertEquals(15, PrettypipesRates.ratedExtractionInterval(15, 1));
        assertEquals(10, PrettypipesRates.ratedExtractionInterval(10, 1));
        assertEquals(1, PrettypipesRates.ratedExtractionLimit(1, 1));
        assertEquals(8, PrettypipesRates.ratedExtractionLimit(8, 1));
        assertEquals(64, PrettypipesRates.ratedExtractionLimit(64, 1));
        assertEquals(.05f, PrettypipesRates.ratedSpeedBonus(.05f, 1), 1e-6);
        assertEquals(.1f, PrettypipesRates.ratedSpeedBonus(.1f, 1), 1e-6);
        assertEquals(.2f, PrettypipesRates.ratedSpeedBonus(.2f, 1), 1e-6);
    }

    @Test void cheapMaterialPaysTierRelativeWorkWithoutPromotingLowToHigh() {
        assertEquals(27, PrettypipesRates.ratedExtractionInterval(20, .75));
        assertEquals(20, PrettypipesRates.ratedExtractionInterval(15, .75));
        assertEquals(13, PrettypipesRates.ratedExtractionInterval(10, .75));
        assertEquals(1, PrettypipesRates.ratedExtractionLimit(1, .75));
        assertEquals(6, PrettypipesRates.ratedExtractionLimit(8, .75));
        assertEquals(48, PrettypipesRates.ratedExtractionLimit(64, .75));
        assertEquals(.0375f, PrettypipesRates.ratedSpeedBonus(.05f, .75), 1e-6);
        assertEquals(.075f, PrettypipesRates.ratedSpeedBonus(.1f, .75), 1e-6);
        assertEquals(.15f, PrettypipesRates.ratedSpeedBonus(.2f, .75), 1e-6);
    }

    @Test void boundedFasterMaterialCannotCreateUnboundedStackOrInvalidBaseline() {
        assertEquals(7, PrettypipesRates.ratedExtractionInterval(10, 1.5));
        assertEquals(12, PrettypipesRates.ratedExtractionLimit(8, 1.5));
        assertEquals(64, PrettypipesRates.ratedExtractionLimit(64, 1.5));
        assertTrue(PrettypipesRates.ratedSpeedBonus(.2f, 1.5) > .2f);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, .74, 1.51}) {
            assertEquals(15, PrettypipesRates.ratedExtractionInterval(15, invalid));
            assertEquals(8, PrettypipesRates.ratedExtractionLimit(8, invalid));
            assertEquals(.1f, PrettypipesRates.ratedSpeedBonus(.1f, invalid));
        }
        assertEquals(0, PrettypipesRates.ratedExtractionLimit(0, 1.5));
        assertEquals(0, PrettypipesRates.ratedExtractionInterval(0, 1.5));
    }
}
