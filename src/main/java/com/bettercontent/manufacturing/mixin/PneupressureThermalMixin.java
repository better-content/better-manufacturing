package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native generatePressure consumes airProduced for both chamber air and thermal equalisation. */
@Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ThermalCompressorBlockEntity", remap = false)
public abstract class PneupressureThermalMixin {
    @Inject(method = "airProduced", at = @At("RETURN"), cancellable = true)
    private void pneupressure$heatDrivenAir(Direction side, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValue() * PneupressureBuild.rate((BlockEntity) (Object) this, "compression_rate"));
    }
}
