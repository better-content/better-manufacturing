package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.compressed.CompressedVariant;
import com.lgmrszd.compressedcreativity.blocks.compressed_air_engine.CompressedAirEngineBlockEntity;
import com.lgmrszd.compressedcreativity.blocks.rotational_compressor.RotationalCompressorBlockEntity;
import com.lgmrszd.compressedcreativity.blocks.heater.HeaterBlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rate is applied at native conversion accounting points, never by independently producing air/heat. */
public final class CompressedConversionMixin {
    private CompressedConversionMixin() {}
    @Mixin(value = RotationalCompressorBlockEntity.class, remap = false)
    public abstract static class Compressor {
        @Shadow private double airGeneratedPerTick;
        @Inject(method = "tick", at = @At(value = "FIELD", target = "Lcom/lgmrszd/compressedcreativity/blocks/rotational_compressor/RotationalCompressorBlockEntity;airGeneratedPerTick:D", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
        private void manufacturing$productionForSameRpm(CallbackInfo info) {
            var variant = CompressedVariant.get(this);
            if (variant != null) airGeneratedPerTick *= variant.property("conversion");
        }
    }
    @Mixin(value = CompressedAirEngineBlockEntity.class, remap = false)
    public abstract static class Engine {
        @Shadow private float airUsage;
        @Shadow private boolean working;
        @Inject(method = "getGeneratedSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$nativeRpm(CallbackInfoReturnable<Float> result) {
            var variant = CompressedVariant.get(this);
            if (variant == null) return;
            float speed = result.getReturnValueF();
            // Do not raise the native 256 RPM ceiling or forge stress beyond native output.
            result.setReturnValue((float) Math.copySign(Math.min(256, Math.abs(speed) * variant.property("conversion")), speed));
        }
        @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lcom/lgmrszd/compressedcreativity/blocks/compressed_air_engine/CompressedAirEngineBlockEntity;airUsage:F", opcode = Opcodes.PUTFIELD))
        private void manufacturing$matchedWorkAirCost(CompressedAirEngineBlockEntity engine, float nativeCost) {
            var variant = CompressedVariant.get(this);
            if (variant == null || !working || engine.isOverStressed()) { airUsage = nativeCost; return; }
            double rate = variant.property("conversion");
            // Native output is 256 RPM when working. Lower ratings reduce both actual RPM and
            // running air cost together; higher ratings cannot exceed the native 256 RPM ceiling,
            // so they must not discount air for unchanged output. Idle/overstressed remain native.
            airUsage = (float) (nativeCost * Math.min(1, rate));
        }
    }
    @Mixin(value = HeaterBlockEntity.class, remap = false)
    public abstract static class Heater {
        @Inject(method = "getCoolingSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$thermalTransfer(CallbackInfoReturnable<Double> result) {
            var variant = CompressedVariant.get(this);
            if (variant != null) result.setReturnValue(result.getReturnValueD() * variant.property("conversion"));
        }
    }
}
