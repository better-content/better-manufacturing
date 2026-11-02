package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native kinetic valve drives a real Kelvin aperture; scale only its opening response, not pressure limits. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlockEntity", remap = false)
public abstract class ClockworkValveResponseMixin {
    @Inject(method = "getChaseSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$valveResponse(CallbackInfoReturnable<Double> callback) {
        double nativeRate = callback.getReturnValueD();
        callback.setReturnValue(Math.max(0, Math.min(1, nativeRate * ClockworkSettingsStore.rate(this, "gas_transfer"))));
    }
}
