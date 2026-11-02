package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.trackwork.TrackworkSettings;
import edn.stratodonut.trackwork.tracks.blocks.PhysEntityTrackBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PhysEntityTrackBlockEntity.class, remap = false)
public abstract class TrackworkPhysMixin {
    /** Native revolute-joint compliance, not wheel radius, mass, geometry, or a global force. */
    @ModifyConstant(method = "asyncConstrainWheel", constant = @Constant(doubleValue = 1.0E-8d))
    private double manufacturing$compliance(double nativeCompliance) {
        double response = TrackworkSettings.property(this, "suspension_response");
        double load = TrackworkSettings.property(this, "mechanical_load");
        return nativeCompliance / Math.max(.75, Math.min(1.5, response * load));
    }
    /** Let native mass, track size and configuration calculate stress, then rate the load. */
    @Inject(method = "calculateStressApplied(F)F", at = @At("RETURN"), cancellable = true)
    private void manufacturing$load(float shipMass, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue((float)(cir.getReturnValue() * TrackworkSettings.property(this, "mechanical_load")));
    }
}
