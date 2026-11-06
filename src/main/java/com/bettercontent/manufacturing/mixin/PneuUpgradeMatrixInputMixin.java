package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PreparedThermoInput;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain PNCR's thermo work and accounting while allowing a plate-paid single-item input. */
public final class PneuUpgradeMatrixInputMixin {
    private PneuUpgradeMatrixInputMixin() {}

    @Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity", remap = false)
    public static abstract class Plant {
        @Shadow private boolean searchForRecipe;
        @Shadow @Final private ItemStackHandler inputItemHandler;
        @Unique private boolean manufacturing$preparedLapis;

        // PNCR normally invalidates only when the Item identity changes. Raw
        // and prepared lapis have the same Item but different valid recipes.
        @Inject(method = "tickServer", at = @At("HEAD"))
        private void manufacturing$recheckPreparedLapis(CallbackInfo info) {
            boolean now = PreparedThermoInput.isUpgradeMatrixLapis(inputItemHandler.getStackInSlot(0));
            if (now != manufacturing$preparedLapis) {
                manufacturing$preparedLapis = now;
                searchForRecipe = true;
            }
        }
    }

    @Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity$InputItemHandler", remap = false)
    public static abstract class Input {
        // PNCR's global AcceptabilityCache is keyed by Item, not NBT. Testing
        // ordinary lapis first must not permanently reject a prepared stack.
        // Only the exact prepared marker bypasses that stale cache; the native
        // recipe still tests its actual Ingredient before running any work.
        @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
        private void manufacturing$acceptPreparedLapis(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
            if (slot == 0 && PreparedThermoInput.isUpgradeMatrixLapis(stack)) result.setReturnValue(true);
        }
    }
}
