package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.ManufacturingRecipes;
import com.bettercontent.manufacturing.recipe.PartSettings;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One tagged plank pays every working pair before Clockwork's native 100-tick saw pass. */
public final class ClockworkBladeCuttingPrep extends CustomRecipe {
    public static final String MARKER = "better_manufacturing:clockwork_blade_cutting";
    private static final TagKey<Item> PLANKS = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft:planks"));
    private final String output;
    private final int count;

    public ClockworkBladeCuttingPrep(ResourceLocation id, String output) {
        super(id, CraftingBookCategory.MISC);
        this.output = output;
        this.count = output.equals("vs_clockwork:propeller_blade") ? 3
            : output.equals("vs_clockwork:wide_propeller_blade") ? 2 : 0;
        if (count == 0) throw new IllegalArgumentException("Unknown Clockwork blade cutting output");
    }

    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.CLOCKWORK_BLADE_CUTTING_PREP.get(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= count * 2 + 1; }
    @Override public boolean matches(CraftingContainer grid, Level level) { return !assemble(grid, level.registryAccess()).isEmpty(); }

    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        ItemStack plank = ItemStack.EMPTY;
        var blades = new ArrayList<ItemStack>();
        var handles = new ArrayList<ItemStack>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(PLANKS) && plank.isEmpty()) plank = stack;
            else if (id(stack).equals("tconstruct:broad_blade")) blades.add(stack);
            else if (id(stack).equals("tconstruct:tough_handle")) handles.add(stack);
            else return ItemStack.EMPTY;
        }
        if (plank.isEmpty() || blades.size() != count || handles.size() != count
            || plank.hasTag() && plank.getTag().contains(MARKER)) return ItemStack.EMPTY;
        try {
            var settings = new ListTag();
            for (int i = 0; i < count; i++) {
                MachineSettings baked = PartSettings.bake(output, ClockworkPartSettings.FLIGHT,
                    Map.of("blade", blades.get(i), "handle", handles.get(i)));
                var holder = new CompoundTag();
                SettingsNbt.write(holder, baked);
                settings.add(holder.getCompound(SettingsNbt.KEY).copy());
            }
            ItemStack result = plank.copy();
            result.setCount(1);
            var marker = new CompoundTag();
            marker.putString("output", output);
            marker.put("settings", settings);
            result.getOrCreateTag().put(MARKER, marker);
            return result;
        } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
    }

    public static List<MachineSettings> paidSettings(ItemStack source, String output) {
        int expected = output.equals("vs_clockwork:propeller_blade") ? 3
            : output.equals("vs_clockwork:wide_propeller_blade") ? 2 : 0;
        if (expected == 0 || !source.is(PLANKS) || !source.hasTag()
            || !source.getTag().contains(MARKER, Tag.TAG_COMPOUND)) return List.of();
        var marker = source.getTag().getCompound(MARKER);
        if (!marker.getString("output").equals(output)
            || !marker.contains("settings", Tag.TAG_LIST)) return List.of();
        var saved = marker.getList("settings", Tag.TAG_COMPOUND);
        if (saved.size() != expected) return List.of();
        var result = new ArrayList<MachineSettings>(expected);
        for (int i = 0; i < expected; i++) {
            var holder = new CompoundTag();
            holder.put(SettingsNbt.KEY, saved.getCompound(i).copy());
            var rating = SettingsNbt.read(holder, output);
            if (rating == null || !rating.properties().keySet().equals(java.util.Set.of("actuator_response"))) return List.of();
            result.add(rating);
        }
        return List.copyOf(result);
    }

    private static String id(ItemStack stack) { return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())); }

    public static final class Serializer implements RecipeSerializer<ClockworkBladeCuttingPrep> {
        @Override public ClockworkBladeCuttingPrep fromJson(ResourceLocation id, JsonObject json) {
            return new ClockworkBladeCuttingPrep(id, GsonHelper.getAsString(json, "output"));
        }
        @Override public ClockworkBladeCuttingPrep fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ClockworkBladeCuttingPrep(id, buffer.readUtf(96));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, ClockworkBladeCuttingPrep recipe) {
            buffer.writeUtf(recipe.output, 96);
        }
    }
}
