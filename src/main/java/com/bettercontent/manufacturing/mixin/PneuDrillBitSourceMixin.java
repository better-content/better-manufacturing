package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneuDrillBitSource;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounded transfer at PNCR's actual thermo completion and assembly drill result. */
public final class PneuDrillBitSourceMixin {
    private PneuDrillBitSourceMixin() {}

    @Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity", remap = false)
    public static abstract class Plant {
        @Shadow private boolean searchForRecipe;
        @Shadow private ThermoPlantRecipe currentRecipe;
        @Shadow private int craftingProgress;
        @Shadow @Final private ItemStackHandler inputItemHandler;
        @Unique private String manufacturing$lastDrillInput = "";

        @Inject(method = "tickServer", at = @At("HEAD"))
        private void manufacturing$recheckPreparedDrillInput(CallbackInfo info) {
            ItemStack input = inputItemHandler.getStackInSlot(0);
            String marker = input.hasTag() ? input.getTag().getString(PneuDrillBitSource.MARKER) : "";
            if (!marker.equals(manufacturing$lastDrillInput)) {
                manufacturing$lastDrillInput = marker;
                searchForRecipe = true;
            }
        }

        @Inject(method = "runOneCycle", at = @At("HEAD"), cancellable = true)
        private void manufacturing$rejectStaleDrillInput(CallbackInfo info) {
            if (currentRecipe == null) return;
            String recipe = currentRecipe.getId().toString();
            if (!recipe.equals("pneumaticcraft:thermo_plant/iron_drill_bit")
                && !recipe.equals("pneumaticcraft:thermo_plant/compressed_iron_drill_bit")
                && !recipe.equals("pneumaticcraft:thermo_plant/diamond_drill_bit")) return;
            String output = PneuDrillBitSource.id(currentRecipe.getOutputItem());
            if (!PneuDrillBitSource.thermoInput(inputItemHandler.getStackInSlot(0), output)) {
                craftingProgress = 0;
                searchForRecipe = true;
                info.cancel();
            }
        }

        @Redirect(method = "runOneCycle", at = @At(value = "INVOKE",
            target = "Lme/desht/pneumaticcraft/api/crafting/recipe/ThermoPlantRecipe;getOutputItem()Lnet/minecraft/world/item/ItemStack;"))
        private ItemStack manufacturing$transferDrillBit(ThermoPlantRecipe recipe) {
            String id = recipe.getId().toString();
            if (!id.equals("pneumaticcraft:thermo_plant/iron_drill_bit")
                && !id.equals("pneumaticcraft:thermo_plant/compressed_iron_drill_bit")
                && !id.equals("pneumaticcraft:thermo_plant/diamond_drill_bit")) return recipe.getOutputItem();
            return PneuDrillBitSource.thermoOutput(inputItemHandler.getStackInSlot(0), recipe.getOutputItem());
        }
    }

    @Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity$InputItemHandler", remap = false)
    public static abstract class Input {
        @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
        private void manufacturing$acceptPreparedDrillBit(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
            if (slot != 0 || !stack.hasTag()) return;
            String output = stack.getTag().getString(PneuDrillBitSource.MARKER);
            if (PneuDrillBitSource.thermoInput(stack, output)) result.setReturnValue(true);
        }
    }

    @Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.AssemblyDrillBlockEntity", remap = false)
    public static abstract class Assembly {
        @Inject(method = "getDrilledOutputForItem", at = @At("RETURN"), cancellable = true)
        private void manufacturing$transferNetheriteBit(ItemStack source, CallbackInfoReturnable<ItemStack> result) {
            ItemStack output = result.getReturnValue();
            if (PneuDrillBitSource.id(output).equals(PneuDrillBitSource.NETHERITE))
                result.setReturnValue(PneuDrillBitSource.assemblyOutput(source, output));
        }
    }
}
