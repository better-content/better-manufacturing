package com.bettercontent.manufacturing.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Each numerical axis names exactly one component role and one supported property. */
public record MachineProfile(String id, String chassis, String function, Map<String, Role> roles,
                             List<Axis> axes, Map<String, Double> stockValues,
                             Set<String> requiredQualifications, List<AccessLimit> accessLimits) {
    public MachineProfile(String id, String chassis, String function, Map<String, Role> roles,
                          List<Axis> axes, Map<String, Double> stockValues) {
        this(id, chassis, function, roles, axes, stockValues, Set.of(), List.of());
    }
    /** Without this recipe-issued qualification, a stronger material cannot exceed the access ceiling. */
    public record AccessLimit(String qualification, String axis, double unqualifiedMax) {
        public AccessLimit {
            Identifiers.id(qualification); Identifiers.role(axis); Identifiers.finite(unqualifiedMax, "access ceiling");
        }
    }
    public Set<String> acceptedQualifications() {
        var result = new java.util.HashSet<>(requiredQualifications);
        accessLimits.forEach(limit -> result.add(limit.qualification()));
        return Set.copyOf(result);
    }
    public record Role(Set<String> parts, String statRole) {
        public Role {
            parts = Set.copyOf(parts);
            if (parts.isEmpty()) throw new IllegalArgumentException("Role has no eligible parts");
            parts.forEach(Identifiers::id); Identifiers.id(statRole);
        }
    }
    public record Axis(String name, String componentRole, MaterialEngineeringProfile.Stat stat, String trait,
                       double referenceInput, double referenceOutput, double min, double max) {
        public Axis {
            Identifiers.role(name); Identifiers.role(componentRole);
            if ((stat == null) == (trait == null)) throw new IllegalArgumentException("Select one stat OR one engineering trait");
            if (trait != null) Identifiers.role(trait);
            Identifiers.finite(referenceInput, "reference input"); Identifiers.finite(referenceOutput, "reference output");
            Identifiers.finite(min, "minimum"); Identifiers.finite(max, "maximum");
            if (min < 0 || min > referenceOutput || referenceOutput > max) throw new IllegalArgumentException("Invalid axis bounds");
            if (stat == null || stat.semantics() == MaterialEngineeringProfile.Semantics.BASE) {
                if (referenceInput <= 0) throw new IllegalArgumentException("Base reference must be positive");
            } else if (referenceInput <= -1) throw new IllegalArgumentException("Invalid modifier reference");
        }
        public double evaluate(double input) {
            Identifiers.finite(input, "input");
            if (stat == null && input < 0) throw new IllegalArgumentException("Negative engineering coefficient");
            double ratio = stat == null ? input / referenceInput : stat.relative(input, referenceInput);
            double result = referenceOutput * ratio;
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Axis overflow");
            return Math.max(min, Math.min(max, result));
        }
    }
    public MachineProfile {
        Identifiers.id(id); Identifiers.id(chassis); Identifiers.id(function);
        roles = Map.copyOf(roles); axes = List.copyOf(axes); stockValues = Map.copyOf(stockValues);
        requiredQualifications = Set.copyOf(requiredQualifications); accessLimits = List.copyOf(accessLimits);
        requiredQualifications.forEach(Identifiers::id);
        var qualificationIds = new java.util.HashSet<>(requiredQualifications);
        accessLimits.forEach(limit -> qualificationIds.add(limit.qualification()));
        if (qualificationIds.size() > MachineBuild.MAX_QUALIFICATIONS || accessLimits.size() > 64)
            throw new IllegalArgumentException("Too many access rules");
        if (roles.size() > MachineBuild.MAX_ROLES) throw new IllegalArgumentException("Too many roles");
        roles.keySet().forEach(Identifiers::role);
        var names = new java.util.HashSet<String>();
        for (Axis axis : axes) {
            if (!roles.containsKey(axis.componentRole()) || !names.add(axis.name())) throw new IllegalArgumentException("Unknown role or duplicate axis");
            if (axis.stat() != null && !roles.get(axis.componentRole()).statRole().equals(axis.stat().role())) throw new IllegalArgumentException("Axis uses unrelated stat role");
            Double stock = stockValues.get(axis.name());
            if (stock == null || !Double.isFinite(stock) || stock < axis.min() || stock > axis.max()) throw new IllegalArgumentException("Invalid stock value");
        }
        if (!stockValues.keySet().equals(names)) throw new IllegalArgumentException("Stock/evaluated axis mismatch");
        var limits = new java.util.HashSet<String>();
        for (var limit : accessLimits) {
            Axis axis = axes.stream().filter(candidate -> candidate.name().equals(limit.axis())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown access axis"));
            if (!limits.add(limit.qualification() + "/" + limit.axis()) || limit.unqualifiedMax() < axis.min() || limit.unqualifiedMax() > axis.max())
                throw new IllegalArgumentException("Invalid access ceiling");
        }
    }
}
