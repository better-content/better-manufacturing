package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply the drive to native water-channel speed, never replace native shallow-water geometry. */
@Mixin(targets = "dev.murad.shipping.entity.custom.vessel.tug.AbstractTugEntity", remap = false)
public abstract class LittleLogisticsTugMixin {
    @Inject(method = "swimSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$swim(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValueD() * LittleLogisticsRuntime.rate((Entity) (Object) this));
    }

    /** Native removal still drops all cargo and route items. Amend only the native tug's own drop. */
    @ModifyVariable(method = "remove", at = @At("STORE"), ordinal = 0)
    private ItemStack manufacturing$drop(ItemStack nativeDrop) {
        return LittleLogisticsRuntime.keep((Entity) (Object) this, nativeDrop);
    }
}
