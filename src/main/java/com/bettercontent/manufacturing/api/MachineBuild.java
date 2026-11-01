package com.bettercontent.manufacturing.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, bounded flat provenance. An absent record is NOT a crafted reference build. */
public record MachineBuild(int version, String chassis, String function, Map<String, ComponentProvenance> components) {
    public static final int VERSION = 1;
    public static final int MAX_ROLES = 16;
    public MachineBuild {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported build version: " + version);
        Identifiers.id(chassis);
        Identifiers.id(function);
        components = Map.copyOf(components);
        if (components.size() > MAX_ROLES) throw new IllegalArgumentException("Too many component roles");
        components.forEach((role, part) -> { Identifiers.role(role); java.util.Objects.requireNonNull(part); });
    }
    public boolean hasProvenance() { return !components.isEmpty(); }
    public static MachineBuild stock(String chassis, String function) {
        return new MachineBuild(VERSION, chassis, function, Map.of());
    }
    public Replacement replace(String role, ComponentProvenance replacement, boolean emptyAndStopped) {
        if (!emptyAndStopped) throw new IllegalArgumentException("Retooling requires an empty stopped machine");
        if (!components.containsKey(role)) throw new IllegalArgumentException("No attributable component in role " + role);
        var next = new LinkedHashMap<>(components);
        var old = next.put(role, replacement);
        return new Replacement(new MachineBuild(version, chassis, function, next), old);
    }
    public record Replacement(MachineBuild build, ComponentProvenance recovered) {}
}
