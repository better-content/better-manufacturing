package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The native runOneCycle derives progress, pressure consumption and heat consumption from this same step. */
@Mixin(value = ThermopneumaticProcessingPlantBlockEntity.class, remap = false)
public abstract class PneuProcessThermoPlantMixin {
    @ModifyConstant(method = "runOneCycle", constant = @Constant(doubleValue = 100.0d))
    private double betterManufacturing$cycleRate(double nativeStep) {
        return nativeStep * PneuProcessNative.rate((BlockEntity) (Object) this, "process_rate");
    }
}
