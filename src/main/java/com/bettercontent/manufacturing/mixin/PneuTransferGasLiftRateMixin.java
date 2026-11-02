package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import me.desht.pneumaticcraft.common.block.entity.GasLiftBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Change the native work interval, not depth, pressure gate, fluid amount or air used per cycle. */
@Mixin(value = GasLiftBlockEntity.class, remap = false)
public abstract class PneuTransferGasLiftRateMixin {
    // Both comparison and subtraction must use the same interval (native 20 ticks of work).
    @ModifyConstant(method = "tickServer", constant = @Constant(intValue = 20), require = 2)
    private int betterManufacturing$workInterval(int original) {
        return PneuTransferSettings.interval(original, this);
    }
}
