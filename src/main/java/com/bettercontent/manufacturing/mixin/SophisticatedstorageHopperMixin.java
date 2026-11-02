package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.sophisticatedstorage.HopperEngineering;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedstorage.upgrades.hopper.HopperUpgradeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the native cooldown interval changes; keep the native push/pull, filters, sides, stack limits and tiers. */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.upgrades.hopper.HopperUpgradeWrapper", remap = false)
public abstract class SophisticatedstorageHopperMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/p3pp3rf1y/sophisticatedstorage/upgrades/hopper/HopperUpgradeItem;getTransferSpeedTicks()J", remap = false), remap = false)
    private long manufacturing$installedHopperInterval(HopperUpgradeItem item) {
        return HopperEngineering.interval(((IUpgradeWrapper) this).getUpgradeStack(), item.getTransferSpeedTicks());
    }
}
