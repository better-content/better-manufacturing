package com.bettercontent.manufacturing.integration.trackwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import java.util.Set;

/** Implemented by the three native running-gear entity types; no global physics state. */
public interface TrackworkVariant {
    Set<String> OUTPUTS = Set.of(
        "trackwork:large_phys_track", "trackwork:large_simple_wheel", "trackwork:large_suspension_track",
        "trackwork:med_phys_track", "trackwork:med_simple_wheel", "trackwork:med_suspension_track",
        "trackwork:oleo_wheel", "trackwork:phys_track", "trackwork:simple_wheel",
        "trackwork:small_simple_wheel", "trackwork:suspension_track");
    static boolean isOutput(String id) { return OUTPUTS.contains(id); }
    String manufacturing$outputId();
    MachineSettings manufacturing$settings();
    void manufacturing$setSettings(MachineSettings settings);
}
