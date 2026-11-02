package com.bettercontent.manufacturing.integration.powergrid;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.google.gson.JsonObject;
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
import java.util.*;

/** Keeps Create's mechanical-crafter recipe type, grid and mirroring, baking only the selected parts. */
public final class PowergridMechanicalRecipe extends MechanicalCraftingRecipe {
    public record Part(String role, int slot) {}
    private final String function;
    private final List<Part> parts;
    public PowergridMechanicalRecipe(MechanicalCraftingRecipe base, String function, List<Part> parts) {
        super(base.getId(), base.getGroup(), base.getWidth(), base.getHeight(), base.getIngredients(),
            base.getResultItem(RegistryAccess.EMPTY).copy(), base.acceptsMirrored());
        if (parts.isEmpty() || parts.size() > 9 || getWidth() > 9 || getHeight() > 9 || getWidth() * getHeight() > 81)
            throw new IllegalArgumentException("Invalid mechanical parts");
        var slots = new HashSet<Integer>(); var roles = new HashSet<String>();
        for (Part part : parts) if (part.slot < 0 || part.slot >= getWidth() * getHeight()
            || getIngredients().get(part.slot).isEmpty() || !slots.add(part.slot) || !roles.add(part.role))
            throw new IllegalArgumentException("Invalid mechanical part slot");
        this.function = function; this.parts = List.copyOf(parts);
    }
    @Override public RecipeSerializer<?> getSerializer() { return com.bettercontent.manufacturing.recipe.ManufacturingRecipes.POWERGRID_MECHANICAL_VARIANT.get(); }
    @Override public boolean matches(CraftingContainer grid, Level level) {
        return super.matches(grid, level) && !assemble(grid, level.registryAccess()).isEmpty();
    }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        for (int y = 0; y <= grid.getHeight() - getHeight(); y++) for (int x = 0; x <= grid.getWidth() - getWidth(); x++) {
            for (boolean mirrored : new boolean[]{false, true}) {
                if (mirrored && !acceptsMirrored() || !matchesAt(grid, x, y, mirrored)) continue;
                var inputs = new LinkedHashMap<String, ItemStack>();
                for (Part part : parts) {
                    int px = part.slot % getWidth(), py = part.slot / getWidth();
                    inputs.put(part.role, grid.getItem(x + (mirrored ? getWidth() - 1 - px : px) + (y + py) * grid.getWidth()));
                }
                var output = super.assemble(grid, access);
                var id = ForgeRegistries.ITEMS.getKey(output.getItem());
                if (id == null || output.isEmpty()) return ItemStack.EMPTY;
                try {
                    SettingsNbt.write(output.getOrCreateTag(), PowergridPartSettings.bake(id.toString(), function, inputs));
                    return output;
                } catch (IllegalArgumentException unsupported) { return ItemStack.EMPTY; }
            }
        }
        return ItemStack.EMPTY;
    }
    private boolean matchesAt(CraftingContainer grid, int sx, int sy, boolean mirrored) {
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
            int px = x - sx, py = y - sy;
            Ingredient expected = px < 0 || py < 0 || px >= getWidth() || py >= getHeight() ? Ingredient.EMPTY
                : getIngredients().get((mirrored ? getWidth() - 1 - px : px) + py * getWidth());
            if (!expected.test(grid.getItem(x + y * grid.getWidth()))) return false;
        }
        return true;
    }
    public static final class Serializer implements RecipeSerializer<PowergridMechanicalRecipe> {
        private final MechanicalCraftingRecipe.Serializer nativeSerializer = new MechanicalCraftingRecipe.Serializer();
        @Override public PowergridMechanicalRecipe fromJson(ResourceLocation id, JsonObject json) {
            var base = (MechanicalCraftingRecipe) nativeSerializer.fromJson(id, json);
            var parts = new ArrayList<Part>();
            for (var e : GsonHelper.getAsJsonArray(json, "parts")) {
                var p = e.getAsJsonObject(); parts.add(new Part(GsonHelper.getAsString(p, "role"), GsonHelper.getAsInt(p, "slot")));
            }
            return new PowergridMechanicalRecipe(base, GsonHelper.getAsString(json, "function"), parts);
        }
        @Override public PowergridMechanicalRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var base = (MechanicalCraftingRecipe) nativeSerializer.fromNetwork(id, buf);
            String function = buf.readUtf(192); int count = buf.readVarInt();
            if (count < 1 || count > 9) throw new IllegalArgumentException("Invalid part count");
            var parts = new ArrayList<Part>();
            for (int i = 0; i < count; i++) parts.add(new Part(buf.readUtf(48), buf.readVarInt()));
            return new PowergridMechanicalRecipe(base, function, parts);
        }
        @Override public void toNetwork(FriendlyByteBuf buf, PowergridMechanicalRecipe recipe) {
            nativeSerializer.toNetwork(buf, recipe);
            buf.writeUtf(recipe.function, 192); buf.writeVarInt(recipe.parts.size());
            for (Part part : recipe.parts) { buf.writeUtf(part.role, 48); buf.writeVarInt(part.slot); }
        }
    }
}
