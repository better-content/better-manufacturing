package com.bettercontent.manufacturing.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Numeric replay of native Clockwork segment lengths, independent of any world or saw fixture. */
class ClockworkBladeInheritanceTest {
    private static final String OUTPUT = "vs_clockwork:propeller_blade";
    private static final String FUNCTION = "better_manufacturing:inherit/actuator_response";

    private static NumericInputs segments(Map<String, Map<String, Double>> components) {
        return new NumericInputs(FUNCTION, Map.of(), components);
    }

    @Test void weightedLengthIsPreservedAndBoundedForBothNativeBladeKinds() {
        var inputs = segments(Map.of(
            "blade_0", Map.of("actuator_response", .75, "blade_length", 1d),
            "blade_1", Map.of("actuator_response", 1.5, "blade_length", 3d)));
        for (String output : new String[] {OUTPUT, "vs_clockwork:wide_propeller_blade"}) {
            assertEquals(1.3125, NumericEvaluator.evaluate(output, inputs, null).get("actuator_response"), 1e-12);
        }
    }

    @Test void malformedLengthCannotTurnIntoDefaultStockRating() {
        for (double length : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> NumericEvaluator.evaluate(OUTPUT,
                segments(Map.of("blade_0", Map.of("actuator_response", 1d, "blade_length", length))), null));
        }
        assertThrows(IllegalArgumentException.class, () -> NumericEvaluator.evaluate(OUTPUT,
            segments(Map.of("blade_0", Map.of("actuator_response", 1d))), null));
        assertThrows(IllegalArgumentException.class, () -> NumericEvaluator.evaluate("minecraft:stick",
            segments(Map.of("blade_0", Map.of("actuator_response", 1d, "blade_length", 1d))), null));
    }
}
