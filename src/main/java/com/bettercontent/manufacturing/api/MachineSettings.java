package com.bettercontent.manufacturing.api;

import java.util.Map;

/** A crafting-time result, not an inventory of the parts used to make it. */
public record MachineSettings(String output, Map<String, Double> properties, Map<String, String> materials, String harvestTier) {
    public static final int VERSION = 1;
    public MachineSettings {
        Identifiers.id(output);
        properties = Map.copyOf(properties); materials = Map.copyOf(materials);
        if (properties.size() > 16 || materials.size() > 16) throw new IllegalArgumentException("Too many settings");
        properties.forEach((key, value) -> {
            Identifiers.role(key); Identifiers.finite(value, key);
            if (value < 0 || value > 1_000_000) throw new IllegalArgumentException("Invalid setting");
            if ((key.equals("work_rate") || key.equals("airflow") || key.equals("pressure")
                    || key.equals("drive_rate") || key.equals("transfer_rate")) && (value < .75 || value > 1.5))
                throw new IllegalArgumentException("Untiered rate outside reference bounds");
        });
        materials.forEach((role, material) -> {
            Identifiers.role(role);
            if (material.length() > 192) throw new IllegalArgumentException("Invalid material label");
        });
        if (harvestTier != null) Identifiers.id(harvestTier);
    }
    public double rate() {
        double rate = properties.getOrDefault("work_rate", properties.getOrDefault("airflow", properties.getOrDefault("pressure", 1.0)));
        return rate >= .75 && rate <= 1.5 ? rate : 1;
    }
}
