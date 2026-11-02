package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.common.block.entity.EtchingTankBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native temperature still determines etch interval, acid wear, and UV-dependent random result. */
@Mixin(value = EtchingTankBlockEntity.class, remap = false)
public abstract class PneuProcessEtchingMixin {
    @Inject(method = "getTickInterval", at = @At("RETURN"), cancellable = true)
    private void betterManufacturing$interval(CallbackInfoReturnable<Integer> cir) {
        double rate = PneuProcessNative.rate((BlockEntity) (Object) this, "process_rate");
        cir.setReturnValue(Math.max(1, Math.min(40, (int) Math.round(cir.getReturnValue() / rate))));
    }
}
