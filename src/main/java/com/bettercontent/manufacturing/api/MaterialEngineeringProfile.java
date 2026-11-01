package com.bettercontent.manufacturing.api;

import java.util.Map;
import java.util.Set;

/** Explicit properties for one material variant. Missing properties have no inferred fallback. */
public record MaterialEngineeringProfile(String materialVariant, Set<String> supportedParts,
        Map<Stat, Double> stats, Map<String, Double> engineeringTraits, Set<String> compatibleProcesses) {
    public enum Semantics { BASE, ADDITIVE_MODIFIER }
    public record Stat(String role, String name, Semantics semantics) {
        public Stat { Identifiers.id(role); Identifiers.role(name); java.util.Objects.requireNonNull(semantics); }
        public double relative(double value, double reference) {
            Identifiers.finite(value, "stat"); Identifiers.finite(reference, "reference");
            if (semantics == Semantics.ADDITIVE_MODIFIER) {
                if (value <= -1 || reference <= -1) throw new IllegalArgumentException("Invalid additive modifier");
                return (1 + value) / (1 + reference);
            }
            if (value < 0 || reference <= 0) throw new IllegalArgumentException("Invalid base stat or reference");
            return value / reference;
        }
    }
    public MaterialEngineeringProfile {
        Identifiers.variant(materialVariant);
        supportedParts = Set.copyOf(supportedParts);
        supportedParts.forEach(Identifiers::id);
        stats = Map.copyOf(stats);
        stats.forEach((key, value) -> { java.util.Objects.requireNonNull(key); Identifiers.finite(value, key.name()); });
        engineeringTraits = Map.copyOf(engineeringTraits);
        engineeringTraits.forEach((key, value) -> { Identifiers.role(key); Identifiers.finite(value, key); });
        compatibleProcesses = Set.copyOf(compatibleProcesses);
        compatibleProcesses.forEach(Identifiers::id);
    }
}
