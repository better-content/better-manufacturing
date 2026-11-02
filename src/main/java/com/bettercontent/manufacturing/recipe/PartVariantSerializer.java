package com.bettercontent.manufacturing.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.*;
import java.util.ArrayList;

public final class PartVariantSerializer implements RecipeSerializer<PartVariantRecipe> {
    private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();
    @Override public PartVariantRecipe fromJson(ResourceLocation id, JsonObject json) {
        var entries = GsonHelper.getAsJsonArray(json, "parts");
        if (entries.size() < 1 || entries.size() > 9) throw new IllegalArgumentException("Invalid part count");
        var parts = new ArrayList<PartVariantRecipe.Part>();
        for (var entry : entries) {
            var part = entry.getAsJsonObject();
            parts.add(new PartVariantRecipe.Part(GsonHelper.getAsString(part, "role"), GsonHelper.getAsInt(part, "slot")));
        }
        return new PartVariantRecipe(vanilla.fromJson(id, json), GsonHelper.getAsString(json, "function"), parts);
    }
    @Override public PartVariantRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        var base = vanilla.fromNetwork(id, buffer); String function = buffer.readUtf(192);
        int count = buffer.readVarInt();
        if (count < 1 || count > 9) throw new IllegalArgumentException("Invalid part count");
        var parts = new ArrayList<PartVariantRecipe.Part>();
        for (int i = 0; i < count; i++) parts.add(new PartVariantRecipe.Part(buffer.readUtf(48), buffer.readVarInt()));
        return new PartVariantRecipe(base, function, parts);
    }
    @Override public void toNetwork(FriendlyByteBuf buffer, PartVariantRecipe recipe) {
        vanilla.toNetwork(buffer, recipe.base()); buffer.writeUtf(recipe.function(), 192); buffer.writeVarInt(recipe.parts().size());
        for (var part : recipe.parts()) { buffer.writeUtf(part.role(), 48); buffer.writeVarInt(part.slot()); }
    }
}
