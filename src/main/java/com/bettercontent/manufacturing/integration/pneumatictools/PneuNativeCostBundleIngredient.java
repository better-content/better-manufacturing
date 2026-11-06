package com.bettercontent.manufacturing.integration.pneumatictools;

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

import java.util.Set;

/** Requires the exact route marker on the same eligible native material ingredient. */
public final class PneuNativeCostBundleIngredient extends AbstractIngredient {
    public static final IIngredientSerializer<PneuNativeCostBundleIngredient> SERIALIZER = new Serializer();
    public static final String KEY = "better_manufacturing:pneu_native_cost_bundle";
    private static final Set<String> ROUTE_MARKERS = Set.of(
        "pneumaticcraft:electrostatic_compressor", "pneumaticcraft:etching_tank", "pneumaticcraft:fluid_mixer",
        "pneumaticcraft:flux_compressor", "pneumaticcraft:heat_pipe", "pneumaticcraft:heat_sink",
        "pneumaticcraft:huge_tank", "pneumaticcraft:jackhammer", "pneumaticcraft:large_tank",
        "pneumaticcraft:liquid_compressor", "pneumaticcraft:manual_compressor", "pneumaticcraft:medium_tank",
        "pneumaticcraft:pneumatic_cylinder", "pneumaticcraft:pressure_chamber_glass",
        "pneumaticcraft:pressure_chamber_valve", "pneumaticcraft:pressure_chamber_wall",
        "pneumaticcraft:pressure_tube", "pneumaticcraft:refinery", "pneumaticcraft:refinery_output",
        "pneumaticcraft:small_tank", "pneumaticcraft:solar_compressor", "pneumaticcraft:thermal_compressor",
        "pneumaticcraft:tube_junction", "pneumaticcraft:turbine_rotor", "pneumaticcraft:vacuum_pump",
        "pneumaticcraft:vacuum_trap", "pneumaticcraft:vortex_tube"
    );
    private final Ingredient anchor;
    private final String marker;

    public PneuNativeCostBundleIngredient(Ingredient anchor, String marker) {
        super(anchor.getItems().length == 0 ? java.util.stream.Stream.empty()
            : java.util.Arrays.stream(anchor.getItems()).map(Ingredient.ItemValue::new));
        if (anchor.isEmpty()) throw new IllegalArgumentException("Empty native cost anchor");
        this.marker = requireMarker(marker);
        this.anchor = anchor;
    }

    public static String requireMarker(String marker) {
        ResourceLocation id = ResourceLocation.tryParse(marker);
        if (id == null || !ROUTE_MARKERS.contains(id.toString()))
            throw new IllegalArgumentException("Invalid PNCR cost marker");
        return id.toString();
    }

    @Override public boolean test(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && !isTank(stack)
            && stack.hasTag() && stack.getTag().getString(KEY).equals(marker) && anchor.test(stack);
    }

    private static boolean isTank(ItemStack stack) {
        String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
        return id.equals("pneumaticcraft:small_tank") || id.equals("pneumaticcraft:medium_tank")
            || id.equals("pneumaticcraft:large_tank") || id.equals("pneumaticcraft:huge_tank");
    }

    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return SERIALIZER; }
    @Override public JsonElement toJson() {
        var json = new JsonObject();
        json.addProperty("type", "better_manufacturing:pneu_native_cost_bundle");
        json.add("anchor", anchor.toJson());
        json.addProperty("marker", marker);
        return json;
    }

    private static final class Serializer implements IIngredientSerializer<PneuNativeCostBundleIngredient> {
        @Override public PneuNativeCostBundleIngredient parse(JsonObject json) {
            return new PneuNativeCostBundleIngredient(Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "anchor")),
                GsonHelper.getAsString(json, "marker"));
        }
        @Override public PneuNativeCostBundleIngredient parse(FriendlyByteBuf buffer) {
            var anchor = com.google.gson.JsonParser.parseString(buffer.readUtf(4096)).getAsJsonObject();
            return new PneuNativeCostBundleIngredient(Ingredient.fromJson(anchor), buffer.readUtf(128));
        }
        @Override public void write(FriendlyByteBuf buffer, PneuNativeCostBundleIngredient ingredient) {
            buffer.writeUtf(ingredient.anchor.toJson().toString(), 4096);
            buffer.writeUtf(ingredient.marker, 128);
        }
    }
}
