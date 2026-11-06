package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Finish a blade made by Clockwork's original plank saw route, without duplicating blade length. */
@Mixin(PartVariantRecipe.class)
public abstract class ClockworkBladeFinishingMixin {
    @Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
    private void manufacturing$finishCutBlade(CraftingContainer grid, RegistryAccess registries,
                                                CallbackInfoReturnable<ItemStack> callback) {
        var recipe = (PartVariantRecipe) (Object) this;
        String id = recipe.getId().toString();
        if (!id.equals("vs_clockwork:propeller_blade_finish")
            && !id.equals("vs_clockwork:wide_propeller_blade_finish")) return;
        ItemStack finished = callback.getReturnValue();
        if (finished == null || finished.isEmpty()) return;
        String output = String.valueOf(ForgeRegistries.ITEMS.getKey(finished.getItem()));
        ItemStack blank = ItemStack.EMPTY;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (!output.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())))) continue;
            if (!blank.isEmpty() || stack.hasTag() && stack.getTag().contains(SettingsNbt.KEY)) {
                callback.setReturnValue(ItemStack.EMPTY); // never retool or duplicate a configured blade
                return;
            }
            blank = stack;
        }
        if (blank.isEmpty()) { callback.setReturnValue(ItemStack.EMPTY); return; }
        var settings = SettingsNbt.read(finished.getTag(), output);
        if (settings == null) { callback.setReturnValue(ItemStack.EMPTY); return; }
        // A newly crafted or saw-cut stock blade has Clockwork's implicit length of one.
        // A present length must be valid; never turn a damaged or malformed blade into one.
        var nativeTag = blank.getTag();
        boolean hasLength = nativeTag != null && nativeTag.contains("BladeLength");
        if (hasLength && (!nativeTag.contains("BladeLength", Tag.TAG_ANY_NUMERIC)
            || !Double.isFinite(nativeTag.getDouble("BladeLength"))
            || nativeTag.getDouble("BladeLength") <= 0)) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        // Native cutting and assembly encode the actual physical length on the blade item.
        // Preserve that tag and any native metadata; replace only Manufacturing's final rating.
        if (nativeTag != null) finished.setTag(nativeTag.copy());
        if (!hasLength) finished.getOrCreateTag().putDouble("BladeLength", 1);
        SettingsNbt.write(finished.getOrCreateTag(), settings);
    }
}
