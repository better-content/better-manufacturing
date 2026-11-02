package com.bettercontent.manufacturing.integration.pneutransfer;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Stream;

/** Native strict-empty-tank match, ignoring ONLY the already-consumed manufacturing setting. */
public final class PneuTransferEmptyTankIngredient extends AbstractIngredient {
    public static final IIngredientSerializer<PneuTransferEmptyTankIngredient> SERIALIZER = new Serializer();
    private static final Set<String> TANKS = Set.of("pneumaticcraft:small_tank", "pneumaticcraft:medium_tank", "pneumaticcraft:large_tank");
    private final Item item;

    public PneuTransferEmptyTankIngredient(Item item) {
        super(Stream.of(new Ingredient.ItemValue(new ItemStack(item))));
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(item));
        if (!TANKS.contains(id)) throw new IllegalArgumentException("Not a tier-input tank");
        this.item = item;
    }

    @Override public boolean test(@Nullable ItemStack input) {
        if (input == null || input.isEmpty() || input.getItem() != item) return false;
        String output = String.valueOf(ForgeRegistries.ITEMS.getKey(item));
        var copy = input.copy();
        var tag = copy.getTag();
        if (tag != null) {
            if (tag.contains(SettingsNbt.KEY) && !PneuTransferSettings.valid(SettingsNbt.read(tag, output), output)) return false;
            tag.remove(SettingsNbt.KEY);
            var nativeTag = copy.getTagElement("BlockEntityTag");
            if (nativeTag != null) {
                if (nativeTag.contains(SettingsNbt.KEY) && !PneuTransferSettings.valid(SettingsNbt.read(nativeTag, output), output)) return false;
                nativeTag.remove(SettingsNbt.KEY);
                if (nativeTag.isEmpty()) tag.remove("BlockEntityTag");
            }
            if (tag.isEmpty()) copy.setTag(null);
        }
        // Same native share-tag/capability equality as Forge StrictNBTIngredient; full tanks stay excluded.
        return new ItemStack(item).areShareTagsEqual(copy);
    }
    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return SERIALIZER; }
    @Override public JsonElement toJson() {
        var json = new JsonObject();
        json.addProperty("type", "better_manufacturing:pneu_empty_tank");
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(item).toString());
        return json;
    }

    private static final class Serializer implements IIngredientSerializer<PneuTransferEmptyTankIngredient> {
        private static PneuTransferEmptyTankIngredient of(ResourceLocation id) {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) throw new IllegalArgumentException("Unknown tank: " + id);
            return new PneuTransferEmptyTankIngredient(item);
        }
        @Override public PneuTransferEmptyTankIngredient parse(JsonObject json) {
            return of(new ResourceLocation(GsonHelper.getAsString(json, "item")));
        }
        @Override public PneuTransferEmptyTankIngredient parse(FriendlyByteBuf buffer) {
            return of(buffer.readResourceLocation());
        }
        @Override public void write(FriendlyByteBuf buffer, PneuTransferEmptyTankIngredient ingredient) {
            buffer.writeResourceLocation(ForgeRegistries.ITEMS.getKey(ingredient.item));
        }
    }
}
