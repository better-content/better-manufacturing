package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.valkyrienskies.clockwork.ClockworkConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native Kelvin flow and kinetic work remain Clockwork-owned; only baked throughput scales. */
public final class ClockworkGasFlowMixin {
    private ClockworkGasFlowMixin() {}

    @Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.pump.PumpDuctBlockEntity", remap = false)
    public static abstract class Pump {
        @Inject(method = "getPumpPressure", at = @At("RETURN"), cancellable = true)
        private void manufacturing$pressure(CallbackInfoReturnable<Double> callback) {
            // Clockwork derives pressure from |RPM|/256 and caps the mechanical speed at 256.
            // A rated pump may reach that native maximum earlier, never exceed it.
            callback.setReturnValue(Math.min(1_023_440d,
                callback.getReturnValueD() * ClockworkSettingsStore.rate(this, "gas_transfer")));
        }
    }

    @Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.generation.compressor.AirCompressorBlockEntity", remap = false)
    public static abstract class Compressor {
        // Modify the native work-speed factor once, before native air/aether composition and pressure checks.
        // Both gases scale together; atmosphere sampling and maximum native pressure are unchanged.
        @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lorg/valkyrienskies/clockwork/ClockworkConfig$Server;getAirCompressorSpeed()D"))
        private double manufacturing$airSpeed(ClockworkConfig.Server config) {
            return config.getAirCompressorSpeed() * ClockworkSettingsStore.rate(this, "gas_transfer");
        }
    }
}
