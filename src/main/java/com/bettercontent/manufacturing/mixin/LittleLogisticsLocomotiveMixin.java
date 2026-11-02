package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native rail slope, corner, powered-rail and station decisions precede this bounded drive multiplier. */
@Mixin(targets = "dev.murad.shipping.entity.custom.train.locomotive.AbstractLocomotiveEntity", remap = false)
public abstract class LittleLogisticsLocomotiveMixin {
    @Shadow private double speedLimit;

    @Inject(method = "tickSpeedLimit", at = @At(value = "FIELD", target = "Ldev/murad/shipping/entity/custom/train/locomotive/AbstractLocomotiveEntity;speedLimit:D", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void manufacturing$railLimit(CallbackInfo ci) {
        this.speedLimit *= LittleLogisticsRuntime.rate((Entity) (Object) this);
    }

    @Inject(method = "getSpeedModifier", at = @At("RETURN"), cancellable = true)
    private void manufacturing$traction(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValueD() * LittleLogisticsRuntime.rate((Entity) (Object) this));
    }

    @Inject(method = "getMaxCartSpeedOnRail", at = @At("RETURN"), cancellable = true)
    private void manufacturing$railCeiling(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue((float) (cir.getReturnValueF() * LittleLogisticsRuntime.rate((Entity) (Object) this)));
    }
}
