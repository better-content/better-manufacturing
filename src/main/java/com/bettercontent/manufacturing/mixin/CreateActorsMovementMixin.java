package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rates only Create's existing stalled block-breaking progress; native tilling/paving paths remain unchanged. */
public final class CreateActorsMovementMixin {
    private CreateActorsMovementMixin() {}

    @Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
    public abstract static class Plough {
        @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$ploughSpeed(MovementContext context, CallbackInfoReturnable<Float> result) {
            if (!((Object) this instanceof PloughMovementBehaviour)) return;
            var settings = context.blockEntityData == null ? null
                : SettingsNbt.read(context.blockEntityData, "create:mechanical_plough");
            // A valid v1 record for this output with another family's airflow or
            // pressure is not a plough rating. Create already clamps to [1/128, 16];
            // a second minimum here would erase a paid sub-1x slowdown.
            if (settings != null && settings.properties().keySet().equals(java.util.Set.of("work_rate")))
                result.setReturnValue(Math.min(16f, result.getReturnValueF() * (float) settings.rate()));
        }
    }

    @Mixin(value = RollerMovementBehaviour.class, remap = false)
    public abstract static class Roller {
        @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$rollerSpeed(MovementContext context, CallbackInfoReturnable<Float> result) {
            var settings = context.blockEntityData == null ? null
                : SettingsNbt.read(context.blockEntityData, "create:mechanical_roller");
            // Retain native capped upper speed, but do not clamp a paid sub-1x
            // excavation rate back up to Create's already-applied minimum.
            if (settings != null && settings.properties().keySet().equals(java.util.Set.of("work_rate")))
                result.setReturnValue(Math.min(16f, result.getReturnValueF() * (float) settings.rate()));
        }
    }
}
