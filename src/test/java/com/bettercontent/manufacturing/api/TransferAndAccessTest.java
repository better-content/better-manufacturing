package com.bettercontent.manufacturing.api;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferAndAccessTest {
    private static final ComponentProvenance HEAD = new ComponentProvenance("tconstruct:pick_head", "tconstruct:iron", "tconstruct:head", 180, true, Map.of());
    private static final String CHASSIS = "test:machine", FUNCTION = "test:work", ACCESS = "test:meteor_logistics";
    private static MachineBuild build(Set<String> qualifications) { return new MachineBuild(MachineBuild.VERSION, CHASSIS, FUNCTION, Map.of("head", HEAD), qualifications); }
    private static TransferPlan simple() { return new TransferPlan(List.of(new TransferPlan.Move(0, "part", "head")), List.of(), Set.of()); }

    @Test void retoolKeepsAccessButStockCannotFabricateIt() {
        assertEquals(Set.of(ACCESS), build(Set.of(ACCESS)).replace("head", HEAD, true).build().qualifications());
        assertThrows(IllegalArgumentException.class, () -> new MachineBuild(MachineBuild.VERSION, CHASSIS, FUNCTION, Map.of(), Set.of(ACCESS)));
    }
    @Test void strongerMaterialDoesNotBypassProgressionAndStockRemainsNative() {
        var axis = new MachineProfile.Axis("rate", "head", null, "working_rate", 1, 1, .75, 1.5);
        var profile = new MachineProfile("test:profile", CHASSIS, FUNCTION,
            Map.of("head", new MachineProfile.Role(Set.of(HEAD.item()), HEAD.statRole())), List.of(axis), Map.of("rate", 1.0),
            Set.of(), List.of(new MachineProfile.AccessLimit(ACCESS, "rate", 1.0)));
        var material = new MaterialEngineeringProfile(HEAD.materialVariant(), Set.of(HEAD.item()), Map.of(), Map.of("working_rate", 2.0), Set.of());
        var materials = Map.of(HEAD.materialVariant(), material);
        assertEquals(1.0, EngineeringEvaluator.evaluate(build(Set.of()), profile, materials).values().get("rate"));
        assertEquals(1.5, EngineeringEvaluator.evaluate(build(Set.of(ACCESS)), profile, materials).values().get("rate"));
        assertEquals("unknown_qualification", EngineeringEvaluator.evaluate(build(Set.of("test:unissued")), profile, materials).blockedReason());
        var gated = new MachineProfile(profile.id(), CHASSIS, FUNCTION, profile.roles(), profile.axes(), profile.stockValues(), Set.of(ACCESS), List.of());
        assertEquals("missing_qualification", EngineeringEvaluator.evaluate(build(Set.of()), gated, materials).blockedReason());
        assertTrue(EngineeringEvaluator.evaluate(MachineBuild.stock(CHASSIS, FUNCTION), gated, Map.of()).allowed());
    }
    @Test void accessLimitsCannotInventAxesOrExceedBounds() {
        var roles = Map.of("head", new MachineProfile.Role(Set.of(HEAD.item()), HEAD.statRole()));
        var axes = List.of(new MachineProfile.Axis("rate", "head", null, "working_rate", 1, 1, .75, 1.5));
        for (var limit : List.of(new MachineProfile.AccessLimit(ACCESS, "other", 1), new MachineProfile.AccessLimit(ACCESS, "rate", 9)))
            assertThrows(IllegalArgumentException.class, () -> new MachineProfile("test:p", CHASSIS, FUNCTION, roles, axes, Map.of("rate", 1.0), Set.of(), List.of(limit)));
    }
    @Test void nativePartTransfersAreExactAndInputsStayImmutable() {
        var source = TransferPlan.Source.part(HEAD);
        var output = simple().apply(CHASSIS, FUNCTION, Map.of(0, source), 2);
        assertEquals(90, output.components().get("head").materialQuanta());
        assertFalse(output.components().get("head").wholePart());
        assertEquals(180, source.components().get("part").materialQuanta());
        assertThrows(IllegalArgumentException.class, () -> simple().apply(CHASSIS, FUNCTION, Map.of(0, source), 7));
    }
    @Test void duplicateSourcesAndOverwritesAreNotTransfers() {
        assertThrows(IllegalArgumentException.class, () -> new TransferPlan(List.of(new TransferPlan.Move(0, "part", "head"), new TransferPlan.Move(0, "part", "liner")), List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new TransferPlan(List.of(new TransferPlan.Move(0, "part", "head"), new TransferPlan.Move(1, "part", "head")), List.of(), Set.of()));
    }
    @Test void subassembliesCannotSilentlyLoseComponentsOrQualifications() {
        var source = TransferPlan.Source.build(build(Set.of(ACCESS)));
        var missingAccess = new TransferPlan(List.of(new TransferPlan.Move(0, "head", "head")), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> missingAccess.apply(CHASSIS, FUNCTION, Map.of(0, source), 1));
        var full = new TransferPlan(missingAccess.moves(), List.of(new TransferPlan.QualificationMove(0, ACCESS)), Set.of());
        assertEquals(build(Set.of(ACCESS)), full.apply(CHASSIS, FUNCTION, Map.of(0, source), 1));
        assertThrows(IllegalArgumentException.class, () -> full.apply(CHASSIS, FUNCTION, Map.of(0, source), 2));
        var extraPart = new TransferPlan.Source(Map.of("head", HEAD, "liner", HEAD), source.qualifications());
        assertThrows(IllegalArgumentException.class, () -> full.apply(CHASSIS, FUNCTION, Map.of(0, extraPart), 1));
    }
    @Test void sourceSlotsMustExactlyMatchAndStatefulPartsCannotBeDivided() {
        assertThrows(IllegalArgumentException.class, () -> simple().apply(CHASSIS, FUNCTION, Map.of(1, TransferPlan.Source.part(HEAD)), 1));
        var worn = new ComponentProvenance(HEAD.item(), HEAD.materialVariant(), HEAD.statRole(), HEAD.materialQuanta(), true, Map.of("test:wear", "23"));
        assertThrows(IllegalArgumentException.class, () -> simple().apply(CHASSIS, FUNCTION, Map.of(0, TransferPlan.Source.part(worn)), 2));
        assertEquals(worn, simple().apply(CHASSIS, FUNCTION, Map.of(0, TransferPlan.Source.part(worn)), 1).components().get("head"));
    }
    @Test void vacuumNeedsAnOperatingConnectedNamedPumpAndRecoveryTrap() {
        var envelope = new ProcessEnvelope("test:lactide", null, null, true, "test:plant",
            new ProcessEnvelope.VacuumRequirement("test:pump", new ProcessEnvelope.Range(-.9, -.5), true));
        var good = new ProcessEnvelope.VacuumState("test:pump", true, true, true, -.7);
        assertEquals("", envelope.blockedReason(new ProcessEnvelope.Conditions(null, null, true, "test:plant", good)));
        assertEquals("missing_vacuum_source", envelope.blockedReason(new ProcessEnvelope.Conditions(null, -.7, true, "test:plant", null)));
        assertEquals("wrong_condition_source", envelope.blockedReason(new ProcessEnvelope.Conditions(null, null, true, "test:other", good)));
        for (var invalid : List.of(new ProcessEnvelope.VacuumState("test:pump", false, true, true, -.7),
                new ProcessEnvelope.VacuumState("test:pump", true, false, true, -.7),
                new ProcessEnvelope.VacuumState("test:pump", true, true, false, -.7),
                new ProcessEnvelope.VacuumState("test:pump", true, true, true, 5.0),
                new ProcessEnvelope.VacuumState("test:pump", true, true, true, Double.NaN),
                new ProcessEnvelope.VacuumState("test:other", true, true, true, -.7)))
            assertFalse(envelope.blockedReason(new ProcessEnvelope.Conditions(null, null, true, "test:plant", invalid)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ProcessEnvelope.VacuumRequirement("test:pump", new ProcessEnvelope.Range(1, 5), false));
    }
}
