package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import dev.architectury.registry.fuel.FuelRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The native burner alone owns the fuel inventory, burn timer, gas heat capacity and Kelvin node.
 * A faster burner spends the same fuel in fewer ticks; a slower burner stretches its fuel.
 * Rounding burn duration down ensures the total added heat per item never exceeds native's
 * uncapped heat budget. The native 10 kJ/t limit and 850 K target are not raised.
 */
@Mixin(targets = "org.valkyrienskies.clockwork.content.logistics.gas.generation.coal_burner.CoalBurnerBlockEntity", remap = false)
public abstract class ClockworkCoalBurnerMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Ldev/architectury/registry/fuel/FuelRegistry;get(Lnet/minecraft/world/item/ItemStack;)I"))
    private int manufacturing$paidBurnTime(ItemStack fuel) {
        int nativeTicks = FuelRegistry.get(fuel);
        if (nativeTicks <= 0) return nativeTicks;
        double rate = ClockworkSettingsStore.rate(this, "gas_transfer");
        // One-tick fuels cannot pay for an increased rate; the heat hook caps those separately.
        return Math.max(1, (int) Math.floor(nativeTicks / rate));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"))
    private double manufacturing$paidHeat(double requested, double nativeCap) {
        double rate = ClockworkSettingsStore.rate(this, "gas_transfer");
        // Clockwork stores the paid burn duration in maxBurnTime when taking a fuel item.
        // For a single-tick item never increase output over native, even after rounding.
        var burner = (org.valkyrienskies.clockwork.content.logistics.gas.generation.coal_burner.CoalBurnerBlockEntity) (Object) this;
        rate = Math.min(rate, Math.max(1, burner.getMaxBurnTime()));
        return Math.min(requested * rate, nativeCap);
    }
}
