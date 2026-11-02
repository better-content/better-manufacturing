package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.valkyrienskies.clockwork.content.physicalities.gyro.GyroBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native gyro controller still owns orientation, feedback and ship physics; only its paid speed input changes. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.physicalities.gyro.GyroBlockEntity", remap = false)
public abstract class ClockworkGyroDriveMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lorg/valkyrienskies/clockwork/content/physicalities/gyro/GyroBlockEntity;getSpeed()F", ordinal = 0))
    private float manufacturing$controllerDrive(GyroBlockEntity gyro) {
        return (float) (gyro.getSpeed() * ClockworkSettingsStore.rate(this, "actuator_response"));
    }
}
