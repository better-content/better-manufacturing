package com.bettercontent.manufacturing.integration.pneumatictools;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.ManufacturingRecipes;
import com.bettercontent.manufacturing.recipe.PartSettings;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
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

import java.util.Map;

/** Pays one real pick head while retaining the exact native material item for thermo work. */
public final class PneuDrillBitSourceRecipe extends CustomRecipe {
    private final String output;
    private final TagKey<Item> material;
    private final boolean netherite;

    public PneuDrillBitSourceRecipe(ResourceLocation id, String output, ResourceLocation material) {
        super(id, CraftingBookCategory.MISC);
        this.netherite = output.equals(PneuDrillBitSource.NETHERITE);
        if (!netherite && !output.equals("pneumaticcraft:drill_bit_iron")
            && !output.equals("pneumaticcraft:drill_bit_compressed_iron")
            && !output.equals(PneuDrillBitSource.DIAMOND)) throw new IllegalArgumentException("Unknown drill bit");
        ResourceLocation expected = new ResourceLocation(switch (output) {
            case "pneumaticcraft:drill_bit_iron" -> "forge:ingots/iron";
            case "pneumaticcraft:drill_bit_compressed_iron" -> "forge:ingots/compressed_iron";
            case "pneumaticcraft:drill_bit_diamond" -> "forge:storage_blocks/diamond";
            default -> "forge:ingots/netherite";
        });
        if (!expected.equals(material)) throw new IllegalArgumentException("Wrong drill-bit material tag");
        this.output = output;
        this.material = TagKey.create(Registries.ITEM, material);
    }

    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.PNEU_DRILL_BIT_SOURCE.get(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public boolean matches(CraftingContainer grid, Level level) { return !assemble(grid, level.registryAccess()).isEmpty(); }

    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        ItemStack source = ItemStack.EMPTY, head = ItemStack.EMPTY;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(material) && source.isEmpty()) source = stack;
            else if (!netherite && PneuDrillBitSource.id(stack).equals("tconstruct:pick_head") && head.isEmpty()) head = stack;
            else if (netherite && PneuDrillBitSource.id(stack).equals(PneuDrillBitSource.DIAMOND) && head.isEmpty()) head = stack;
            else return ItemStack.EMPTY;
        }
        if (source.isEmpty() || head.isEmpty() || source.getCount() != 1 || head.getCount() != 1) return ItemStack.EMPTY;
        if (netherite) {
            var settings = SettingsNbt.read(head.getTag(), PneuDrillBitSource.DIAMOND);
            if (!PneuDrillBitSource.valid(settings, PneuDrillBitSource.DIAMOND)) return ItemStack.EMPTY;
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(PneuDrillBitSource.UNASSEMBLED));
            if (item == null) return ItemStack.EMPTY;
            ItemStack result = new ItemStack(item);
            var tag = head.getTag().copy();
            tag.putString(PneuDrillBitSource.MARKER, PneuDrillBitSource.DIAMOND);
            result.setTag(tag);
            return result;
        }
        if (source.hasTag() && (source.getTag().contains(PneuDrillBitSource.MARKER)
            || source.getTag().contains(SettingsNbt.KEY))) return ItemStack.EMPTY;
        try {
            var settings = PartSettings.bake(output, PneumatictoolsSettings.FUNCTION, Map.of("head", head));
            ItemStack result = source.copy();
            result.setCount(1);
            var tag = result.getOrCreateTag();
            tag.putString(PneuDrillBitSource.MARKER, output);
            SettingsNbt.write(tag, settings);
            return result;
        } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
    }

    public static final class Serializer implements RecipeSerializer<PneuDrillBitSourceRecipe> {
        @Override public PneuDrillBitSourceRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new PneuDrillBitSourceRecipe(id, GsonHelper.getAsString(json, "output"),
                new ResourceLocation(GsonHelper.getAsString(json, "material_tag")));
        }
        @Override public PneuDrillBitSourceRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new PneuDrillBitSourceRecipe(id, buffer.readUtf(96), buffer.readResourceLocation());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, PneuDrillBitSourceRecipe recipe) {
            buffer.writeUtf(recipe.output, 96);
            buffer.writeResourceLocation(recipe.material.location());
        }
    }
}
