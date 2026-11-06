package com.bettercontent.manufacturing.integration.pneumatictools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/** PNCR still matches its own recipe; this ingredient gates stock material on paid head evidence. */
public final class PneuPreparedDrillBitIngredient extends AbstractIngredient {
    public static final IIngredientSerializer<PneuPreparedDrillBitIngredient> SERIALIZER = new Serializer();
    private final String output;
    private final TagKey<Item> material;

    public PneuPreparedDrillBitIngredient(String output, ResourceLocation tag) {
        super(Stream.of(new Ingredient.TagValue(TagKey.create(Registries.ITEM, tag))));
        ResourceLocation expected = new ResourceLocation(switch (output) {
            case "pneumaticcraft:drill_bit_iron" -> "forge:ingots/iron";
            case "pneumaticcraft:drill_bit_compressed_iron" -> "forge:ingots/compressed_iron";
            case "pneumaticcraft:drill_bit_diamond" -> "forge:storage_blocks/diamond";
            default -> throw new IllegalArgumentException("Wrong prepared bit output");
        });
        if (!expected.equals(tag)) throw new IllegalArgumentException("Wrong prepared bit material");
        this.output = output;
        this.material = TagKey.create(Registries.ITEM, tag);
    }

    @Override public boolean test(@Nullable ItemStack input) {
        return input != null && input.is(material) && PneuDrillBitSource.thermoInput(input, output);
    }
    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return SERIALIZER; }
    @Override public JsonElement toJson() {
        var json = new JsonObject();
        json.addProperty("type", "better_manufacturing:pneu_prepared_drill_bit");
        json.addProperty("output", output);
        json.addProperty("tag", material.location().toString());
        return json;
    }

    private static final class Serializer implements IIngredientSerializer<PneuPreparedDrillBitIngredient> {
        @Override public PneuPreparedDrillBitIngredient parse(JsonObject json) {
            return new PneuPreparedDrillBitIngredient(GsonHelper.getAsString(json, "output"),
                new ResourceLocation(GsonHelper.getAsString(json, "tag")));
        }
        @Override public PneuPreparedDrillBitIngredient parse(FriendlyByteBuf buffer) {
            return new PneuPreparedDrillBitIngredient(buffer.readUtf(96), buffer.readResourceLocation());
        }
        @Override public void write(FriendlyByteBuf buffer, PneuPreparedDrillBitIngredient ingredient) {
            buffer.writeUtf(ingredient.output, 96);
            buffer.writeResourceLocation(ingredient.material.location());
        }
    }
}
