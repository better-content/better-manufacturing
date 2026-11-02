package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import de.ellpeck.prettypipes.pipe.PipeBlockEntity;
import de.ellpeck.prettypipes.pipe.modules.SpeedModuleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native installed-module dispatch keeps incompatibility checks and module serialization. */
@Mixin(value = SpeedModuleItem.class, remap = false)
public abstract class PrettypipesSpeedMixin {
    @Inject(method = "getItemSpeedIncrease", at = @At("RETURN"), cancellable = true)
    private void betterManufacturing$installedBonus(ItemStack module, PipeBlockEntity pipe, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(PrettypipesRates.speedBonus(module, cir.getReturnValue()));
    }
}
