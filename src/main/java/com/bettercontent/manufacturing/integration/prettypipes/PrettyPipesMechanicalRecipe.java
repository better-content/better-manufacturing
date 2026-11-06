package com.bettercontent.manufacturing.integration.prettypipes;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartSettings;
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
import java.util.Map;

/** Full native module grid plus one physically consumed handle in the mechanical crafter. */
public final class PrettyPipesMechanicalRecipe extends MechanicalCraftingRecipe {
    private final String function;
    private final int handleSlot;
    private final int predecessorSlot;

    public PrettyPipesMechanicalRecipe(MechanicalCraftingRecipe base, String function, int handleSlot, int predecessorSlot) {
        super(base.getId(), base.getGroup(), base.getWidth(), base.getHeight(), base.getIngredients(),
            base.getResultItem(RegistryAccess.EMPTY).copy(), base.acceptsMirrored());
        if (getWidth() != 3 || getHeight() != 4 || acceptsMirrored() || handleSlot != 9
            || predecessorSlot != -1 && predecessorSlot != 4
            || getIngredients().get(handleSlot).isEmpty()
            || predecessorSlot == 4 && getIngredients().get(predecessorSlot).isEmpty()
            || !function.equals("better_manufacturing:prettypipes/speed")
                && !function.equals("better_manufacturing:prettypipes/extraction")
                && !function.equals("better_manufacturing:prettypipes/pressurizer"))
            throw new IllegalArgumentException("Invalid Pretty Pipes mechanical recipe");
        this.function = function;
        this.handleSlot = handleSlot;
        this.predecessorSlot = predecessorSlot;
    }

    @Override public RecipeSerializer<?> getSerializer() {
        return com.bettercontent.manufacturing.recipe.ManufacturingRecipes.PRETTY_PIPES_MECHANICAL.get();
    }
    @Override public boolean matches(CraftingContainer grid, Level level) {
        return super.matches(grid, level) && !assemble(grid, level.registryAccess()).isEmpty();
    }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        for (int y = 0; y <= grid.getHeight() - getHeight(); y++) for (int x = 0; x <= grid.getWidth() - getWidth(); x++) {
            if (!matchesAt(grid, x, y)) continue;
            ItemStack handle = grid.getItem(x + (y + handleSlot / getWidth()) * grid.getWidth() + handleSlot % getWidth());
            ItemStack predecessor = predecessorSlot < 0 ? ItemStack.EMPTY
                : grid.getItem(x + (y + predecessorSlot / getWidth()) * grid.getWidth() + predecessorSlot % getWidth());
            ItemStack output = super.assemble(grid, access);
            var outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
            var predecessorId = ForgeRegistries.ITEMS.getKey(predecessor.getItem());
            if (outputId == null || output.isEmpty()) return ItemStack.EMPTY;
            boolean pressurizer = outputId.toString().equals("prettypipes:pressurizer");
            if (pressurizer != (predecessorSlot == -1)
                || pressurizer != function.equals("better_manufacturing:prettypipes/pressurizer")) return ItemStack.EMPTY;
            if (!pressurizer) {
                String expected = switch (outputId.toString()) {
                    case "prettypipes:medium_speed_module" -> "prettypipes:low_speed_module";
                    case "prettypipes:high_speed_module" -> "prettypipes:medium_speed_module";
                    case "prettypipes:high_extraction_module" -> "prettypipes:medium_extraction_module";
                    default -> null;
                };
                if (predecessorId == null || !predecessorId.toString().equals(expected)) return ItemStack.EMPTY;
                if (predecessor.hasTag()) output.setTag(predecessor.getTag().copy());
            }
            try {
                SettingsNbt.write(output.getOrCreateTag(), PartSettings.bake(outputId.toString(), function,
                    Map.of(pressurizer ? "working_assembly" : "actuator", handle)));
                return output;
            } catch (IllegalArgumentException unsupported) { return ItemStack.EMPTY; }
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
    public static final class Serializer implements RecipeSerializer<PrettyPipesMechanicalRecipe> {
        private final MechanicalCraftingRecipe.Serializer nativeSerializer = new MechanicalCraftingRecipe.Serializer();
        @Override public PrettyPipesMechanicalRecipe fromJson(ResourceLocation id, JsonObject json) {
            var base = (MechanicalCraftingRecipe) nativeSerializer.fromJson(id, json);
            return new PrettyPipesMechanicalRecipe(base, GsonHelper.getAsString(json, "function"),
                GsonHelper.getAsInt(json, "handle_slot"), GsonHelper.getAsInt(json, "predecessor_slot", -1));
        }
        @Override public PrettyPipesMechanicalRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var base = (MechanicalCraftingRecipe) nativeSerializer.fromNetwork(id, buf);
            return new PrettyPipesMechanicalRecipe(base, buf.readUtf(192), buf.readVarInt(), buf.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buf, PrettyPipesMechanicalRecipe recipe) {
            nativeSerializer.toNetwork(buf, recipe);
            buf.writeUtf(recipe.function, 192);
            buf.writeVarInt(recipe.handleSlot);
            buf.writeVarInt(recipe.predecessorSlot);
        }
    }
}
