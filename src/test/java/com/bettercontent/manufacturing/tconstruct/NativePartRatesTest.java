package com.bettercontent.manufacturing.tconstruct;

import org.junit.jupiter.api.Test;
import static com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure formulas: inputs represent native TCon material stats, not machine-policy ratings. */
class NativePartRatesTest {
    @Test void distinctPolymerEquivalentHandleStatsProduceDistinctSettingsWithoutPolicy() {
        // PLA / polypropylene native handle mining modifiers; reference is a neutral handle.
        assertEquals(.90, NativePartRates.derive(HANDLE_MINING_SPEED, -.10, 0), 1e-9);
        assertEquals(.95, NativePartRates.derive(HANDLE_MINING_SPEED, -.05, 0), 1e-9);
        // Pumping uses native handle durability instead of a pump_rate table.
        assertEquals(.85, NativePartRates.derive(HANDLE_DURABILITY, -.15, 0), 1e-9);
        assertEquals(1.05, NativePartRates.derive(HANDLE_DURABILITY, .05, 0), 1e-9);
    }

    @Test void nativeHeadsSupplyPlateBladeAndHeadWorkWithoutInventedRoles() {
        assertEquals(1.25, NativePartRates.derive(HEAD_MINING_SPEED, 2.5, 2), 1e-9);
        assertEquals(1.5, NativePartRates.derive(HEAD_MINING_SPEED, 3.0, 2), 1e-9);
        assertEquals(1.2, NativePartRates.derive(HEAD_DURABILITY, 120, 100), 1e-9);
        assertEquals(.75, NativePartRates.derive(HEAD_MINING_SPEED, 1, 10), 1e-9);
        assertEquals(1.5, NativePartRates.derive(HEAD_MINING_SPEED, 20, 10), 1e-9);
    }

    @Test void nativeLimbHasBaseDurabilityAndAdditiveDrawSpeed() {
        // Pinned TCon LimbMaterialStats.apply uses durability.update and DRAW_SPEED.add;
        // ToolStats.DRAW_SPEED has a native base of 1, unlike HEAD base ratios.
        assertEquals(1.25, NativePartRates.derive(LIMB_DURABILITY, 125, 100), 1e-9);
        assertEquals(1.10, NativePartRates.derive(LIMB_DRAW_SPEED, .10, 0), 1e-9);
        assertEquals(.90, NativePartRates.derive(LIMB_VELOCITY, -.10, 0), 1e-9);
    }

    @Test void invalidNativeStatsCannotBecomeConfiguredRatings() {
        // Zero head attack is a valid native material value, but cannot produce a positive
        // work-rate ratio; other native head measures can still use this same material.
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HEAD_ATTACK, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HEAD_MINING_SPEED, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HEAD_MINING_SPEED, 6, 0));
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HANDLE_MINING_SPEED, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HANDLE_DURABILITY, .1, -1));
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HEAD_MINING_SPEED, Double.NaN, 6));
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(HANDLE_ATTACK_SPEED, 0, Double.POSITIVE_INFINITY));
    }
}
