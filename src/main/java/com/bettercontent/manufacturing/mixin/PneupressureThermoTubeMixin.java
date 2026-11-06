package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.Map;

/** The native thermo-plant still tests output capacity, spends 50 mB plastic and applies its heat/pressure gate. */
@Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity", remap = false)
public abstract class PneupressureThermoTubeMixin {
    @Shadow private ThermoPlantRecipe currentRecipe;
    @Shadow private ItemStackHandler inputItemHandler;
    // PNCR's tickServer delegates completed work to runOneCycle. Tag BOTH the
    // simulated output-capacity insertion and the committed insertion before native consumption.
    @ModifyArg(method = "runOneCycle", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/ItemStackHandler;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"), index = 1, require = 2)
    private ItemStack pneupressure$inheritTube(ItemStack result) {
        if (currentRecipe == null || !currentRecipe.getId().toString().equals("pneumaticcraft:thermo_plant/reinforced_pressure_tube")
            || !result.is(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("pneumaticcraft:reinforced_pressure_tube")))) return result;
        var source = inputItemHandler.getStackInSlot(0);
        var sourceId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(source.getItem());
        if (sourceId == null || !sourceId.toString().equals("pneumaticcraft:pressure_tube")) return result;
        var settings = SettingsNbt.read(source.getTag(), sourceId.toString());
        if (settings == null || !settings.properties().keySet().equals(java.util.Set.of("pressure_envelope"))) return result;
        double envelope = settings.properties().get("pressure_envelope");
        if (!Double.isFinite(envelope) || envelope < .75 || envelope > 1) return result;
        var output = result.copy();
        SettingsNbt.write(output.getOrCreateTag(), SettingsNbt.inherit("pneumaticcraft:reinforced_pressure_tube",
            "pressure_envelope", settings));
        return output;
    }
}
