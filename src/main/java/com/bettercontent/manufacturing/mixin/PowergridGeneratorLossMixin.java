package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridGeneratorCoupling;
import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.GeneratorCoupling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Circuit's real internal loss changes; native generator voltage, rotor force and topology stay native. */
@Mixin(targets = "org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlockEntity", remap = false)
public abstract class PowergridGeneratorLossMixin implements PowergridGeneratorCoupling {
    @Shadow protected GeneratorCoupling source;
    @Shadow private float resistance;

    @Override public void manufacturing$applyInternalResistance(double ratio) {
        if (source != null) source.setResistance((float) (resistance / ratio));
    }

    @ModifyArg(method = "buildCircuit", at = @At(value = "INVOKE", target = "Ljava/lang/Float;valueOf(F)Ljava/lang/Float;"), index = 0)
    private float manufacturing$loss(float nativeResistance) {
        return (float) (nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "generator_ratio"));
    }
}
