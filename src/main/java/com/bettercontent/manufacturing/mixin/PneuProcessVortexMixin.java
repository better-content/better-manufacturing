package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.common.block.entity.VortexTubeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Native pressure/temperature constraints stay in force; air consumption and heat moved scale together. */
@Mixin(value = VortexTubeBlockEntity.class, remap = false)
public abstract class PneuProcessVortexMixin {
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = 10.0f))
    private float betterManufacturing$heatThroughput(float nativeAirPerPressure) {
        return (float) (nativeAirPerPressure * PneuProcessNative.rate((BlockEntity) (Object) this, "thermal_transfer"));
    }
}
