package com.bettercontent.manufacturing.api;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Semantics.*;

class ManufacturingContractsTest {
    private static final String PICK = "tconstruct:pick_head", IRON = "tconstruct:iron", HEAD = "tconstruct:head";
    private static final String DRILL = "create:mechanical_drill", FUNCTION = "better_manufacturing:cutting";
    private static final MaterialEngineeringProfile.Stat SPEED = new MaterialEngineeringProfile.Stat(HEAD, "mining_speed", BASE);
    private static ComponentProvenance part(String material, long amount) { return new ComponentProvenance(PICK, material, HEAD, amount, true, Map.of()); }
    private static MachineBuild build(String material) { return new MachineBuild(1, DRILL, FUNCTION, Map.of("head", part(material, 180))); }
    private static MaterialEngineeringProfile material(double speed) {
        return new MaterialEngineeringProfile(IRON, Set.of(PICK), Map.of(SPEED, speed), Map.of("pressure_limit", 5.0), Set.of("test:water"));
    }
    private static MachineProfile profile() {
        return new MachineProfile("test:drill", DRILL, FUNCTION, Map.of("head", new MachineProfile.Role(Set.of(PICK), HEAD)),
            List.of(new MachineProfile.Axis("rate", "head", SPEED, null, 6, 1, .75, 1.5)), Map.of("rate", 1.0));
    }
    @Test void rateUsesReferenceAndCaps() {
        assertEquals(1.0, EngineeringEvaluator.evaluate(build(IRON), profile(), Map.of(IRON, material(6))).values().get("rate"));
        assertEquals(1.5, EngineeringEvaluator.evaluate(build(IRON), profile(), Map.of(IRON, material(100))).values().get("rate"));
        assertEquals(.75, EngineeringEvaluator.evaluate(build(IRON), profile(), Map.of(IRON, material(1))).values().get("rate"));
    }
    @Test void modifierZeroMeansOneNotZero() {
        var stat = new MaterialEngineeringProfile.Stat("tconstruct:handle", "mining_speed", ADDITIVE_MODIFIER);
        assertEquals(1, stat.relative(0, 0));
        assertEquals(1.1, stat.relative(.1, 0), 1e-12);
        assertEquals(1, stat.relative(.1, .1), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> stat.relative(-1, 0));
    }
    @Test void baseAndModifierAreNotInterchangeable() {
        assertEquals(.5, SPEED.relative(3, 6));
        assertThrows(IllegalArgumentException.class, () -> SPEED.relative(2, 0));
        assertThrows(IllegalArgumentException.class, () -> SPEED.relative(Double.NaN, 6));
    }
    @Test void unknownMaterialNeverUpgradesToStock() {
        var result = EngineeringEvaluator.evaluate(build("test:missing"), profile(), Map.of(IRON, material(6)));
        assertFalse(result.allowed()); assertTrue(result.values().isEmpty());
    }
    @Test void materialVariantIdentityIsNotCollapsed() {
        var result = EngineeringEvaluator.evaluate(build("tconstruct:iron#special"), profile(), Map.of(IRON, material(6)));
        assertFalse(result.allowed());
    }
    @Test void stockIsUsefulButHasNoSalvageCredit() {
        var result = EngineeringEvaluator.evaluate(MachineBuild.stock(DRILL, FUNCTION), profile(), Map.of());
        assertTrue(result.allowed()); assertEquals(1, result.values().get("rate")); assertFalse(result.recoverable());
    }
    @Test void wrongChassisIsNotStock() {
        assertFalse(EngineeringEvaluator.evaluate(MachineBuild.stock("test:other", FUNCTION), profile(), Map.of()).allowed());
    }
    @Test void missingRoleDoesNotFallback() {
        var b = new MachineBuild(1, DRILL, FUNCTION, Map.of("liner", part(IRON, 90)));
        assertFalse(EngineeringEvaluator.evaluate(b, profile(), Map.of(IRON, material(6))).allowed());
    }
    @Test void unsupportedPartAndMissingStatAreBlocked() {
        var wrong = new MaterialEngineeringProfile(IRON, Set.of("tconstruct:large_plate"), Map.of(SPEED, 6.0), Map.of(), Set.of());
        assertFalse(EngineeringEvaluator.evaluate(build(IRON), profile(), Map.of(IRON, wrong)).allowed());
        var missing = new MaterialEngineeringProfile(IRON, Set.of(PICK), Map.of(), Map.of(), Set.of());
        assertFalse(EngineeringEvaluator.evaluate(build(IRON), profile(), Map.of(IRON, missing)).allowed());
    }
    @Test void malformedInputsFailAtBoundary() {
        assertThrows(IllegalArgumentException.class, () -> part(IRON, 0));
        assertThrows(IllegalArgumentException.class, () -> part("minecraft:iron#", 1));
        assertThrows(IllegalArgumentException.class, () -> new MachineBuild(99, DRILL, FUNCTION, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> material(Double.POSITIVE_INFINITY));
        var roles = new HashMap<String, ComponentProvenance>();
        for (int i = 0; i < 17; i++) roles.put("role_" + i, part(IRON, 90));
        assertThrows(IllegalArgumentException.class, () -> new MachineBuild(1, DRILL, FUNCTION, roles));
    }
    @Test void batchCreditIsExactAndNotAWholePlatePerOutput() {
        var input = part(IRON, 360);
        var perOutput = input.divide(8);
        assertEquals(45, perOutput.materialQuanta()); assertFalse(perOutput.wholePart());
        assertEquals(input.materialQuanta(), perOutput.materialQuanta() * 8);
        assertThrows(IllegalArgumentException.class, () -> input.divide(7));
        assertThrows(IllegalArgumentException.class, () -> input.divide(0));
    }
    @Test void statefulPartsCannotBeSplitOrHaveWearReset() {
        var worn = new ComponentProvenance(PICK, IRON, HEAD, 180, true, Map.of("better_compat_fixes:jackhammer_wear", "37"));
        assertThrows(IllegalArgumentException.class, () -> worn.divide(2));
        var b = new MachineBuild(1, DRILL, FUNCTION, Map.of("head", worn));
        var changed = b.replace("head", part("tconstruct:copper", 180), true);
        assertEquals(worn, changed.recovered()); assertEquals("37", changed.recovered().retainedData().get("better_compat_fixes:jackhammer_wear"));
    }
    @Test void retoolRejectsOccupiedAndUnattributedRoles() {
        assertThrows(IllegalArgumentException.class, () -> build(IRON).replace("head", part(IRON, 180), false));
        assertThrows(IllegalArgumentException.class, () -> MachineBuild.stock(DRILL, FUNCTION).replace("head", part(IRON, 180), true));
    }
    @Test void retoolPreservesOtherRolesAndReturnsOnlyRemovedPart() {
        var first = part(IRON, 180); var second = part("tconstruct:copper", 360);
        var b = new MachineBuild(1, DRILL, FUNCTION, Map.of("head", first, "liner", second));
        var result = b.replace("head", part("tconstruct:steel", 180), true);
        assertEquals(first, result.recovered()); assertEquals(second, result.build().components().get("liner"));
        assertEquals(first, b.components().get("head"));
    }
    @Test void vacuumIsARangeNotAMinimumPressureCheck() {
        var process = new ProcessEnvelope("test:vacuum", new ProcessEnvelope.Range(350, 450), new ProcessEnvelope.Range(-.9, -.5), true);
        assertEquals("", process.blockedReason(new ProcessEnvelope.Conditions(400.0, -.7, true)));
        assertEquals("pressure_out_of_range", process.blockedReason(new ProcessEnvelope.Conditions(400.0, 5.0, true)));
        assertEquals("missing_containment", process.blockedReason(new ProcessEnvelope.Conditions(400.0, -.7, false)));
        assertEquals("temperature_out_of_range", process.blockedReason(new ProcessEnvelope.Conditions(500.0, -.7, true)));
    }
    @Test void createDoesNotAcquireAnInventedPressureRequirement() {
        var p = new ProcessEnvelope("test:mixing", null, null, false);
        assertEquals("", p.blockedReason(new ProcessEnvelope.Conditions(null, null, false)));
    }
    @Test void requiredMeasurementsCannotBeUnknownOrNan() {
        var p = new ProcessEnvelope("test:heat", new ProcessEnvelope.Range(300, 400), null, false);
        assertFalse(p.blockedReason(new ProcessEnvelope.Conditions(null, null, false)).isEmpty());
        assertFalse(p.blockedReason(new ProcessEnvelope.Conditions(Double.NaN, null, false)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ProcessEnvelope.Range(400, 300));
    }
    @Test void weakestComponentLimitsAssembly() {
        var strong = new MaterialEngineeringProfile("tconstruct:steel", Set.of(PICK), Map.of(), Map.of("pressure_limit", 20.0), Set.of());
        assertEquals(5, EngineeringEvaluator.weakestLimit(List.of(material(6), strong), "pressure_limit"));
        assertThrows(IllegalArgumentException.class, () -> EngineeringEvaluator.weakestLimit(List.of(strong), "temperature_limit"));
        assertThrows(IllegalArgumentException.class, () -> EngineeringEvaluator.weakestLimit(List.of(), "pressure_limit"));
    }
    @Test void compatibilityChecksOnlyExplicitRoles() {
        var b = new MachineBuild(1, DRILL, FUNCTION, Map.of("head", part(IRON, 180), "grip", part("test:unknown", 90)));
        assertEquals("", EngineeringEvaluator.incompatibleRole(b, List.of("head"), "test:water", Map.of(IRON, material(6))));
        assertFalse(EngineeringEvaluator.incompatibleRole(b, List.of("head"), "test:acid", Map.of(IRON, material(6))).isEmpty());
    }
    @Test void cacheReusesAndInvalidatesWithoutWorldScan() {
        var cache = new ProfileCache<Integer>(2); var loads = new AtomicInteger();
        assertEquals(1, cache.resolve(build(IRON), "test:drill", 1, loads::incrementAndGet));
        assertEquals(1, cache.resolve(build(IRON), "test:drill", 1, loads::incrementAndGet));
        assertEquals(2, cache.resolve(build(IRON), "test:drill", 2, loads::incrementAndGet));
        cache.resolve(build("tconstruct:copper"), "test:drill", 2, loads::incrementAndGet);
        cache.resolve(build("tconstruct:steel"), "test:drill", 2, loads::incrementAndGet);
        assertEquals(2, cache.size());
        assertEquals(5, cache.resolve(build(IRON), "test:drill", 2, loads::incrementAndGet));
    }
    @Test void dataIsDefensivelyCopied() {
        var roles = new HashMap<String, ComponentProvenance>(); roles.put("head", part(IRON, 180));
        var b = new MachineBuild(1, DRILL, FUNCTION, roles); roles.clear();
        assertEquals(1, b.components().size());
        assertThrows(UnsupportedOperationException.class, () -> b.components().clear());
    }
    @Test void malformedProfilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MachineProfile.Axis("rate", "head", SPEED, "rate", 6, 1, .75, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new MachineProfile.Axis("rate", "head", SPEED, null, 0, 1, .75, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new ComponentProvenance(PICK, IRON, HEAD, 180, true, Map.of("test:value", "x".repeat(3000))));
    }
}
