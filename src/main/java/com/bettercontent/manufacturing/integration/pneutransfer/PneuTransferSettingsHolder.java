package com.bettercontent.manufacturing.integration.pneutransfer;

import com.bettercontent.manufacturing.api.MachineSettings;

/** Implemented by the native block entities; no original parts are retained. */
public interface PneuTransferSettingsHolder {
    MachineSettings betterManufacturing$transferSettings();
    void betterManufacturing$setTransferSettings(MachineSettings settings);
}
