package com.bettercontent.manufacturing.api;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A stock consumed rotor must not be given a third rated term by v2 replay. */
class NumericEvaluatorPneupressureTest {
    private static final String OUTPUT = "pneumaticcraft:electrostatic_compressor";
    private static final String FUNCTION = "better_manufacturing:pneupressure/compression";

    private static NumericInputs inputs(Map<String, Double> rotor) {
        return new NumericInputs(FUNCTION, Map.of(
            "body", Map.of(Measure.HEAD_MINING_SPEED, new NumericInputs.Stat(4, 6),
                Measure.HEAD_DURABILITY, new NumericInputs.Stat(130, 250)),
            "handle", Map.of(Measure.HANDLE_MINING_SPEED, new NumericInputs.Stat(-.15, 0))),
            Map.of("rotor", rotor));
    }

    @Test void stockRotorUsesNativeTwoPartFormulaThroughV2Save() {
        var numeric = inputs(Map.of("baseline", 1.0));
        var values = NumericEvaluator.evaluate(OUTPUT, numeric, null);
        assertEquals(Math.sqrt(.75 * .85), values.get("compression_rate"));
        assertEquals(.75, values.get("pressure_envelope"));
        var holder = new CompoundTag();
        SettingsNbt.write(holder, new MachineSettings(OUTPUT, values, Map.of("body", "tconstruct:slimewood"), null, numeric));
        assertEquals(values, SettingsNbt.read(holder, OUTPUT).properties());
    }

    @Test void ratedRotorUsesNativeThreePartFormulaThroughV2Save() {
        var numeric = inputs(Map.of("rotation_rate", 1.2));
        var values = NumericEvaluator.evaluate(OUTPUT, numeric, null);
        assertEquals(Math.cbrt(.75 * .85 * 1.2), values.get("compression_rate"));
        assertNotEquals(NumericEvaluator.evaluate(OUTPUT, inputs(Map.of("baseline", 1.0)), null)
            .get("compression_rate"), values.get("compression_rate"));
        var holder = new CompoundTag();
        SettingsNbt.write(holder, new MachineSettings(OUTPUT, values, Map.of("body", "tconstruct:slimewood"), null, numeric));
        assertEquals(values, SettingsNbt.read(holder, OUTPUT).properties());
    }
}
