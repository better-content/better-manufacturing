package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Faster native Kelvin pocket-opening response also draws gas sooner; buoyancy and gas types stay native. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.pockets.nozzle.GasNozzleBlockEntity", remap = false)
public abstract class ClockworkNozzleResponseMixin {
    @Inject(method = "getChaseSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$nozzleResponse(CallbackInfoReturnable<Double> callback) {
        callback.setReturnValue(callback.getReturnValueD() * ClockworkSettingsStore.rate(this, "gas_transfer"));
    }
}
