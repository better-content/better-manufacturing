package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scales the native bearing's motion rate, never replaces flap geometry or ship physics. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingBlockEntity", remap = false)
public abstract class ClockworkFlightResponseMixin {
    @Inject(method = "getAngularSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$flapResponse(CallbackInfoReturnable<Double> callback) {
        callback.setReturnValue(callback.getReturnValueD() * ClockworkSettingsStore.rate(this, "actuator_response"));
    }
}
