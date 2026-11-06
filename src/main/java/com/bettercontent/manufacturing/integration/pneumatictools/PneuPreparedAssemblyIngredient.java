package com.bettercontent.manufacturing.integration.pneumatictools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/** Only the configured diamond-bit upgrade can enter the native netherite drill program. */
public final class PneuPreparedAssemblyIngredient extends AbstractIngredient {
    public static final IIngredientSerializer<PneuPreparedAssemblyIngredient> SERIALIZER = new Serializer();

    public PneuPreparedAssemblyIngredient() {
        super(Stream.of(new Ingredient.ItemValue(new ItemStack(
            ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(PneuDrillBitSource.UNASSEMBLED))))));
    }

    @Override public boolean test(@Nullable ItemStack input) {
        return input != null && PneuDrillBitSource.assemblyInput(input);
    }
    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return SERIALIZER; }
    @Override public JsonElement toJson() {
        var json = new JsonObject();
        json.addProperty("type", "better_manufacturing:pneu_prepared_assembly");
        return json;
    }

    private static final class Serializer implements IIngredientSerializer<PneuPreparedAssemblyIngredient> {
        @Override public PneuPreparedAssemblyIngredient parse(JsonObject json) { return new PneuPreparedAssemblyIngredient(); }
        @Override public PneuPreparedAssemblyIngredient parse(FriendlyByteBuf buffer) { return new PneuPreparedAssemblyIngredient(); }
        @Override public void write(FriendlyByteBuf buffer, PneuPreparedAssemblyIngredient ingredient) {}
    }
}
