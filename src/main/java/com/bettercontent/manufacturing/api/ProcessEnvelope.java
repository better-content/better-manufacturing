package com.bettercontent.manufacturing.api;

/** Pressure is the owning machine's gauge-bar domain; no pressure is invented for Create. */
public record ProcessEnvelope(String process, Range temperatureKelvin, Range gaugePressureBar, boolean containmentRequired) {
    public record Range(double min, double max) {
        public Range {
            Identifiers.finite(min, "minimum"); Identifiers.finite(max, "maximum");
            if (min > max) throw new IllegalArgumentException("Inverted operating range");
        }
        public boolean contains(double value) { return Double.isFinite(value) && value >= min && value <= max; }
    }
    public record Conditions(Double temperatureKelvin, Double gaugePressureBar, boolean contained) {}
    public ProcessEnvelope {
        Identifiers.id(process);
        if (temperatureKelvin != null && temperatureKelvin.min() < 0) throw new IllegalArgumentException("Below absolute zero");
    }
    public String blockedReason(Conditions state) {
        if (containmentRequired && !state.contained()) return "missing_containment";
        if (temperatureKelvin != null && (state.temperatureKelvin() == null || !temperatureKelvin.contains(state.temperatureKelvin()))) return "temperature_out_of_range";
        if (gaugePressureBar != null && (state.gaugePressureBar() == null || !gaugePressureBar.contains(state.gaugePressureBar()))) return "pressure_out_of_range";
        return "";
    }
}
