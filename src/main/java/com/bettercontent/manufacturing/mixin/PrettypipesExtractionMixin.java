package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import de.ellpeck.prettypipes.pipe.PipeBlockEntity;
import de.ellpeck.prettypipes.pipe.modules.extraction.ExtractionModuleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraftforge.items.IItemHandler;

/** Keep native routing, simulated extraction, filter and oversending code intact. */
@Mixin(value = ExtractionModuleItem.class, remap = false)
public abstract class PrettypipesExtractionMixin {
    // Redirect supports capturing tick's arguments; ModifyArg only receives invocation arguments.
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lde/ellpeck/prettypipes/pipe/PipeBlockEntity;shouldWorkNow(I)Z"))
    private boolean betterManufacturing$interval(PipeBlockEntity receiver, int nativeInterval,
                                                ItemStack module, PipeBlockEntity pipe) {
        return receiver.shouldWorkNow(PrettypipesRates.extractionInterval(module, nativeInterval));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
    private ItemStack betterManufacturing$limit(IItemHandler receiver, int slot, int nativeLimit, boolean simulate,
                                               ItemStack module, PipeBlockEntity pipe) {
        return receiver.extractItem(slot, PrettypipesRates.extractionLimit(module, nativeLimit), simulate);
    }
}
