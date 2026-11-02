package com.bettercontent.manufacturing.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable provenance and recipe-issued access identities, never trusted performance numbers. */
public record MachineBuild(int version, String chassis, String function, Map<String, ComponentProvenance> components,
                           Set<String> qualifications) {
    public static final int VERSION = 2;
    public static final int MAX_ROLES = 16, MAX_QUALIFICATIONS = 16;
    public MachineBuild(int version, String chassis, String function, Map<String, ComponentProvenance> components) {
        this(version, chassis, function, components, Set.of());
    }
    public MachineBuild {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported build version: " + version);
        Identifiers.id(chassis); Identifiers.id(function);
        components = Map.copyOf(components); qualifications = Set.copyOf(qualifications);
        if (components.size() > MAX_ROLES) throw new IllegalArgumentException("Too many component roles");
        if (qualifications.size() > MAX_QUALIFICATIONS) throw new IllegalArgumentException("Too many qualifications");
        components.forEach((role, part) -> { Identifiers.role(role); java.util.Objects.requireNonNull(part); });
        qualifications.forEach(Identifiers::id);
        if (components.isEmpty() && !qualifications.isEmpty()) throw new IllegalArgumentException("Stock cannot carry qualifications");
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
        return new Replacement(new MachineBuild(version, chassis, function, next, qualifications), old);
    }
    public record Replacement(MachineBuild build, ComponentProvenance recovered) {}
}
