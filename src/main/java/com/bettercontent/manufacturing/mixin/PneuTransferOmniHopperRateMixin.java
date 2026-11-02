package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import me.desht.pneumaticcraft.common.block.entity.OmnidirectionalHopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Omni is an ITEM hopper, not a fluid handler. Change its native transfer interval only. */
@Mixin(value = OmnidirectionalHopperBlockEntity.class, remap = false)
public abstract class PneuTransferOmniHopperRateMixin {
    @ModifyConstant(method = "getItemTransferInterval", constant = @Constant(intValue = 8))
    private int betterManufacturing$interval(int original) {
        return PneuTransferSettings.interval(original, this);
    }
}
