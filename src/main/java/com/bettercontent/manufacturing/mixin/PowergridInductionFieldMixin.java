package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native field links voltage AND reciprocal rotor force in GeneratorCoupling.tick; no free electric output. */
@Mixin(targets = "org.patryk3211.powergrid.kinetics.generator.inductionrotor.InductionRotorBlockEntity", remap = false)
public abstract class PowergridInductionFieldMixin {
    @ModifyArg(method = "recalculateField", at = @At(value = "INVOKE", target = "Ljava/lang/Float;valueOf(F)Ljava/lang/Float;"), index = 0)
    private float manufacturing$field(float nativeField) {
        return (float) (nativeField * PowergridRatings.ratio((BlockEntity) (Object) this, "generator_ratio"));
    }
}
