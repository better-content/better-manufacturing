package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import de.ellpeck.prettypipes.pipe.PipeBlockEntity;
import de.ellpeck.prettypipes.pipe.modules.extraction.ExtractionModuleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep native routing, simulated extraction, filter and oversending code intact. */
@Mixin(value = ExtractionModuleItem.class, remap = false)
public abstract class PrettypipesExtractionMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lde/ellpeck/prettypipes/pipe/PipeBlockEntity;shouldWorkNow(I)Z"), index = 0)
    private int betterManufacturing$interval(int nativeInterval, ItemStack module, PipeBlockEntity pipe) {
        return PrettypipesRates.extractionInterval(module, nativeInterval);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;", ordinal = 0), index = 1)
    private int betterManufacturing$limit(int nativeLimit, ItemStack module, PipeBlockEntity pipe) {
        return PrettypipesRates.extractionLimit(module, nativeLimit);
    }
}
