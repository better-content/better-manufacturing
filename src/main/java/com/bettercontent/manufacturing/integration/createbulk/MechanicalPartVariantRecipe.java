package com.bettercontent.manufacturing.integration.createbulk;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/** Full native 3x3 cost plus explicit consumed parts in additional Create crafter rows.
 *  Only late-era recipes already downstream of mechanical crafters use this serializer.
 */
public final class MechanicalPartVariantRecipe extends MechanicalCraftingRecipe {
    public record Part(String role, int slot) {}

    private final String function;
    private final List<Part> parts;

    public MechanicalPartVariantRecipe(MechanicalCraftingRecipe nativeRecipe, String function, List<Part> parts) {
        super(nativeRecipe.getId(), nativeRecipe.getGroup(), nativeRecipe.getWidth(), nativeRecipe.getHeight(),
            nativeRecipe.getIngredients(), nativeRecipe.getResultItem(RegistryAccess.EMPTY).copy(), nativeRecipe.acceptsMirrored());
        if (getWidth() != 3 || (getHeight() != 4 && getHeight() != 5) || parts.isEmpty() || parts.size() > 3)
            throw new IllegalArgumentException("Expected a 3x4 or 3x5 cost-preserving craft");
        var slots = new HashSet<Integer>();
        var roles = new HashSet<String>();
        if (parts.stream().noneMatch(part -> part.slot() >= getWidth() * (getHeight() - 1)
            && part.slot() < getWidth() * getHeight()))
            throw new IllegalArgumentException("Final row must consume a working part");
        for (var part : parts) {
            if (part.slot() < 0 || part.slot() >= getWidth() * getHeight() || part.role() == null
                || !part.role().matches("[a-z][a-z0-9_]{0,47}")
                || getIngredients().get(part.slot()).isEmpty()
                || !slots.add(part.slot()) || !roles.add(part.role()))
                throw new IllegalArgumentException("Invalid physical part binding");
        }
        this.function = function;
        this.parts = List.copyOf(parts);
    }

    @Override public RecipeSerializer<?> getSerializer() {
        return com.bettercontent.manufacturing.recipe.ManufacturingRecipes.MECHANICAL_PART_VARIANT.get();
    }

    @Override public boolean matches(CraftingContainer grid, Level level) {
        return super.matches(grid, level) && !assemble(grid, level.registryAccess()).isEmpty();
    }

    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        for (int y = 0; y <= grid.getHeight() - getHeight(); y++) for (int x = 0; x <= grid.getWidth() - getWidth(); x++) {
            if (!matchesAt(grid, x, y)) continue;
            ItemStack output = super.assemble(grid, access);
            var outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (output.isEmpty() || outputId == null) return ItemStack.EMPTY;
            var inputs = new LinkedHashMap<String, ItemStack>();
            for (var part : parts) {
                int px = part.slot() % getWidth(), py = part.slot() / getWidth();
                inputs.put(part.role(), grid.getItem(x + px + (y + py) * grid.getWidth()));
            }
            try {
                SettingsNbt.write(output.getOrCreateTag(), PartSettings.bake(outputId.toString(), function, inputs));
                return output;
            } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
        }
        return ItemStack.EMPTY;
    }

    private boolean matchesAt(CraftingContainer grid, int sx, int sy) {
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
            int px = x - sx, py = y - sy;
            Ingredient expected = px < 0 || py < 0 || px >= getWidth() || py >= getHeight() ? Ingredient.EMPTY
                : getIngredients().get(px + py * getWidth());
            if (!expected.test(grid.getItem(x + y * grid.getWidth()))) return false;
        }
        return true;
    }

    public static final class Serializer implements RecipeSerializer<MechanicalPartVariantRecipe> {
        private final MechanicalCraftingRecipe.Serializer vanilla = new MechanicalCraftingRecipe.Serializer();

        @Override public MechanicalPartVariantRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                var parts = new ArrayList<Part>();
                for (var element : GsonHelper.getAsJsonArray(json, "parts")) {
                    var part = GsonHelper.convertToJsonObject(element, "part");
                    parts.add(new Part(GsonHelper.getAsString(part, "role"), GsonHelper.getAsInt(part, "slot")));
                }
                return new MechanicalPartVariantRecipe((MechanicalCraftingRecipe) vanilla.fromJson(id, json),
                    GsonHelper.getAsString(json, "function"), parts);
            } catch (IllegalArgumentException invalid) {
                throw new JsonParseException("Invalid mechanical part recipe " + id, invalid);
            }
        }

        @Override public MechanicalPartVariantRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var nativeRecipe = (MechanicalCraftingRecipe) vanilla.fromNetwork(id, buf);
            String function = buf.readUtf(192);
            int count = buf.readVarInt();
            if (count < 1 || count > 3) throw new IllegalArgumentException("Invalid mechanical part count");
            var parts = new ArrayList<Part>();
            for (int i = 0; i < count; i++) parts.add(new Part(buf.readUtf(48), buf.readVarInt()));
            return new MechanicalPartVariantRecipe(nativeRecipe, function, parts);
        }

        @Override public void toNetwork(FriendlyByteBuf buf, MechanicalPartVariantRecipe recipe) {
            vanilla.toNetwork(buf, recipe);
            buf.writeUtf(recipe.function, 192);
            buf.writeVarInt(recipe.parts.size());
            for (var part : recipe.parts) { buf.writeUtf(part.role(), 48); buf.writeVarInt(part.slot()); }
        }
    }
}
