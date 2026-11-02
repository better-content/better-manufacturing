package com.bettercontent.manufacturing.integration.createbulk;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.google.gson.JsonObject;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

/** Native mechanical-crafter recipe; two wheels leave with the same baked settings. */
public final class BulkWheelRecipe extends MechanicalCraftingRecipe {
    public record Part(String role, int slot) {}
    private final List<Part> parts;
    private final String function;
    public BulkWheelRecipe(MechanicalCraftingRecipe base, String function, List<Part> parts) {
        super(base.getId(), base.getGroup(), base.getWidth(), base.getHeight(), base.getIngredients(), base.getResultItem(RegistryAccess.EMPTY).copy(), base.acceptsMirrored());
        if (!BulkPartSettings.WHEEL.equals(function) || getWidth() > 9 || getHeight() > 9 || parts.size() != 2)
            throw new IllegalArgumentException("Invalid wheel recipe");
        var slots = new HashSet<Integer>(); var roles = new HashSet<String>();
        for (var part : parts) if (part.slot() < 0 || part.slot() >= getWidth() * getHeight() || getIngredients().get(part.slot()).isEmpty()
                || !slots.add(part.slot()) || !roles.add(part.role())) throw new IllegalArgumentException("Invalid wheel part slot");
        if (!roles.equals(java.util.Set.of("hammer", "plate"))) throw new IllegalArgumentException("Invalid wheel roles");
        this.parts = List.copyOf(parts); this.function = function;
    }
    @Override public RecipeSerializer<?> getSerializer() { return com.bettercontent.manufacturing.recipe.ManufacturingRecipes.BULK_WHEEL_VARIANT.get(); }
    @Override public boolean matches(CraftingContainer grid, Level level) {
        return super.matches(grid, level) && !assemble(grid, level.registryAccess()).isEmpty();
    }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        for (int y = 0; y <= grid.getHeight() - getHeight(); y++) for (int x = 0; x <= grid.getWidth() - getWidth(); x++) {
            if (!matchesAt(grid, x, y)) continue;
            var inputs = new LinkedHashMap<String, ItemStack>();
            for (var part : parts) inputs.put(part.role(), grid.getItem(x + part.slot() % getWidth() + (y + part.slot() / getWidth()) * grid.getWidth()));
            var output = super.assemble(grid, access);
            var id = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (id == null || output.getCount() != 2) return ItemStack.EMPTY;
            try {
                SettingsNbt.write(output.getOrCreateTag(), BulkPartSettings.bake(id.toString(), function, inputs));
                return output;
            } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
        }
        return ItemStack.EMPTY;
    }
    private boolean matchesAt(CraftingContainer grid, int sx, int sy) {
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
            int px = x - sx, py = y - sy;
            var expected = px < 0 || py < 0 || px >= getWidth() || py >= getHeight() ? net.minecraft.world.item.crafting.Ingredient.EMPTY
                    : getIngredients().get(px + py * getWidth());
            if (!expected.test(grid.getItem(x + y * grid.getWidth()))) return false;
        }
        return true;
    }
    public List<Part> parts() { return parts; }
    public String function() { return function; }

    public static final class Serializer implements RecipeSerializer<BulkWheelRecipe> {
        private final MechanicalCraftingRecipe.Serializer nativeSerializer = new MechanicalCraftingRecipe.Serializer();
        @Override public BulkWheelRecipe fromJson(ResourceLocation id, JsonObject json) {
            var raw = GsonHelper.getAsJsonArray(json, "pattern");
            if (raw.size() == 0 || raw.size() > 9) throw new IllegalArgumentException("Invalid native wheel pattern");
            int width = raw.get(0).getAsString().length();
            if (width < 1 || width > 9) throw new IllegalArgumentException("Invalid native wheel width");
            int left = width, right = -1, top = raw.size(), bottom = -1;
            for (int y = 0; y < raw.size(); y++) {
                String row = raw.get(y).getAsString();
                if (row.length() != width) throw new IllegalArgumentException("Ragged native wheel pattern");
                for (int x = 0; x < width; x++) if (row.charAt(x) != ' ') {
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
            }
            // Create delegates to ShapedRecipe.Serializer: leading/trailing EMPTY columns
            // and rows are trimmed before its native mechanical recipe is constructed.
            var base = (MechanicalCraftingRecipe) nativeSerializer.fromJson(id, json);
            if (right < left || base.getWidth() != right - left + 1 || base.getHeight() != bottom - top + 1)
                throw new IllegalArgumentException("Wheel pattern does not match native trimmed grid");
            var parts = new ArrayList<Part>();
            for (var entry : GsonHelper.getAsJsonArray(json, "parts")) {
                var part = entry.getAsJsonObject();
                int rawSlot = GsonHelper.getAsInt(part, "slot");
                int y = rawSlot / width, x = rawSlot % width;
                if (rawSlot < 0 || y >= raw.size() || x < left || x > right || y < top || y > bottom
                    || raw.get(y).getAsString().charAt(x) == ' ')
                    throw new IllegalArgumentException("Wheel part outside native occupied pattern");
                parts.add(new Part(GsonHelper.getAsString(part, "role"), (y - top) * base.getWidth() + x - left));
            }
            return new BulkWheelRecipe(base, GsonHelper.getAsString(json, "function"), parts);
        }
        @Override public BulkWheelRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var base = nativeSerializer.fromNetwork(id, buf);
            String function = buf.readUtf(192); int size = buf.readVarInt();
            if (size != 2) throw new IllegalArgumentException("Invalid wheel parts");
            var parts = new ArrayList<Part>();
            for (int i = 0; i < size; i++) parts.add(new Part(buf.readUtf(48), buf.readVarInt()));
            return new BulkWheelRecipe((MechanicalCraftingRecipe) base, function, parts);
        }
        @Override public void toNetwork(FriendlyByteBuf buf, BulkWheelRecipe recipe) {
            nativeSerializer.toNetwork(buf, recipe);
            buf.writeUtf(recipe.function, 192); buf.writeVarInt(recipe.parts.size());
            for (var part : recipe.parts) { buf.writeUtf(part.role(), 48); buf.writeVarInt(part.slot()); }
        }
    }
}
