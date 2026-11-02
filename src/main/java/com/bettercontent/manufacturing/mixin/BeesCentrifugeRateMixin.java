package com.bettercontent.manufacturing.mixin;

import com.accbdd.complicated_bees.block.entity.CentrifugeBlockEntity;
import com.bettercontent.manufacturing.integration.bees.BeesSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve installed bee upgrades and output rolls; compensate RF/tick for the faster native cycle. */
@Mixin(value = CentrifugeBlockEntity.class, remap = false)
public abstract class BeesCentrifugeRateMixin {
    @Inject(method = "getMaxProgress", at = @At("RETURN"), cancellable = true)
    private void bees$progress(CallbackInfoReturnable<Integer> cir) {
        double rate = BeesSettings.rate((BlockEntity)(Object)this);
        if (rate != 1) {
            int progress = Math.max(1, (int)Math.round(cir.getReturnValue() / rate));
            ((CentrifugeBlockEntity)(Object)this).setMaxProgress(progress);
            cir.setReturnValue(progress);
        }
    }

    @Inject(method = "getActiveEnergyUsage", at = @At("RETURN"), cancellable = true)
    private void bees$energy(CallbackInfoReturnable<Integer> cir) {
        double rate = BeesSettings.rate((BlockEntity)(Object)this);
        if (rate != 1) {
            int energy = Math.max(1, (int)Math.ceil(cir.getReturnValue() * rate));
            ((CentrifugeBlockEntity)(Object)this).setActiveEnergyUsage(energy);
            cir.setReturnValue(energy);
        }
    }
}
