package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import dev.murad.shipping.capability.ReadWriteEnergyStorage;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Faster FE drives pay proportionally more FE per operating tick; no free motion or new battery. */
@Mixin(targets = {
    "dev.murad.shipping.entity.custom.vessel.tug.EnergyTugEntity",
    "dev.murad.shipping.entity.custom.train.locomotive.EnergyLocomotiveEntity"
}, remap = false)
public abstract class LittleLogisticsElectricDriveMixin {
    @Redirect(method = "tickFuel", at = @At(value = "INVOKE", target = "Ldev/murad/shipping/capability/ReadWriteEnergyStorage;extractEnergy(IZ)I"))
    private int manufacturing$paidEnergy(ReadWriteEnergyStorage battery, int nativeUsage, boolean simulate) {
        double rate = LittleLogisticsRuntime.rate((Entity) (Object) this);
        if (nativeUsage <= 0 || simulate || rate == 1) return battery.extractEnergy(nativeUsage, simulate);
        int cost = Math.max(1, (int) Math.ceil(nativeUsage * rate));
        // Native tickFuel considers *any* positive extraction sufficient to move. Require the
        // complete rated cost before committing so a nearly empty battery cannot power a tick.
        if (battery.extractEnergy(cost, true) < cost) return 0;
        return battery.extractEnergy(cost, false) == cost ? cost : 0;
    }
}
