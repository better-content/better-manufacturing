package com.bettercontent.manufacturing.api;

import com.bettercontent.manufacturing.data.EngineeringPolicyJson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineeringPolicyTest {
    private static MaterialPolicy policy(boolean nativeStats) {
        return new MaterialPolicy("tconstruct:iron", Set.of("tconstruct:tool_handle"), nativeStats,
            Map.of("screen_rate", 1.0), Set.of("test:water"));
    }
    @Test void canonicalRoundTripIsImmutableAndNeverInventsMissingTraits() {
        var value = policy(true); String encoded = EngineeringPolicyJson.encode(Map.of(value.material(), value));
        var parsed = EngineeringPolicyJson.decode(encoded);
        assertEquals(Map.of(value.material(), value), parsed);
        assertEquals(encoded, EngineeringPolicyJson.encode(parsed));
        assertThrows(UnsupportedOperationException.class, () -> parsed.clear());
        assertFalse(parsed.get(value.material()).traits().containsKey("pressure_limit"));
        assertEquals(Set.of("test:water"), parsed.get(value.material()).compatibleProcesses());
    }
    @Test void nativeBindingIsExplicitAndCannotOverrideNativeValues() {
        var stat = new MaterialEngineeringProfile.Stat("tconstruct:handle", "mining_speed", MaterialEngineeringProfile.Semantics.ADDITIVE_MODIFIER);
        assertEquals(Map.of(stat, .1), policy(true).bind(Map.of(stat, .1)).stats());
        assertTrue(policy(false).bind(Map.of(stat, .1)).stats().isEmpty());
    }
    @Test void unknownFieldsDuplicateMaterialsAndNonfiniteTraitsReject() {
        var json = EngineeringPolicyJson.write(policy(true)); json.addProperty("mining_tier_as_pressure", 5);
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.read(json));
        String one = EngineeringPolicyJson.write(policy(true)).toString();
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[" + one + "," + one + "]"));
        assertThrows(IllegalArgumentException.class, () -> new MaterialPolicy("test:iron", Set.of("test:part"), true, Map.of("rate", Double.NaN), Set.of()));
        var textTrait = EngineeringPolicyJson.write(policy(true)); textTrait.getAsJsonObject("traits").addProperty("screen_rate", "1.0");
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.read(textTrait));
    }
    @Test void syntaxAndDuplicateFieldsCannotSilentlyRemoveRestrictions() {
        String one = EngineeringPolicyJson.write(policy(true)).toString();
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[" + one.replace("\"schema\":1", "\"schema\":1,\"schema\":1") + "]"));
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[" + one.replace("\"screen_rate\":1.0", "\"screen_rate\":0.75,\"screen_rate\":1.0") + "]"));
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[ /* skipped */ " + one + "]"));
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[" + one + "] false"));
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[{\"schema\":"));
    }
    @Test void payloadAndNestingAreBoundedBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode(" ".repeat(EngineeringPolicyJson.MAX_BYTES + 1)));
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.decode("[".repeat(20) + "]".repeat(20)));
        var json = EngineeringPolicyJson.write(policy(true));
        json.getAsJsonArray("supported_parts").add("tconstruct:tool_handle");
        var duplicate = json;
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.read(duplicate));
        json = EngineeringPolicyJson.write(policy(true)); json.addProperty("schema", 1.5);
        var invalidVersion = json;
        assertThrows(IllegalArgumentException.class, () -> EngineeringPolicyJson.read(invalidVersion));
    }
}
