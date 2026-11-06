package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartCapture;
import com.google.gson.JsonObject;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;

/** Native Create mechanical crafter keeps all nine aerospace gates and consumes two working parts. */
public final class ClockworkMechanicalRecipe extends MechanicalCraftingRecipe {
    public static final ResourceLocation ID = new ResourceLocation("better_manufacturing", "clockwork_mechanical");
    public ClockworkMechanicalRecipe(MechanicalCraftingRecipe nativeRecipe) {
        super(nativeRecipe.getId(), nativeRecipe.getGroup(), nativeRecipe.getWidth(), nativeRecipe.getHeight(),
            nativeRecipe.getIngredients(), nativeRecipe.getResultItem(RegistryAccess.EMPTY).copy(), nativeRecipe.acceptsMirrored());
    }
    @Override public RecipeSerializer<?> getSerializer() { return Serializer.INSTANCE; }
    @Override public boolean matches(CraftingContainer inventory, Level level) {
        return super.matches(inventory, level) && !assemble(inventory, level.registryAccess()).isEmpty();
    }
    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess registries) {
        ItemStack result = super.assemble(inventory, registries);
        if (result.isEmpty() || !"vs_clockwork:gas_thruster".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(result.getItem()))))
            return ItemStack.EMPTY;
        var parts = new LinkedHashMap<String, ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof ToolPartItem)) continue;
            String role = switch (String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                case "tconstruct:large_plate" -> "plate";
                case "tconstruct:tough_handle" -> "handle";
                default -> "invalid";
            };
            if (role.equals("invalid") || parts.putIfAbsent(role, stack) != null) return ItemStack.EMPTY;
        }
        try { SettingsNbt.write(result.getOrCreateTag(), NativePartCapture.bake(ClockworkPartSettings.GAS, parts,
            () -> ClockworkPartSettings.bake("vs_clockwork:gas_thruster", ClockworkPartSettings.GAS, parts)));
            return result;
        } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
    }
    public static final class Serializer implements RecipeSerializer<ClockworkMechanicalRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        private final MechanicalCraftingRecipe.Serializer nativeSerializer = new MechanicalCraftingRecipe.Serializer();
        @Override public ClockworkMechanicalRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ClockworkMechanicalRecipe((MechanicalCraftingRecipe) nativeSerializer.fromJson(id, json));
        }
        @Override public ClockworkMechanicalRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ClockworkMechanicalRecipe((MechanicalCraftingRecipe) nativeSerializer.fromNetwork(id, buffer));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, ClockworkMechanicalRecipe recipe) {
            nativeSerializer.toNetwork(buffer, recipe);
        }
    }
}
