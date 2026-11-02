package com.bettercontent.manufacturing.nativeparts.create;

import com.bettercontent.manufacturing.integration.sifter.SifterFrameSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure native-handle formula checks; no material-policy table or game/loader bootstrap. */
class SifterFrameRateTest {
    @Test void nativeHandleSpeedContrastsOrdinaryMaterialsWithoutScreenTraits() {
        double slower = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -.10, 0);
        double faster = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -.05, 0);
        assertEquals(.90, SifterFrameSettings.frameRate(slower, slower, false, 1), 1e-9);
        assertEquals(.95, SifterFrameSettings.frameRate(faster, faster, false, 1), 1e-9);
        assertEquals(1, SifterFrameSettings.frameRate(1, 1, false, 1), 1e-9);
        assertTrue(SifterFrameSettings.frameRate(slower, slower, false, 1)
            < SifterFrameSettings.frameRate(faster, faster, false, 1));
    }

    @Test void brassUpgradeAveragesNativeHandlesAndExistingConfiguredPredecessorOnce() {
        double slower = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -.10, 0);
        double faster = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -.05, 0);
        assertEquals((slower + faster + 1) / 3, SifterFrameSettings.frameRate(slower, faster, true, 1), 1e-9);
        assertEquals((slower + faster + .75) / 3, SifterFrameSettings.frameRate(slower, faster, true, .75), 1e-9);
        assertEquals((slower + faster) / 2, SifterFrameSettings.frameRate(slower, faster, false, 1.5), 1e-9);
    }

    @Test void physicalBoundsRemainAndNonNativeNumbersDoNotSlipThrough() {
        assertEquals(.75, SifterFrameSettings.frameRate(.75, .75, false, 1), 1e-9);
        assertEquals(1.5, SifterFrameSettings.frameRate(1.5, 1.5, true, 1.5), 1e-9);
        assertThrows(IllegalArgumentException.class,
            () -> NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> SifterFrameSettings.frameRate(Double.NaN, 1, false, 1));
    }
}
