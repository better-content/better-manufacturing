package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Only pump-generated pressure is rated; pipe propagation, endpoints and native flow remain Create-owned. */
@Mixin(value = PumpBlockEntity.class, remap = false)
public abstract class PumpPressureMixin {
    @Redirect(method = "distributePressureTo", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;addPressure(Lnet/minecraft/core/Direction;ZF)V"))
    private void manufacturing$pressure(FluidTransportBehaviour pipe, Direction side, boolean pull, float nativePressure) {
        var variant = KineticVariantBehaviour.get(this);
        pipe.addPressure(side, pull, (float) (nativePressure * (variant == null ? 1 : variant.rate())));
    }
}
