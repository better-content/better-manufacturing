package com.bettercontent.manufacturing.integration.eureka;

import com.bettercontent.manufacturing.api.MachineSettings;

/** Server-owned baked record on the actual native Eureka block entity. */
public interface EurekaEngineData {
    MachineSettings manufacturing$getEngineSettings();
    void manufacturing$setEngineSettings(MachineSettings settings);
}
