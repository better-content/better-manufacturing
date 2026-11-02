package com.bettercontent.manufacturing.integration.prettypipes;

import com.bettercontent.manufacturing.api.MachineSettings;

/** Injected only into Pretty Pipes pressurizer block entities. */
public interface PrettypipesPressurizerSettings {
    MachineSettings betterManufacturing$getSettings();
    void betterManufacturing$setSettings(MachineSettings settings);
}
