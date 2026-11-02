package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import me.desht.pneumaticcraft.common.block.entity.AbstractFluidTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Stock capacity and fluid-compatibility checks stay intact. Only native transfer budgets change. */
@Mixin(value = AbstractFluidTankBlockEntity.class, remap = false)
public abstract class PneuTransferTankRateMixin {
    // Native vertical gravity flow: capacity / 32, retaining the native four size distinctions.
    @ModifyConstant(method = "tickServer", constant = @Constant(intValue = 32))
    private int betterManufacturing$gravityDivisor(int original) {
        return PneuTransferSettings.interval(original, this);
    }
    // Native auto-eject: 25 mB/t times installed speed upgrades; never change installed upgrades.
    @ModifyConstant(method = "lambda$tickServer$2", constant = @Constant(intValue = 25))
    private int betterManufacturing$ejectBudget(int original) {
        return PneuTransferSettings.scale(original, this);
    }
}
