package com.bettercontent.manufacturing.api;

/** Native gauge-bar domain, not an invented Create pressure or an SI-vacuum conversion. */
public record ProcessEnvelope(String process, Range temperatureKelvin, Range gaugePressureBar, boolean containmentRequired,
                              String conditionSource, VacuumRequirement vacuum) {
    public ProcessEnvelope(String process, Range temperatureKelvin, Range gaugePressureBar, boolean containmentRequired) {
        this(process, temperatureKelvin, gaugePressureBar, containmentRequired, null, null);
    }
    public record Range(double min, double max) {
        public Range {
            Identifiers.finite(min, "minimum"); Identifiers.finite(max, "maximum");
            if (min > max) throw new IllegalArgumentException("Inverted operating range");
        }
        public boolean contains(double value) { return Double.isFinite(value) && value >= min && value <= max; }
    }
    /** Supplied only by an adapter that checked the physical pump connection and native operation. */
    public record VacuumState(String source, boolean connected, boolean pumping, boolean trapConnected, Double gaugePressureBar) {
        public VacuumState { Identifiers.id(source); }
    }
    public record VacuumRequirement(String source, Range gaugePressureBar, boolean trapRequired) {
        public VacuumRequirement {
            Identifiers.id(source); java.util.Objects.requireNonNull(gaugePressureBar);
            if (gaugePressureBar.max() >= 0) throw new IllegalArgumentException("Vacuum requires a negative native gauge range");
        }
        public String blockedReason(VacuumState state) {
            if (state == null || !source.equals(state.source())) return "missing_vacuum_source";
            if (!state.connected() || !state.pumping()) return "vacuum_not_operating";
            if (trapRequired && !state.trapConnected()) return "missing_vacuum_trap";
            if (state.gaugePressureBar() == null || !gaugePressureBar.contains(state.gaugePressureBar())) return "vacuum_out_of_range";
            return "";
        }
    }
    public record Conditions(Double temperatureKelvin, Double gaugePressureBar, boolean contained,
                             String source, VacuumState vacuum) {
        public Conditions(Double temperatureKelvin, Double gaugePressureBar, boolean contained) {
            this(temperatureKelvin, gaugePressureBar, contained, null, null);
        }
        public Conditions { if (source != null) Identifiers.id(source); }
    }
    public ProcessEnvelope {
        Identifiers.id(process);
        if (conditionSource != null) Identifiers.id(conditionSource);
        if (temperatureKelvin != null && temperatureKelvin.min() < 0) throw new IllegalArgumentException("Below absolute zero");
        if (vacuum != null && conditionSource == null) throw new IllegalArgumentException("Vacuum process needs a native condition source");
    }
    public String blockedReason(Conditions state) {
        java.util.Objects.requireNonNull(state);
        if (conditionSource != null && !conditionSource.equals(state.source())) return "wrong_condition_source";
        if (containmentRequired && !state.contained()) return "missing_containment";
        if (temperatureKelvin != null && (state.temperatureKelvin() == null || !temperatureKelvin.contains(state.temperatureKelvin()))) return "temperature_out_of_range";
        if (gaugePressureBar != null && (state.gaugePressureBar() == null || !gaugePressureBar.contains(state.gaugePressureBar()))) return "pressure_out_of_range";
        return vacuum == null ? "" : vacuum.blockedReason(state.vacuum());
    }
}
