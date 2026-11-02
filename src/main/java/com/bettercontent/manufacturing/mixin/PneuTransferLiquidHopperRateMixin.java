package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import me.desht.pneumaticcraft.common.block.entity.LiquidHopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** One native mB budget per import/export route: keep entity upgrades, leave-material and direction checks. */
@Mixin(value = LiquidHopperBlockEntity.class, remap = false)
public abstract class PneuTransferLiquidHopperRateMixin {
    @ModifyConstant(method = {"doImport", "doExport", "lambda$doImport$5", "lambda$doImport$6",
            "lambda$doExport$3", "lambda$doExport$4"}, constant = @Constant(intValue = 100), require = 6)
    private int betterManufacturing$fluidBudget(int original) {
        return PneuTransferSettings.scale(original, this);
    }
}
