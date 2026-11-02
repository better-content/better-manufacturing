package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.clockwork.content.physicalities.gas_thruster.GasThrusterBlockEntity;

/** Bounded nozzle losses only: never create extra thrust from unchanged Kelvin gas consumption. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.physicalities.gas_thruster.GasThrusterBlockEntity", remap = false)
public abstract class ClockworkThrusterResponseMixin {
    @Shadow private volatile double thrust;
    @Redirect(method = "physTick", at = @At(value = "FIELD",
        target = "Lorg/valkyrienskies/clockwork/content/physicalities/gas_thruster/GasThrusterBlockEntity;thrust:D"))
    private double manufacturing$effectiveThrust(GasThrusterBlockEntity thruster) {
        return thrust * Math.min(1, ClockworkSettingsStore.rate(this, "gas_transfer"));
    }
}
