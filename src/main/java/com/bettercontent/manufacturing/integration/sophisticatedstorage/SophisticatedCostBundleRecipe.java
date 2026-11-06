package com.bettercontent.manufacturing.integration.sophisticatedstorage;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Pays a displaced native material and construction parts before the native craft. */
public final class SophisticatedCostBundleRecipe extends CustomRecipe {
    private record Match(ItemStack anchor, int anchorSlot) {}
    private final Ingredient anchor;
    private final List<Ingredient> costs;
    private final String marker;

    public SophisticatedCostBundleRecipe(ResourceLocation id, Ingredient anchor, List<Ingredient> costs, String marker) {
        super(id, CraftingBookCategory.MISC);
        if (anchor.isEmpty() || costs.isEmpty() || costs.size() > 3)
            throw new IllegalArgumentException("Invalid Sophisticated cost bundle");
        this.anchor = anchor;
        this.costs = List.copyOf(costs);
        this.marker = SophisticatedCostBundleIngredient.requireMarker(marker);
    }

    @Override public RecipeSerializer<?> getSerializer() {
        return com.bettercontent.manufacturing.recipe.ManufacturingRecipes.SOPHISTICATED_COST_BUNDLE.get();
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= costs.size() + 1; }
    @Override public boolean matches(CraftingContainer grid, Level level) { return findMatch(grid) != null; }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        Match match = findMatch(grid);
        if (match == null) return ItemStack.EMPTY;
        ItemStack result = match.anchor().copy();
        result.setCount(1);
        result.getOrCreateTag().putString(SophisticatedCostBundleIngredient.KEY, marker);
        return result;
    }

    private Match findMatch(CraftingContainer grid) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.hasTag() && (stack.getTag().contains(SophisticatedCostBundleIngredient.KEY)
                || stack.getTag().contains(SettingsNbt.KEY))) return null;
            occupied.add(slot);
        }
        if (occupied.size() != costs.size() + 1) return null;
        for (int anchorSlot : occupied) {
            ItemStack stack = grid.getItem(anchorSlot);
            if (!anchor.test(stack)) continue;
            boolean[] used = new boolean[grid.getContainerSize()];
            used[anchorSlot] = true;
            if (matchesCosts(grid, 0, used)) return new Match(stack, anchorSlot);
        }
        return null;
    }

    private boolean matchesCosts(CraftingContainer grid, int index, boolean[] used) {
        if (index == costs.size()) return true;
        Ingredient cost = costs.get(index);
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (used[slot] || stack.isEmpty() || !cost.test(stack)) continue;
            used[slot] = true;
            if (matchesCosts(grid, index + 1, used)) return true;
            used[slot] = false;
        }
        return false;
    }

    public static final class Serializer implements RecipeSerializer<SophisticatedCostBundleRecipe> {
        @Override public SophisticatedCostBundleRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient anchor = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "anchor"));
            var array = GsonHelper.getAsJsonArray(json, "costs");
            List<Ingredient> costs = new ArrayList<>();
            for (var entry : array) costs.add(Ingredient.fromJson(entry));
            return new SophisticatedCostBundleRecipe(id, anchor, costs, GsonHelper.getAsString(json, "marker"));
        }
        @Override public SophisticatedCostBundleRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return fromJson(id, JsonParser.parseString(buffer.readUtf(8192)).getAsJsonObject());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, SophisticatedCostBundleRecipe recipe) {
            var json = new JsonObject();
            json.add("anchor", recipe.anchor.toJson());
            var costs = new com.google.gson.JsonArray();
            recipe.costs.forEach(cost -> costs.add(cost.toJson()));
            json.add("costs", costs);
            json.addProperty("marker", recipe.marker);
            buffer.writeUtf(json.toString(), 8192);
        }
    }
}
