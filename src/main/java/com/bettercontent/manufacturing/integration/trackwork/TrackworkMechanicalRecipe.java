package com.bettercontent.manufacturing.integration.trackwork;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.google.gson.JsonObject;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;

/** Native Create mechanical-crafter recipe, retaining its 5x5 pattern and output count. */
public final class TrackworkMechanicalRecipe extends MechanicalCraftingRecipe {
    public static final ResourceLocation ID = new ResourceLocation("better_manufacturing", "trackwork_mechanical");
    public TrackworkMechanicalRecipe(MechanicalCraftingRecipe base) {
        super(base.getId(), base.getGroup(), base.getWidth(), base.getHeight(), base.getIngredients(),
            base.getResultItem(RegistryAccess.EMPTY).copy(), base.acceptsMirrored());
    }
    @Override public RecipeSerializer<?> getSerializer() { return TrackworkMechanicalSerializer.INSTANCE; }
    @Override public boolean matches(CraftingContainer inventory, Level level) {
        return super.matches(inventory, level) && !assemble(inventory, level.registryAccess()).isEmpty();
    }
    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess registries) {
        ItemStack result = super.assemble(inventory, registries);
        if (result.isEmpty()) return result;
        ItemStack hub = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.getItem() instanceof ToolPartItem &&
                (String.valueOf(ForgeRegistries.ITEMS.getKey(candidate.getItem())).equals("tconstruct:tough_handle") ||
                 String.valueOf(ForgeRegistries.ITEMS.getKey(candidate.getItem())).equals("tconstruct:bow_limb"))) {
                if (!hub.isEmpty()) return ItemStack.EMPTY;
                hub = candidate;
            }
        }
        if (!hub.isEmpty()) {
            try { SettingsNbt.write(result.getOrCreateTag(), TrackworkSettings.bake(
                String.valueOf(ForgeRegistries.ITEMS.getKey(result.getItem())), TrackworkSettings.FUNCTION, Map.of("hub_suspension", hub)));
            } catch (IllegalArgumentException | java.util.NoSuchElementException invalid) { return ItemStack.EMPTY; }
        } else return ItemStack.EMPTY;
        return result;
    }

    public static final class TrackworkMechanicalSerializer implements RecipeSerializer<TrackworkMechanicalRecipe> {
        public static final TrackworkMechanicalSerializer INSTANCE = new TrackworkMechanicalSerializer();
        private final MechanicalCraftingRecipe.Serializer nativeSerializer = new MechanicalCraftingRecipe.Serializer();
        @Override public TrackworkMechanicalRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new TrackworkMechanicalRecipe((MechanicalCraftingRecipe) nativeSerializer.fromJson(id, json));
        }
        @Override public TrackworkMechanicalRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new TrackworkMechanicalRecipe((MechanicalCraftingRecipe) nativeSerializer.fromNetwork(id, buffer));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, TrackworkMechanicalRecipe recipe) {
            nativeSerializer.toNetwork(buffer, recipe);
        }
    }
}
