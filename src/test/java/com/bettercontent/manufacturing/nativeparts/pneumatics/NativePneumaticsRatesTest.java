package com.bettercontent.manufacturing.nativeparts.pneumatics;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.compressed.CompressedSettings;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureSettings;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettingsHolder;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Isolated mechanical formulas only; production bakes call the native TCon reader on the real consumed parts. */
class NativePneumaticsRatesTest {
    @Test void movingHandleChangesPaidCompressorRateWithoutMaterialPolicy() {
        double slow = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, -.15, 0);
        double fast = NativePartRates.derive(Measure.HANDLE_MINING_SPEED, .2, 0);
        assertEquals(.85, slow, 1e-12);
        assertEquals(1.2, fast, 1e-12);
        double slowAir = PneupressureSettings.compressionRate(1, slow, null);
        double fastAir = PneupressureSettings.compressionRate(1, fast, null);
        assertTrue(slowAir < 1 && fastAir > 1);
        assertTrue(fastAir > slowAir);
        assertEquals(Math.cbrt(1.2 * 1.1), PneupressureSettings.compressionRate(1, fast, 1.1), 1e-12);
        assertEquals(1, PneupressureSettings.compressionRate(1, 1, null), 1e-12);
    }

    @Test void nativePlateStatsAffectFlowAndBoundPressureWithoutMaterialTables() {
        double slowFlow = NativePartRates.derive(Measure.HEAD_MINING_SPEED, 4, 6);
        double fastFlow = NativePartRates.derive(Measure.HEAD_MINING_SPEED, 8, 6);
        assertEquals(.75, PneuTransferSettings.rate(transfer(slowFlow)), 1e-12);
        assertEquals(4d / 3d, PneuTransferSettings.rate(transfer(fastFlow)), 1e-12);
        assertEquals(75, PneuTransferSettings.scale(100, transfer(slowFlow)));
        assertEquals(133, PneuTransferSettings.scale(100, transfer(fastFlow)));
        double weakerPlate = NativePartRates.derive(Measure.HEAD_DURABILITY, 85, 100);
        double strongerPlate = NativePartRates.derive(Measure.HEAD_DURABILITY, 140, 100);
        assertEquals(.85, Math.min(1, weakerPlate), 1e-12);
        assertEquals(1, Math.min(1, strongerPlate), 1e-12);
        assertEquals(.75, CompressedSettings.boundedPartRate(1.5, slowFlow), 1e-12);
        assertEquals(4d / 3d, CompressedSettings.boundedPartRate(1.5, fastFlow), 1e-12);
    }

    private static PneuTransferSettingsHolder transfer(double rate) {
        MachineSettings settings = new MachineSettings("pneumaticcraft:small_tank",
            Map.of("transfer_rate", rate), Map.of(), null);
        return new PneuTransferSettingsHolder() {
            @Override public MachineSettings betterManufacturing$transferSettings() { return settings; }
            @Override public void betterManufacturing$setTransferSettings(MachineSettings next) { throw new UnsupportedOperationException(); }
        };
    }

    @Test void nativeBladeSpeedAndConsumedRotorRateAffectMotionOnly() {
        double slowBlade = NativePartRates.derive(Measure.HEAD_MINING_SPEED, 4, 6);
        double fastBlade = NativePartRates.derive(Measure.HEAD_MINING_SPEED, 8, 6);
        assertEquals(.75, slowBlade, 1e-12);
        assertEquals(4d / 3d, fastBlade, 1e-12);
        assertTrue(PneupressureSettings.compressionRate(slowBlade, 1, null)
            < PneupressureSettings.compressionRate(fastBlade, 1, null));
        assertEquals(1, PneuProcessSettings.motionRate(1, 1, 0), 1e-12);
        assertEquals(Math.sqrt(1.2 * 1.1), PneuProcessSettings.motionRate(1.2, 1.1, 1), 1e-12);
        assertTrue(PneuProcessSettings.motionRate(1, .8 * .9, 2) < 1);
        assertThrows(IllegalArgumentException.class, () -> PneuProcessSettings.motionRate(1, Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> PneupressureSettings.compressionRate(1, 1, 2d));
    }
}
