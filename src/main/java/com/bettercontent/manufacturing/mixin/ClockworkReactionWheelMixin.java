package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Bounded response lag only; native target speed, angular momentum and ship-force code remain untouched. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.physicalities.reactionwheel.ReactionWheelBlockEntity", remap = false)
public abstract class ClockworkReactionWheelMixin {
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 0.1))
    private double manufacturing$rotorLag(double nativeLerp) {
        return nativeLerp * ClockworkSettingsStore.rate(this, "actuator_response");
    }
}
