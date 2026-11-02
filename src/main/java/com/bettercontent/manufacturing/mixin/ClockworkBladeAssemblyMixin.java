package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/** Native variable-length blade assembly retains its shape and max-size check, but not stale source part stacks. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.CraftingTableBladeRecipe", remap = false)
public abstract class ClockworkBladeAssemblyMixin {
    @Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
    private void manufacturing$assembledBlade(CraftingContainer grid, RegistryAccess registries, CallbackInfoReturnable<ItemStack> callback) {
        var result = callback.getReturnValue();
        if (result == null || result.isEmpty()) return;
        var resultId = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (resultId == null || (!resultId.toString().equals("vs_clockwork:propeller_blade")
            && !resultId.toString().equals("vs_clockwork:wide_propeller_blade"))) return;
        double weighted = 0, length = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            var input = grid.getItem(i);
            var id = ForgeRegistries.ITEMS.getKey(input.getItem());
            if (id == null || (!id.toString().equals("vs_clockwork:propeller_blade")
                && !id.toString().equals("vs_clockwork:wide_propeller_blade"))) continue;
            double segment = input.getTag() == null ? 1 : input.getTag().getDouble("BladeLength");
            if (!Double.isFinite(segment) || segment <= 0) segment = 1;
            var baked = SettingsNbt.read(input.getTag(), id.toString());
            double rate = baked == null ? 1 : baked.properties().getOrDefault("actuator_response", 1d);
            weighted += segment * rate;
            length += segment;
        }
        if (length == 0) return;
        double response = Math.max(.75, Math.min(1.5, weighted / length));
        SettingsNbt.write(result.getOrCreateTag(), new MachineSettings(resultId.toString(),
            Map.of("actuator_response", response), Map.of(), null));
    }
}
