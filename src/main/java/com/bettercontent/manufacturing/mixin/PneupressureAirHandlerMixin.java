package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import me.desht.pneumaticcraft.common.capabilities.MachineAirHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native pressure tier always remains the upper bound. Both danger and critical limits scale together. */
@Mixin(value = MachineAirHandler.class, remap = false)
public abstract class PneupressureAirHandlerMixin {
    @Unique private float pneupressure$limit = 1;
    @Inject(method = "tick", at = @At("HEAD"))
    private void pneupressure$bindOwner(BlockEntity owner, CallbackInfo ci) {
        pneupressure$limit = PneupressureBuild.pressureEnvelope(owner);
    }
    @Inject(method = "getDangerPressure", at = @At("RETURN"), cancellable = true)
    private void pneupressure$danger(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * pneupressure$limit);
    }
    @Inject(method = "getCriticalPressure", at = @At("RETURN"), cancellable = true)
    private void pneupressure$critical(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * pneupressure$limit);
    }
}
