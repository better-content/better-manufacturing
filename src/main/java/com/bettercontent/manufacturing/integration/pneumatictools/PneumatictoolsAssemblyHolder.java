package com.bettercontent.manufacturing.integration.pneumatictools;

import com.bettercontent.manufacturing.api.MachineSettings;

/** Baked settings live on the placed assembly drill, not in its consumed pick head. */
public interface PneumatictoolsAssemblyHolder {
    MachineSettings betterManufacturing$settings();
    void betterManufacturing$setSettings(MachineSettings settings);
    boolean betterManufacturing$isWorking();
}
