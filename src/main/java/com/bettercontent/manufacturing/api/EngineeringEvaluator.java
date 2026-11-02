package com.bettercontent.manufacturing.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EngineeringEvaluator {
    public record Evaluation(Map<String, Double> values, String blockedReason, boolean recoverable) {
        public Evaluation { values = Map.copyOf(values); }
        public boolean allowed() { return blockedReason.isEmpty(); }
        static Evaluation blocked(String reason) { return new Evaluation(Map.of(), reason, false); }
    }
    private EngineeringEvaluator() {}
    public static Evaluation evaluate(MachineBuild build, MachineProfile machine,
                                      Map<String, MaterialEngineeringProfile> materials) {
        if (!build.chassis().equals(machine.chassis()) || !build.function().equals(machine.function())) return Evaluation.blocked("chassis_mismatch");
        if (!build.hasProvenance()) return new Evaluation(machine.stockValues(), "", false);
        if (!machine.acceptedQualifications().containsAll(build.qualifications())) return Evaluation.blocked("unknown_qualification");
        if (!build.qualifications().containsAll(machine.requiredQualifications())) return Evaluation.blocked("missing_qualification");
        if (!build.components().keySet().equals(machine.roles().keySet())) return Evaluation.blocked("component_roles_mismatch");
        for (var entry : machine.roles().entrySet()) {
            var part = build.components().get(entry.getKey());
            var material = materials.get(part.materialVariant());
            if (material == null || !material.materialVariant().equals(part.materialVariant())) return Evaluation.blocked("unknown_material:" + part.materialVariant());
            if (!entry.getValue().parts().contains(part.item()) || !material.supportedParts().contains(part.item())
                    || !entry.getValue().statRole().equals(part.statRole())) return Evaluation.blocked("unsupported_part:" + entry.getKey());
        }
        var values = new LinkedHashMap<String, Double>();
        for (var axis : machine.axes()) {
            var part = build.components().get(axis.componentRole());
            var material = materials.get(part.materialVariant());
            Double input = axis.stat() == null ? material.engineeringTraits().get(axis.trait()) : material.stats().get(axis.stat());
            if (input == null) return Evaluation.blocked("missing_property:" + axis.componentRole() + ":" + axis.name());
            try {
                double value = axis.evaluate(input);
                for (var limit : machine.accessLimits()) {
                    if (limit.axis().equals(axis.name()) && !build.qualifications().contains(limit.qualification()))
                        value = Math.min(value, limit.unqualifiedMax());
                }
                values.put(axis.name(), value);
            }
            catch (IllegalArgumentException invalid) { return Evaluation.blocked("invalid_property:" + axis.name()); }
        }
        return new Evaluation(values, "", true);
    }
    /** Explicitly listed wetted roles only: an exterior grip must not veto an acid process. */
    public static String incompatibleRole(MachineBuild build, List<String> wettedRoles, String process,
                                         Map<String, MaterialEngineeringProfile> materials) {
        Identifiers.id(process);
        for (String role : wettedRoles) {
            var part = build.components().get(role);
            if (part == null) return "missing_component:" + role;
            var material = materials.get(part.materialVariant());
            if (material == null || !material.compatibleProcesses().contains(process)) return "incompatible_process:" + role;
        }
        return "";
    }
    public static double weakestLimit(List<MaterialEngineeringProfile> materials, String trait) {
        if (materials.isEmpty()) throw new IllegalArgumentException("Empty assembly has no limit");
        double limit = Double.POSITIVE_INFINITY;
        for (var material : materials) {
            Double value = material.engineeringTraits().get(trait);
            if (value == null || value < 0) throw new IllegalArgumentException("Missing/invalid assembly limit: " + trait);
            limit = Math.min(limit, value);
        }
        return limit;
    }
}
