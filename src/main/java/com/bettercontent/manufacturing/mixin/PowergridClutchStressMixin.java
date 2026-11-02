package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity.ClutchMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native generator sourceForce uses lastStressApplied from this same sum: more force costs more kinetic SU. */
@Mixin(targets = "org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity", remap = false)
public abstract class PowergridClutchStressMixin {
    @Shadow private ScrollOptionBehaviour<ClutchMode> mode;
    @Inject(method = "stressSum", at = @At("RETURN"), cancellable = true)
    private void manufacturing$generatorLoad(CallbackInfoReturnable<Float> result) {
        if (mode != null && mode.get() == ClutchMode.GENERATOR) {
            result.setReturnValue((float) (result.getReturnValueF() * PowergridRatings.ratio((BlockEntity) (Object) this, "generator_ratio")));
        }
    }
}
