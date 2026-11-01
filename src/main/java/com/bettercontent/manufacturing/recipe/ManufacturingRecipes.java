package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.ArrayList;

public final class ManufacturingRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ManufacturingMod.ID);
    public static final RegistryObject<RecipeSerializer<MaterialShapedRecipe>> SHAPED = SERIALIZERS.register("material_shaped", Serializer::new);
    public static final RegistryObject<RecipeSerializer<DrillRetoolingRecipe>> DRILL_RETOOL = SERIALIZERS.register("retool_drill_head",
        () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(DrillRetoolingRecipe::new));
    private ManufacturingRecipes() {}
    public static final class Serializer implements RecipeSerializer<MaterialShapedRecipe> {
        private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();
        @Override public MaterialShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                var transfers = new ArrayList<MaterialShapedRecipe.Transfer>();
                var array = GsonHelper.getAsJsonArray(json, "transfers");
                if (array.size() > 9) throw new JsonParseException("Too many provenance transfers");
                for (var element : array) {
                    var transfer = GsonHelper.convertToJsonObject(element, "transfer");
                    transfers.add(new MaterialShapedRecipe.Transfer(GsonHelper.getAsString(transfer, "role"), GsonHelper.getAsInt(transfer, "slot")));
                }
                return new MaterialShapedRecipe(vanilla.fromJson(id, json), GsonHelper.getAsString(json, "function"), transfers);
            } catch (IllegalArgumentException invalid) { throw new JsonParseException("Invalid manufacturing recipe " + id, invalid); }
        }
        @Override public MaterialShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapedRecipe base = vanilla.fromNetwork(id, buffer);
            String function = buffer.readUtf(192);
            int count = buffer.readVarInt();
            if (count < 1 || count > 9) throw new IllegalArgumentException("Invalid transfer count");
            var transfers = new ArrayList<MaterialShapedRecipe.Transfer>();
            for (int i = 0; i < count; i++) transfers.add(new MaterialShapedRecipe.Transfer(buffer.readUtf(48), buffer.readVarInt()));
            return new MaterialShapedRecipe(base, function, transfers);
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, MaterialShapedRecipe recipe) {
            vanilla.toNetwork(buffer, recipe.base());
            buffer.writeUtf(recipe.function(), 192); buffer.writeVarInt(recipe.transfers().size());
            for (var transfer : recipe.transfers()) { buffer.writeUtf(transfer.role(), 48); buffer.writeVarInt(transfer.slot()); }
        }
    }
}
