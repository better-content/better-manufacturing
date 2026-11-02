package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scale native engine effectiveness AND its Kelvin heat draw together; retain attached-engine cap. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlockEntity", remap = false)
public abstract class ClockworkGasEngineConversionMixin {
    @Inject(method = "getEngineEfficiency", at = @At("RETURN"), cancellable = true)
    private void manufacturing$efficiency(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue((float) Math.min(1,
            callback.getReturnValueF() * ClockworkSettingsStore.rate(this, "gas_transfer")));
    }
    @Inject(method = "getHeatLoss", at = @At("RETURN"), cancellable = true)
    private void manufacturing$heatConsumption(CallbackInfoReturnable<Double> callback) {
        callback.setReturnValue(callback.getReturnValueD() * ClockworkSettingsStore.rate(this, "gas_transfer"));
    }
}
