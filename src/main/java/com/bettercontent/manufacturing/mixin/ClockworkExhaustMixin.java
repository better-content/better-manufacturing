package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Exhaust spends every gas species in the Kelvin node on its native tick. Its fan and
 * ship-thrust presentation both read getSpeed(). A less efficient assembly can lose
 * response, but an improved one cannot create extra work from the same spent gas:
 * Clockwork's native pressure-derived airflow and 256 speed ceiling remain absolute.
 */
@Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.exhaust.ExhaustBlockEntity", remap = false)
public abstract class ClockworkExhaustMixin {
    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$paidAirflow(CallbackInfoReturnable<Float> callback) {
        float nativeSpeed = callback.getReturnValueF();
        if (nativeSpeed > 0)
            callback.setReturnValue((float) (nativeSpeed * Math.min(1d,
                ClockworkSettingsStore.rate(this, "gas_transfer"))));
    }
}
