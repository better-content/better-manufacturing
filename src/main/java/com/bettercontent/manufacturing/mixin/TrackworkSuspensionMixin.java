package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.trackwork.TrackworkSettings;
import edn.stratodonut.trackwork.tracks.blocks.SuspensionTrackBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SuspensionTrackBlockEntity.class, remap = false)
public abstract class TrackworkSuspensionMixin {
    @Shadow private double suspensionScale;
    /** Keep native belt sizes and maximum suspension travel; tune the measured suspension scale. */
    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Ledn/stratodonut/trackwork/tracks/blocks/SuspensionTrackBlockEntity;suspensionScale:D", opcode = 180))
    private double manufacturing$suspension(SuspensionTrackBlockEntity track) {
        double response = TrackworkSettings.property(this, "suspension_response");
        double load = TrackworkSettings.property(this, "mechanical_load");
        return suspensionScale * Math.max(.75, Math.min(1.5, response * load));
    }
}
