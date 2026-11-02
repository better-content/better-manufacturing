package com.bettercontent.manufacturing.nativeparts.create;

import com.bettercontent.manufacturing.integration.createbulk.BulkPartSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native-stat input contrasts, without an engineering material/trait catalogue or loader. */
class BulkPartNativeRatesTest {
    @Test void wheelWeakestNativePartLimitsOreRateAndStrongestStillPaysStress() {
        double fastHammer = NativePartRates.derive(Measure.HEAD_MINING_SPEED, 12, 6);
        double ironPlate = NativePartRates.derive(Measure.HEAD_DURABILITY, 250, 250);
        var budget = BulkPartSettings.wheelBudget(fastHammer, ironPlate);
        assertEquals(1, budget.workRate(), 1e-9);
        assertEquals(1.5, budget.load(), 1e-9);
        double softPlate = NativePartRates.derive(Measure.HEAD_DURABILITY, 125, 250);
        assertEquals(.75, BulkPartSettings.wheelBudget(fastHammer, softPlate).workRate(), 1e-9);
        assertEquals(1.5, BulkPartSettings.wheelBudget(fastHammer, softPlate).load(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> BulkPartSettings.wheelBudget(2, 1));
    }

    @Test void nativeLimbPullAndVelocityProduceIndependentEjectorSettings() {
        double draw = NativePartRates.derive(Measure.LIMB_DRAW_SPEED, .15, 0);
        double throwSpeed = NativePartRates.derive(Measure.LIMB_VELOCITY, -.1, 0);
        assertEquals(1.15, draw, 1e-9);
        assertEquals(.9, throwSpeed, 1e-9);
        assertNotEquals(draw, throwSpeed);
        assertEquals(1, NativePartRates.derive(Measure.LIMB_DRAW_SPEED, 0, 0), 1e-9);
    }

    @Test void nativeHandleMotionAndDurabilityProduceDistinctSteamSettings() {
        double conversion = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -.1, 0);
        double mechanicalLimit = NativePartRates.derive(Measure.HANDLE_DURABILITY, .2, 0);
        assertEquals(.9, conversion, 1e-9);
        assertEquals(1.2, mechanicalLimit, 1e-9);
        assertThrows(IllegalArgumentException.class,
            () -> NativePartRates.derive(Measure.HANDLE_DURABILITY, Double.NaN, 0));
    }
}
