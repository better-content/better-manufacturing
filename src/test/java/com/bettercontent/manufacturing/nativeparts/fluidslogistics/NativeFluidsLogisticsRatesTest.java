package com.bettercontent.manufacturing.nativeparts.fluidslogistics;

import com.bettercontent.manufacturing.integration.createfluids.FluidPartSettings;
import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import com.bettercontent.manufacturing.integration.sophisticatedstorage.HopperEngineering;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure formula checks only; production bake wiring reads actual TCon parts, not a material table. */
class NativeFluidsLogisticsRatesTest {
    @Test void nativeHandleSpeedChangesExistingModuleAndHopperCadenceWithoutTierReplacement() {
        double slower = NativePartRates.derive(NativePartRates.Measure.HANDLE_MINING_SPEED, -.1, 0);
        double faster = NativePartRates.derive(NativePartRates.Measure.HANDLE_MINING_SPEED, .2, 0);
        assertEquals(.9, slower, 1e-12);
        assertEquals(1.2, faster, 1e-12);
        assertEquals(22, PrettypipesRates.ratedExtractionInterval(20, slower));
        assertEquals(17, PrettypipesRates.ratedExtractionInterval(20, faster));
        assertEquals(1, PrettypipesRates.ratedExtractionLimit(1, slower));
        assertEquals(10, PrettypipesRates.ratedExtractionLimit(8, faster));
        assertEquals(.045f, PrettypipesRates.ratedSpeedBonus(.05f, slower), 1e-6);
        assertEquals(.24f, PrettypipesRates.ratedSpeedBonus(.2f, faster), 1e-6);
        assertEquals(9, HopperEngineering.ratedInterval(8, slower));
        assertEquals(7, HopperEngineering.ratedInterval(8, faster));
    }

    @Test void ironReferenceAndInvalidSavedRatioKeepNativeWork() {
        double iron = NativePartRates.derive(NativePartRates.Measure.HANDLE_MINING_SPEED, .1, .1);
        assertEquals(1, iron);
        assertEquals(15, PrettypipesRates.ratedExtractionInterval(15, iron));
        assertEquals(8, HopperEngineering.ratedInterval(8, iron));
        for (double invalid : new double[]{Double.NaN, 0, .74, 1.51}) {
            assertEquals(15, PrettypipesRates.ratedExtractionInterval(15, invalid));
            assertEquals(8, HopperEngineering.ratedInterval(8, invalid));
        }
        assertTrue(HopperEngineering.ratedInterval(8, 1.5) >= 1);
    }

    @Test void nativeHeadDurabilityVariesVesselCapacityWithoutDeletingFilledContents() {
        double cheap = NativePartRates.derive(NativePartRates.Measure.HEAD_DURABILITY, 120, 200);
        double sturdy = NativePartRates.derive(NativePartRates.Measure.HEAD_DURABILITY, 280, 200);
        assertEquals(.75, cheap);
        assertEquals(1.4, sturdy, 1e-12);
        assertEquals(1, FluidPartSettings.boundedFluidRating("create:fluid_tank", "fluid_capacity", cheap));
        assertEquals(1.4, FluidPartSettings.boundedFluidRating("create:fluid_tank", "fluid_capacity", sturdy), 1e-12);
        assertThrows(IllegalArgumentException.class,
            () -> FluidPartSettings.boundedFluidRating("create:fluid_tank", "fluid_capacity", Double.NaN));
    }

    @Test void nativeHeadWorkRateAffectsActiveTransferButNotPassivePipeAmplification() {
        double cheap = NativePartRates.derive(NativePartRates.Measure.HEAD_MINING_SPEED, 4, 6);
        double quick = NativePartRates.derive(NativePartRates.Measure.HEAD_MINING_SPEED, 9, 6);
        assertEquals(.75, cheap);
        assertEquals(1.5, quick);
        assertEquals(.75, FluidPartSettings.boundedFluidRating("create:portable_fluid_interface", "fluid_rate", cheap));
        assertEquals(1.5, FluidPartSettings.boundedFluidRating("create:portable_fluid_interface", "fluid_rate", quick));
        assertEquals(1, FluidPartSettings.boundedFluidRating("create:fluid_pipe", "fluid_rate", quick));
        assertEquals(.75, FluidPartSettings.boundedFluidRating("create:fluid_pipe", "fluid_rate", cheap));
        // IH uses these same two native head measures for approximate work/envelope,
        // with the native 80-unit and source-debit constraints owned by IH unchanged.
        assertEquals(1, NativePartRates.derive(NativePartRates.Measure.HEAD_DURABILITY, 200, 200));
    }
}
