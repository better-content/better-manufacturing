package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.compressed.CompressedVariant;
import com.lgmrszd.compressedcreativity.blocks.rotational_compressor.RotationalCompressorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A rated compressor cannot produce additional pressurized air without matching Create stress. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class CompressedKineticCostMixin {
    @Inject(method = "calculateStressApplied", at = @At("RETURN"), cancellable = true)
    private void manufacturing$compressorCost(CallbackInfoReturnable<Float> result) {
        if (!((Object) this instanceof RotationalCompressorBlockEntity)) return;
        var variant = CompressedVariant.get(this);
        if (variant != null) result.setReturnValue((float) (result.getReturnValueF() * variant.property("conversion")));
    }
}
