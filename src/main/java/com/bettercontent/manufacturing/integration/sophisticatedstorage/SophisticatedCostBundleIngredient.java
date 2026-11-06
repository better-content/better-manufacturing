package com.bettercontent.manufacturing.integration.sophisticatedstorage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/** Requires an exact Sophisticated recipe marker on the same native material. */
public final class SophisticatedCostBundleIngredient extends AbstractIngredient {
    public static final IIngredientSerializer<SophisticatedCostBundleIngredient> SERIALIZER = new Serializer();
    public static final String KEY = "better_manufacturing:sophisticated_cost_bundle";
    private static final Set<String> ROUTE_MARKERS = Set.of(
        "sophisticatedstorage:upgrade_base", "sophisticatedbackpacks:upgrade_base",
        "sophisticatedstorage:controller", "sophisticatedstorage:storage_io"
    );
    private final Ingredient anchor;
    private final String marker;

    public SophisticatedCostBundleIngredient(Ingredient anchor, String marker) {
        super(anchor.getItems().length == 0 ? java.util.stream.Stream.empty()
            : Arrays.stream(anchor.getItems()).map(Ingredient.ItemValue::new));
        if (anchor.isEmpty()) throw new IllegalArgumentException("Empty Sophisticated cost anchor");
        this.anchor = anchor;
        this.marker = requireMarker(marker);
    }

    public static String requireMarker(String marker) {
        ResourceLocation id = ResourceLocation.tryParse(marker);
        if (id == null || !ROUTE_MARKERS.contains(id.toString()))
            throw new IllegalArgumentException("Invalid Sophisticated cost marker");
        return id.toString();
    }

    @Override public boolean test(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag()
            && stack.getTag().getString(KEY).equals(marker) && anchor.test(stack);
    }

    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return SERIALIZER; }
    @Override public JsonElement toJson() {
        var json = new JsonObject();
        json.addProperty("type", KEY);
        json.add("anchor", anchor.toJson());
        json.addProperty("marker", marker);
        return json;
    }

    private static final class Serializer implements IIngredientSerializer<SophisticatedCostBundleIngredient> {
        @Override public SophisticatedCostBundleIngredient parse(JsonObject json) {
            return new SophisticatedCostBundleIngredient(Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "anchor")),
                GsonHelper.getAsString(json, "marker"));
        }
        @Override public SophisticatedCostBundleIngredient parse(FriendlyByteBuf buffer) {
            var anchor = com.google.gson.JsonParser.parseString(buffer.readUtf(4096)).getAsJsonObject();
            return new SophisticatedCostBundleIngredient(Ingredient.fromJson(anchor), buffer.readUtf(128));
        }
        @Override public void write(FriendlyByteBuf buffer, SophisticatedCostBundleIngredient ingredient) {
            buffer.writeUtf(ingredient.anchor.toJson().toString(), 4096);
            buffer.writeUtf(ingredient.marker, 128);
        }
    }
}
