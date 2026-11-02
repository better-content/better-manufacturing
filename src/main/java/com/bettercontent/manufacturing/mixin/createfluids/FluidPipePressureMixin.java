package com.bettercontent.manufacturing.mixin.createfluids;

import com.bettercontent.manufacturing.integration.createfluids.FluidNativeRates;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A passive pipe can impose a material-dependent pressure loss, but never generates pressure. */
@Mixin(value = FluidTransportBehaviour.class, remap = false)
public abstract class FluidPipePressureMixin extends BlockEntityBehaviour {
    protected FluidPipePressureMixin(SmartBlockEntity block) { super(block); }
    @ModifyArg(method = "addPressure", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/fluids/PipeConnection;addPressure(ZF)V"), index = 1)
    private float manufacturing$pipePressure(float nativePressure) {
        if (!(blockEntity instanceof FluidPipeBlockEntity) && !(blockEntity instanceof StraightPipeBlockEntity))
            return nativePressure;
        return (float) (nativePressure * Math.min(1, FluidNativeRates.rate(blockEntity, "fluid_rate")));
    }
}
