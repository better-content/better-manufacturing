package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkBladeCuttingPrep;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Carries each paid part pair through Clockwork's original Create saw work. */
@Mixin(value = SawBlockEntity.class, remap = false)
public abstract class ClockworkBladeCuttingMixin {
    @Shadow public ProcessingInventory inventory;

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void manufacturing$keepMalformedBillUnspent(CallbackInfo callback) {
        ItemStack source = inventory.getStackInSlot(0);
        if (!source.hasTag() || !source.getTag().contains(ClockworkBladeCuttingPrep.MARKER)) return;
        String output = source.getTag().getCompound(ClockworkBladeCuttingPrep.MARKER).getString("output");
        if (ClockworkBladeCuttingPrep.paidSettings(source, output).isEmpty()) callback.cancel();
    }

    // getRecipes runs before Create clears the saw inventory. Reject malformed paid
    // inputs here so they cannot enter the 100-tick native process and lose a plank.
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true, remap = false)
    private void manufacturing$validatePaidCutting(CallbackInfoReturnable<List<? extends Recipe<?>>> callback) {
        ItemStack source = inventory.getStackInSlot(0);
        var recipes = callback.getReturnValue();
        if (recipes == null || recipes.isEmpty()) return;
        var accepted = new ArrayList<Recipe<?>>();
        boolean changed = false;
        for (Recipe<?> recipe : recipes) {
            String output = outputFor(recipe);
            if (output != null && ClockworkBladeCuttingPrep.paidSettings(source, output).isEmpty()) changed = true;
            else accepted.add(recipe);
        }
        if (changed) callback.setReturnValue(accepted);
    }

    @WrapOperation(method = "applyRecipe", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/saw/CuttingRecipe;rollResults()Ljava/util/List;"), remap = false)
    private List<ItemStack> manufacturing$rateCutBlades(CuttingRecipe recipe, Operation<List<ItemStack>> original,
                                                       @Local(name = "input") ItemStack source) {
        String output = outputFor(recipe);
        if (output == null) return original.call(recipe);
        var settings = ClockworkBladeCuttingPrep.paidSettings(source, output);
        if (settings.isEmpty()) return List.of();
        var nativeResults = original.call(recipe);
        int nativeCount = 0;
        for (ItemStack stack : nativeResults) {
            if (!output.equals(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))))
                return List.of();
            nativeCount += stack.getCount();
        }
        if (nativeCount != settings.size()) return List.of();
        var rated = new ArrayList<ItemStack>(settings.size());
        for (var build : settings) {
            ItemStack blade = nativeResults.get(0).copy();
            blade.setCount(1);
            // Clockwork's stock blade item has an implicit initial length of one.
            blade.getOrCreateTag().putDouble("BladeLength", 1);
            SettingsNbt.write(blade.getOrCreateTag(), build);
            rated.add(blade);
        }
        return rated;
    }

    @Unique private static String outputFor(Recipe<?> recipe) {
        if (recipe == null) return null;
        return switch (recipe.getId().toString()) {
            case "vs_clockwork:cutting/propeller_blade" -> "vs_clockwork:propeller_blade";
            case "vs_clockwork:cutting/wide_propeller_blade" -> "vs_clockwork:wide_propeller_blade";
            default -> null;
        };
    }
}
