package com.bettercontent.manufacturing.api;

import java.util.Map;
import java.util.Set;

/** Authored gameplay engineering policy, not a replacement for native Tinkers stat authority. */
public record MaterialPolicy(String material, Set<String> supportedParts, boolean useNativeStats,
                             Map<String, Double> traits, Set<String> compatibleProcesses) {
    public MaterialPolicy {
        Identifiers.variant(material);
        supportedParts = Set.copyOf(supportedParts); traits = Map.copyOf(traits); compatibleProcesses = Set.copyOf(compatibleProcesses);
        if (supportedParts.isEmpty() || supportedParts.size() > 32 || traits.size() > 64 || compatibleProcesses.size() > 64)
            throw new IllegalArgumentException("Invalid engineering policy size");
        supportedParts.forEach(Identifiers::id); compatibleProcesses.forEach(Identifiers::id);
        traits.forEach((key, value) -> { Identifiers.role(key); Identifiers.finite(value, key); });
    }
    public MaterialEngineeringProfile bind(Map<MaterialEngineeringProfile.Stat, Double> nativeStats) {
        return new MaterialEngineeringProfile(material, supportedParts, useNativeStats ? nativeStats : Map.of(), traits, compatibleProcesses);
    }
}
