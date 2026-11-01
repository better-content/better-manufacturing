package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.integration.create.DrillEngineering;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.tconstruct.NativeParts;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Grid retooling for the drill's sole head role. Native drops make the drill an empty stopped item. */
public final class DrillRetoolingRecipe extends CustomRecipe {
    private record Result(ItemStack output, ItemStack recovered, int partSlot) {}
    public DrillRetoolingRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.DRILL_RETOOL.get(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public boolean matches(CraftingContainer grid, Level level) { return compute(grid) != null; }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        var result = compute(grid); return result == null ? ItemStack.EMPTY : result.output();
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        var remaining = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        var result = compute(grid);
        if (result != null) remaining.set(result.partSlot(), result.recovered());
        return remaining;
    }
    private Result compute(CraftingContainer grid) {
        ItemStack machine = ItemStack.EMPTY, head = ItemStack.EMPTY; int partSlot = -1;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            var stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.toString().equals(DrillEngineering.CHASSIS) && machine.isEmpty()) machine = stack;
            else if (id != null && id.toString().equals("tconstruct:pick_head") && head.isEmpty()) { head = stack; partSlot = i; }
            else return null;
        }
        if (machine.isEmpty() || head.isEmpty() || !machine.hasTag()) return null;
        // Reject containers, running-work records and unreviewed extensions instead of deleting them.
        if (!DrillEngineering.safeItem(machine)) return null;
        var state = DrillEngineering.evaluate(machine.getTag());
        if (!state.allowed() || !state.build().hasProvenance()) return null;
        try {
            var replacement = state.build().replace("head", NativeParts.read(head), true);
            ItemStack refund = NativeParts.recover(replacement.recovered());
            ItemStack result = machine.copy(); result.setCount(1);
            BuildNbt.write(result.getOrCreateTag(), replacement.build());
            if (!DrillEngineering.evaluate(result.getTag()).allowed()) return null;
            return new Result(result, refund, partSlot);
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
