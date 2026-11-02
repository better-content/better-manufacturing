package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.integration.sifter.MeshEngineering;
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
import java.util.*;

/** The extracted native mesh is stopped and contains no process inventory. Replace its four sides. */
public final class MeshRetoolingRecipe extends CustomRecipe {
    private static final Map<Integer, String> SLOTS = Map.of(1, "north", 3, "west", 5, "east", 7, "south");
    private record Result(ItemStack mesh, NonNullList<ItemStack> refunds) {}
    public MeshRetoolingRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.MESH_RETOOL.get(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width == 3 && height == 3; }
    @Override public boolean matches(CraftingContainer grid, Level level) { return compute(grid) != null; }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) { var result = compute(grid); return result == null ? ItemStack.EMPTY : result.mesh(); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        var result = compute(grid); return result == null ? NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY) : result.refunds();
    }
    private Result compute(CraftingContainer grid) {
        if (!canCraftInDimensions(grid.getWidth(), grid.getHeight())) return null;
        for (int i = 0; i < 9; i++) if (i != 4 && !SLOTS.containsKey(i) && !grid.getItem(i).isEmpty()) return null;
        var mesh = grid.getItem(4); var id = ForgeRegistries.ITEMS.getKey(mesh.getItem());
        if (id == null || !MeshEngineering.MESHES.contains(id.toString()) || !DrillEngineering.safeItem(mesh)) return null;
        var parsed = BuildNbt.read(mesh.getTag(), id.toString(), MeshEngineering.FUNCTION);
        if (!parsed.valid() || !parsed.build().components().keySet().equals(Set.copyOf(MeshEngineering.ROLES))) return null;
        try {
            var parts = new LinkedHashMap<String, ComponentProvenance>();
            var refunds = NonNullList.withSize(9, ItemStack.EMPTY);
            for (var slot : SLOTS.entrySet()) {
                var old = parsed.build().components().get(slot.getValue());
                if (!old.item().equals("tconstruct:tool_handle") || old.materialQuanta() != 90 || !old.wholePart()) return null;
                var replacement = NativeParts.read(grid.getItem(slot.getKey()));
                if (!replacement.item().equals("tconstruct:tool_handle")) return null;
                parts.put(slot.getValue(), replacement); refunds.set(slot.getKey(), NativeParts.recover(old));
            }
            var result = mesh.copy(); result.setCount(1);
            BuildNbt.write(result.getOrCreateTag(), new MachineBuild(MachineBuild.VERSION, id.toString(), MeshEngineering.FUNCTION, parts, parsed.build().qualifications()));
            if (!MeshEngineering.evaluate(result, com.bettercontent.manufacturing.data.EngineeringCatalog.clientThread()).allowed()) return null;
            return new Result(result, refunds);
        } catch (IllegalArgumentException unsupported) { return null; }
    }
}
