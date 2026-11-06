package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.recipes.special.CompressorUpgradeCrafting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve PNCR's native upgrade serializer and its BlockEntityTag; carry the consumed compressor's construction. */
@Mixin(value = CompressorUpgradeCrafting.class, remap = false)
public abstract class PneupressureUpgradeCraftMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), remap = true)
    private void pneupressure$inherit(CraftingContainer grid, RegistryAccess access, CallbackInfoReturnable<ItemStack> cir) {
        var output = cir.getReturnValue();
        if (output.isEmpty()) return;
        var outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
        if (outputId == null || !(outputId.toString().equals("pneumaticcraft:advanced_air_compressor")
            || outputId.toString().equals("pneumaticcraft:advanced_liquid_compressor"))) return;
        String source = outputId.toString().equals("pneumaticcraft:advanced_air_compressor")
            ? "pneumaticcraft:air_compressor" : "pneumaticcraft:liquid_compressor";
        for (int i = 0; i < grid.getContainerSize(); i++) {
            var stack = grid.getItem(i);
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !id.toString().equals(source)) continue;
            var settings = SettingsNbt.read(stack.getTag(), source);
            if (settings == null && stack.getTag() != null)
                settings = SettingsNbt.read(stack.getTag().getCompound("BlockEntityTag"), source);
            if (settings == null || !settings.properties().keySet().equals(java.util.Set.of("compression_rate", "pressure_envelope"))) return;
            double rate = settings.properties().get("compression_rate");
            double envelope = settings.properties().get("pressure_envelope");
            if (!Double.isFinite(rate) || rate < .75 || rate > 1.5
                || !Double.isFinite(envelope) || envelope < .75 || envelope > 1) return;
            // Same physical working parts are upgraded, not replaced. NBT output identity is rewritten.
            SettingsNbt.write(output.getOrCreateTag(), SettingsNbt.inheritCompressor(outputId.toString(), settings));
            return;
        }
    }
}
