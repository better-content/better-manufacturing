package com.bettercontent.manufacturing.nativeparts.energyvehicles;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.bees.BeesPartSettings;
import com.bettercontent.manufacturing.integration.clockwork.ClockworkPartSettings;
import com.bettercontent.manufacturing.integration.diesel.DieselSettings;
import com.bettercontent.manufacturing.integration.eureka.EurekaEngineSettings;
import com.bettercontent.manufacturing.integration.powergrid.PowergridPartSettings;
import com.bettercontent.manufacturing.integration.railbeetle.RailBeetlePartSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Isolated formulas with contrasting native tool stats; no game, loader or engineering policy table. */
class NativeEnergyVehicleSettingsTest {
    @Test void nativeToolStatsFeedEngineAndGeneratorMechanicalWork() {
        double shaft = NativePartRates.derive(NativePartRates.Measure.HANDLE_MINING_SPEED, .2, 0);
        double plate = NativePartRates.derive(NativePartRates.Measure.HEAD_MINING_SPEED, 9, 10);
        assertEquals(1.2, shaft, 1e-9);
        assertEquals(.9, plate, 1e-9);
        assertEquals(1.035, DieselSettings.mechanicalRate(shaft, plate), 1e-9);
        assertEquals(1.05, PowergridPartSettings.generatorRate(shaft, plate), 1e-9);
        assertEquals(1.2, NativePartRates.derive(NativePartRates.Measure.HEAD_DURABILITY, 120, 100), 1e-9);
        // The same bounded structural ratio can approximate thermal response,
        // without a separate material->machine rating or an invented heat source.
        assertEquals(1, DieselSettings.mechanicalRate(1, 1), 1e-9);
        assertEquals(1, PowergridPartSettings.generatorRate(1, 1), 1e-9);
    }

    @Test void nativeWorkingFaceAndHandleApproximateFlightGasRailAndGyrofuge() {
        double blade = NativePartRates.derive(NativePartRates.Measure.HEAD_MINING_SPEED, 12, 10);
        double handle = NativePartRates.derive(NativePartRates.Measure.HANDLE_MINING_SPEED, -.1, 0);
        assertEquals(1.095, ClockworkPartSettings.workingRate(blade, handle), 1e-9);
        double gasPlate = NativePartRates.derive(NativePartRates.Measure.HEAD_MINING_SPEED, 8, 10);
        assertEquals(.835, ClockworkPartSettings.workingRate(gasPlate, handle), 1e-9);
        assertEquals(1.035, RailBeetlePartSettings.weightedRate(blade, handle), 1e-9);
        assertEquals(.9, BeesPartSettings.weakest(blade, handle), 1e-9);
        assertEquals(1, ClockworkPartSettings.workingRate(1, 1), 1e-9);
        assertEquals(1, RailBeetlePartSettings.weightedRate(1, 1), 1e-9);
    }

    @Test void savedEnginePairIsAuthoritativeAndMalformedNativeStatsFailClosed() {
        var saved = new MachineSettings(EurekaEngineSettings.OUTPUT,
            Map.of("drive_performance", 1.2, "consumption", 1.2),
            Map.of(EurekaEngineSettings.ROLE, "tconstruct:wood"), null);
        assertEquals(1.2, EurekaEngineSettings.rate(saved), 1e-9);
        var mismatched = new MachineSettings(EurekaEngineSettings.OUTPUT,
            Map.of("drive_performance", 1.2, "consumption", .9),
            Map.of(EurekaEngineSettings.ROLE, "tconstruct:wood"), null);
        assertEquals(1, EurekaEngineSettings.rate(mismatched), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> NativePartRates.derive(
            NativePartRates.Measure.HANDLE_MINING_SPEED, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> DieselSettings.mechanicalRate(Double.NaN, 1));
    }
}
