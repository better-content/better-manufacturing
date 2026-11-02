package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.clockwork.content.contraptions.propeller.copter.CopterBearingBlockEntity;

/** Copter overrides native PropellerBearing.newUpdateData; keep its native vector/tilt and use the same paid rotor response. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.propeller.copter.CopterBearingBlockEntity", remap = false)
public abstract class ClockworkCopterBearingMixin {
    @Redirect(method = "newUpdateData()Lorg/valkyrienskies/clockwork/content/contraptions/propeller/data/PropUpdateData;",
        at = @At(value = "INVOKE", target = "Lorg/valkyrienskies/clockwork/content/contraptions/propeller/copter/CopterBearingBlockEntity;getCurrentOmega()D"))
    private double manufacturing$copterRotorResponse(CopterBearingBlockEntity copter) {
        return copter.getCurrentOmega() * ClockworkSettingsStore.rate(this, "actuator_response");
    }
}
