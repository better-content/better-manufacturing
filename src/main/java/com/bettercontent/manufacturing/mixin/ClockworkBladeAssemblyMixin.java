package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
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
import java.util.LinkedHashMap;

/** Native variable-length blade assembly retains its shape and max-size check, but not stale source part stacks. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.CraftingTableBladeRecipe", remap = false)
public abstract class ClockworkBladeAssemblyMixin {
    // Clockwork keeps this typed overload named assemble; only its Container bridge is remapped.
    @Inject(method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true, remap = false)
    private void manufacturing$assembledBlade(CraftingContainer grid, RegistryAccess registries, CallbackInfoReturnable<ItemStack> callback) {
        var result = callback.getReturnValue();
        if (result == null || result.isEmpty()) return;
        var resultId = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (resultId == null || (!resultId.toString().equals("vs_clockwork:propeller_blade")
            && !resultId.toString().equals("vs_clockwork:wide_propeller_blade"))) return;
        double weighted = 0, length = 0;
        boolean configured = false, invalidLength = false;
        var segments = new LinkedHashMap<String, Map<String, Double>>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            var input = grid.getItem(i);
            var id = ForgeRegistries.ITEMS.getKey(input.getItem());
            if (id == null || (!id.toString().equals("vs_clockwork:propeller_blade")
                && !id.toString().equals("vs_clockwork:wide_propeller_blade"))) continue;
            var baked = SettingsNbt.read(input.getTag(), id.toString());
            if (input.hasTag() && input.getTag().contains(SettingsNbt.KEY)) {
                // A present but unreadable component is not an unconfigured stock blade.
                if (baked == null || !baked.properties().keySet().equals(java.util.Set.of("actuator_response"))) {
                    callback.setReturnValue(ItemStack.EMPTY);
                    return;
                }
                configured = true;
            }
            double segment = input.getTag() == null ? 0 : input.getTag().getDouble("BladeLength");
            if (!Double.isFinite(segment) || segment <= 0) invalidLength = true;
            double rate = baked == null ? 1 : baked.properties().get("actuator_response");
            weighted += segment * rate;
            length += segment;
            segments.put("blade_" + i, Map.of("actuator_response", rate, "blade_length", segment));
        }
        // Native stock assembly remains native and must not acquire a synthetic configured record.
        if (!configured) return;
        if (invalidLength || !Double.isFinite(length) || length <= 0) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        double response = Math.max(.75, Math.min(1.5, weighted / length));
        // Native blade length remains native data. Save only the consumed segments' numerical
        // responses and lengths, then replay the same weighted formula on later loads.
        try {
            var numeric = new NumericInputs("better_manufacturing:inherit/actuator_response", Map.of(), segments);
            var derived = NumericEvaluator.evaluate(resultId.toString(), numeric, null);
            if (Math.abs(derived.get("actuator_response") - response) > 1e-9)
                throw new IllegalArgumentException("Blade segment replay disagrees with native assembly");
            SettingsNbt.write(result.getOrCreateTag(), new MachineSettings(resultId.toString(),
                derived, Map.of(), null, numeric));
        } catch (IllegalArgumentException invalidNumericInput) {
            // Never launder a malformed configured segment into an unconfigured or baseline-rated craft.
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
