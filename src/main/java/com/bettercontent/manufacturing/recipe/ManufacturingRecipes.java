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
    public static final RegistryObject<RecipeSerializer<MaterialShapelessRecipe>> SHAPELESS = SERIALIZERS.register("material_shapeless", ShapelessSerializer::new);
    public static final RegistryObject<RecipeSerializer<DrillRetoolingRecipe>> DRILL_RETOOL = SERIALIZERS.register("retool_drill_head",
        () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(DrillRetoolingRecipe::new));
    public static final RegistryObject<RecipeSerializer<MeshRetoolingRecipe>> MESH_RETOOL = SERIALIZERS.register("retool_mesh_frame",
        () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(MeshRetoolingRecipe::new));
    public static final RegistryObject<RecipeSerializer<PartVariantRecipe>> VARIANT = SERIALIZERS.register("part_variant", PartVariantSerializer::new);
    // Create mechanical crafting classes are absent in the optional no-Create fixture.
    public static final RegistryObject<RecipeSerializer<com.bettercontent.manufacturing.integration.createbulk.BulkWheelRecipe>> BULK_WHEEL_VARIANT =
        net.minecraftforge.fml.ModList.get().isLoaded("create") ? SERIALIZERS.register("bulk_wheel_variant", com.bettercontent.manufacturing.integration.createbulk.BulkWheelRecipe.Serializer::new) : null;
    public static final RegistryObject<RecipeSerializer<com.bettercontent.manufacturing.integration.trackwork.TrackworkMechanicalRecipe>> TRACKWORK_MECHANICAL =
        net.minecraftforge.fml.ModList.get().isLoaded("create") && net.minecraftforge.fml.ModList.get().isLoaded("trackwork")
            ? SERIALIZERS.register("trackwork_mechanical", () -> com.bettercontent.manufacturing.integration.trackwork.TrackworkMechanicalRecipe.TrackworkMechanicalSerializer.INSTANCE) : null;
    public static final RegistryObject<RecipeSerializer<com.bettercontent.manufacturing.integration.powergrid.PowergridMechanicalRecipe>> POWERGRID_MECHANICAL_VARIANT =
        net.minecraftforge.fml.ModList.get().isLoaded("create") && net.minecraftforge.fml.ModList.get().isLoaded("powergrid")
            ? SERIALIZERS.register("powergrid_mechanical_variant", com.bettercontent.manufacturing.integration.powergrid.PowergridMechanicalRecipe.Serializer::new) : null;
    public static final RegistryObject<RecipeSerializer<com.bettercontent.manufacturing.integration.clockwork.ClockworkMechanicalRecipe>> CLOCKWORK_MECHANICAL =
        net.minecraftforge.fml.ModList.get().isLoaded("create") && net.minecraftforge.fml.ModList.get().isLoaded("vs_clockwork")
            ? SERIALIZERS.register("clockwork_mechanical", () -> com.bettercontent.manufacturing.integration.clockwork.ClockworkMechanicalRecipe.Serializer.INSTANCE) : null;
    private ManufacturingRecipes() {}
    public static final class ShapelessSerializer implements RecipeSerializer<MaterialShapelessRecipe> {
        private final net.minecraft.world.item.crafting.ShapelessRecipe.Serializer vanilla = new net.minecraft.world.item.crafting.ShapelessRecipe.Serializer();
        @Override public MaterialShapelessRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                var array = GsonHelper.getAsJsonArray(json, "transfers");
                if (array.size() < 1 || array.size() > 9) throw new IllegalArgumentException("Invalid transfer count");
                var bindings = new ArrayList<MaterialShapelessRecipe.Binding>();
                for (var element : array) {
                    var binding = GsonHelper.convertToJsonObject(element, "transfer");
                    bindings.add(new MaterialShapelessRecipe.Binding(GsonHelper.getAsString(binding, "role"), GsonHelper.getAsInt(binding, "ingredient")));
                }
                return new MaterialShapelessRecipe(vanilla.fromJson(id, json), GsonHelper.getAsString(json, "function"), bindings, RecipeQualifications.fromJson(json));
            } catch (IllegalArgumentException invalid) { throw new JsonParseException("Invalid manufacturing recipe " + id, invalid); }
        }
        @Override public MaterialShapelessRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            var base = vanilla.fromNetwork(id, buffer);
            String function = buffer.readUtf(192);
            int count = buffer.readVarInt();
            if (count < 1 || count > 9) throw new IllegalArgumentException("Invalid transfer count");
            var bindings = new ArrayList<MaterialShapelessRecipe.Binding>();
            for (int i = 0; i < count; i++) bindings.add(new MaterialShapelessRecipe.Binding(buffer.readUtf(48), buffer.readVarInt()));
            return new MaterialShapelessRecipe(base, function, bindings, RecipeQualifications.read(buffer));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, MaterialShapelessRecipe recipe) {
            vanilla.toNetwork(buffer, recipe.base()); buffer.writeUtf(recipe.function(), 192); buffer.writeVarInt(recipe.bindings().size());
            for (var binding : recipe.bindings()) { buffer.writeUtf(binding.role(), 48); buffer.writeVarInt(binding.ingredient()); }
            RecipeQualifications.write(buffer, recipe.qualifications());
        }
    }
    public static final class Serializer implements RecipeSerializer<MaterialShapedRecipe> {
        private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();
        @Override public MaterialShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                var transfers = new ArrayList<MaterialShapedRecipe.Transfer>();
                var array = GsonHelper.getAsJsonArray(json, "transfers");
                if (array.size() > com.bettercontent.manufacturing.api.MachineBuild.MAX_ROLES) throw new JsonParseException("Too many provenance transfers");
                for (var element : array) {
                    var transfer = GsonHelper.convertToJsonObject(element, "transfer");
                    transfers.add(new MaterialShapedRecipe.Transfer(GsonHelper.getAsString(transfer, "role"), GsonHelper.getAsInt(transfer, "slot"),
                        GsonHelper.getAsString(transfer, "source_role", "part"), GsonHelper.getAsString(transfer, "source_function", null)));
                }
                return new MaterialShapedRecipe(vanilla.fromJson(id, json), GsonHelper.getAsString(json, "function"), transfers, RecipeQualifications.fromJson(json), RecipeQualifications.movesFromJson(json));
            } catch (IllegalArgumentException invalid) { throw new JsonParseException("Invalid manufacturing recipe " + id, invalid); }
        }
        @Override public MaterialShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapedRecipe base = vanilla.fromNetwork(id, buffer);
            String function = buffer.readUtf(192);
            int count = buffer.readVarInt();
            if (count < 1 || count > com.bettercontent.manufacturing.api.MachineBuild.MAX_ROLES) throw new IllegalArgumentException("Invalid transfer count");
            var transfers = new ArrayList<MaterialShapedRecipe.Transfer>();
            for (int i = 0; i < count; i++) transfers.add(new MaterialShapedRecipe.Transfer(buffer.readUtf(48), buffer.readVarInt(), buffer.readUtf(48), buffer.readBoolean() ? buffer.readUtf(192) : null));
            return new MaterialShapedRecipe(base, function, transfers, RecipeQualifications.read(buffer), RecipeQualifications.readMoves(buffer));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, MaterialShapedRecipe recipe) {
            vanilla.toNetwork(buffer, recipe.base());
            buffer.writeUtf(recipe.function(), 192); buffer.writeVarInt(recipe.transfers().size());
            for (var transfer : recipe.transfers()) {
                buffer.writeUtf(transfer.role(), 48); buffer.writeVarInt(transfer.slot()); buffer.writeUtf(transfer.sourceRole(), 48);
                buffer.writeBoolean(transfer.sourceFunction() != null);
                if (transfer.sourceFunction() != null) buffer.writeUtf(transfer.sourceFunction(), 192);
            }
            RecipeQualifications.write(buffer, recipe.qualifications()); RecipeQualifications.writeMoves(buffer, recipe.inheritedQualifications());
        }
    }
}
