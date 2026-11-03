package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native minecart destroy and pick use getPickResult; keep original entity drop and name handling. */
@Mixin(targets = {
    "dev.murad.shipping.entity.custom.train.locomotive.EnergyLocomotiveEntity",
    "dev.murad.shipping.entity.custom.train.locomotive.SteamLocomotiveEntity"
}, remap = false)
public abstract class LittleLogisticsLocomotiveDropMixin {
    @Inject(method = "getPickResult", at = @At("RETURN"), cancellable = true, remap = true)
    private void manufacturing$keep(CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(LittleLogisticsRuntime.keep((Entity) (Object) this, cir.getReturnValue()));
    }
}
