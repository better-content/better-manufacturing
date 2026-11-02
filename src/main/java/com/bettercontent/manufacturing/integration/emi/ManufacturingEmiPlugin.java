package com.bettercontent.manufacturing.integration.emi;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.*;

/** Ordinary crafting pages with truthful configured outputs; no comparison/recovery subsystem. */
@EmiEntrypoint
public final class ManufacturingEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        if (!MaterialRegistry.isFullyLoaded()) return;
        var materials = new TreeMap<String, slimeknights.tconstruct.library.materials.definition.MaterialVariantId>();
        for (var nativeRecipe : registry.getRecipeManager().getRecipes()) {
            slimeknights.tconstruct.library.materials.definition.MaterialVariantId material = null;
            if (nativeRecipe instanceof slimeknights.tconstruct.library.recipe.material.MaterialRecipe source) material = source.getMaterial().getVariant();
            if (nativeRecipe instanceof slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe source && source.isVisible()) material = source.getOutput().getVariant();
            if (material != null) materials.put(material.toString(), material);
        }
        for (var raw : registry.getRecipeManager().getRecipes()) {
            if (!(raw instanceof PartVariantRecipe recipe)) continue;
            var item = recipe.getResultItem(RegistryAccess.EMPTY).getItem();
            String outputId = ForgeRegistries.ITEMS.getKey(item).toString();
            registry.setDefaultComparison(item, Comparison.compareData(stack -> SettingsNbt.read(stack.getNbt(), outputId)));
            // Native stock items and unconfigured fallback machines remain real items;
            // never hide their EMI stacks just because a configured craft also exists.
            boolean configuredPreview = false;
            for (var material : materials.values()) {
                var grid = grid(); boolean complete = true;
                for (int i = 0; i < recipe.getIngredients().size(); i++) {
                    var ingredient = recipe.getIngredients().get(i);
                    if (ingredient.isEmpty()) continue;
                    var options = ingredient.getItems();
                    if (options.length == 0) { complete = false; break; }
                    grid.setItem(i % recipe.getWidth() + i / recipe.getWidth() * 3, options[0].copy());
                }
                if (!complete) continue;
                for (var binding : recipe.parts()) {
                    int slot = binding.slot() % recipe.getWidth() + binding.slot() / recipe.getWidth() * 3;
                    if (grid.getItem(slot).getItem() instanceof ToolPartItem part) {
                        if (!part.canUseMaterial(material.getId())) { complete = false; break; }
                        grid.setItem(slot, part.withMaterial(material));
                    } else {
                        var componentItem = grid.getItem(slot).getItem();
                        var component = registry.getRecipeManager().getRecipes().stream()
                            .filter(candidate -> candidate instanceof PartVariantRecipe && candidate.getResultItem(RegistryAccess.EMPTY).is(componentItem))
                            .map(candidate -> (PartVariantRecipe) candidate).sorted(Comparator.comparing(candidate -> candidate.getId().toString())).findFirst();
                        if (component.isEmpty()) { complete = false; break; }
                        var configured = componentExample(component.get(), material);
                        if (configured.isEmpty()) { complete = false; break; }
                        grid.setItem(slot, configured);
                    }
                    if (registry.isStackDisabled(EmiStack.of(grid.getItem(slot)))) { complete = false; break; }
                }
                if (!complete) continue;
                var output = recipe.assemble(grid, RegistryAccess.EMPTY);
                if (output.isEmpty()) continue;
                var inputs = new ArrayList<EmiIngredient>();
                for (int i = 0; i < 9; i++) inputs.add(EmiStack.of(grid.getItem(i)));
                var id = MeshEmiPlugin.variantId(recipe.getId(), material.toString());
                registry.addRecipe(new EmiCraftingRecipe(inputs, EmiStack.of(output), id));
                registry.addEmiStack(EmiStack.of(output));
                configuredPreview = true;
            }
            // Suppress the plain result preview only when truthful saved-output examples
            // were actually assembled. Otherwise keep the ordinary recipe visible.
            if (configuredPreview) registry.removeRecipes(recipe.getId());
        }
    }
    private static ItemStack componentExample(PartVariantRecipe recipe, slimeknights.tconstruct.library.materials.definition.MaterialVariantId material) {
        var grid = grid();
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var options = recipe.getIngredients().get(i).getItems();
            if (options.length > 0) grid.setItem(i % recipe.getWidth() + i / recipe.getWidth() * 3, options[0].copy());
        }
        for (var binding : recipe.parts()) {
            int slot = binding.slot() % recipe.getWidth() + binding.slot() / recipe.getWidth() * 3;
            if (!(grid.getItem(slot).getItem() instanceof ToolPartItem part) || !part.canUseMaterial(material.getId())) return ItemStack.EMPTY;
            grid.setItem(slot, part.withMaterial(material));
        }
        return recipe.assemble(grid, RegistryAccess.EMPTY);
    }
    static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
}
