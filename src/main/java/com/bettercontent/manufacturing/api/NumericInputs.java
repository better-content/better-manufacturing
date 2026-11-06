package com.bettercontent.manufacturing.api;

import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import java.util.Map;

/** Immutable crafting-time numerical evidence. Native material registries are never needed to read it. */
public record NumericInputs(String function,
                            Map<String, Map<NativePartRates.Measure, Stat>> parts,
                            Map<String, Map<String, Double>> components) {
    public record Stat(double selected, double reference) {
        public Stat {
            if (!Double.isFinite(selected) || !Double.isFinite(reference))
                throw new IllegalArgumentException("Nonfinite captured Tinkers stat");
        }
        public double rate(NativePartRates.Measure measure) {
            return NativePartRates.derive(measure, selected, reference);
        }
    }
    public NumericInputs {
        Identifiers.id(function);
        if (parts.size() > 9 || components.size() > 9 || parts.size() + components.size() > 9)
            throw new IllegalArgumentException("Too many saved inputs");
        parts = Map.copyOf(parts.entrySet().stream().collect(java.util.stream.Collectors.toMap(entry -> {
            Identifiers.role(entry.getKey());
            return entry.getKey();
        }, entry -> {
            if (entry.getValue().isEmpty() || entry.getValue().size() > 10)
                throw new IllegalArgumentException("Invalid part stat count");
            return Map.copyOf(entry.getValue());
        })));
        components = Map.copyOf(components.entrySet().stream().collect(java.util.stream.Collectors.toMap(entry -> {
            Identifiers.role(entry.getKey());
            return entry.getKey();
        }, entry -> {
            if (entry.getValue().isEmpty() || entry.getValue().size() > 16)
                throw new IllegalArgumentException("Invalid component property count");
            entry.getValue().forEach((name, value) -> {
                Identifiers.role(name);
                if (value == null || !Double.isFinite(value) || value < 0 || value > 1_000_000)
                    throw new IllegalArgumentException("Invalid saved component number");
            });
            return Map.copyOf(entry.getValue());
        })));
        if (parts.isEmpty() && components.isEmpty() || !java.util.Collections.disjoint(parts.keySet(), components.keySet()))
            throw new IllegalArgumentException("Invalid saved input roles");
    }
    public double rate(String role, NativePartRates.Measure measure) {
        var stats = parts.get(role);
        if (stats == null || !stats.containsKey(measure)) throw new IllegalArgumentException("Missing saved stat: " + role + "/" + measure);
        return stats.get(measure).rate(measure);
    }
    public double component(String role, String property) {
        var properties = components.get(role);
        if (properties == null) throw new IllegalArgumentException("Missing saved component: " + role);
        return properties.getOrDefault(property, 1.0);
    }
}
