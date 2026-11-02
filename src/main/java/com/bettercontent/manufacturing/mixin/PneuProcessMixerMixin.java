package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.common.block.entity.FluidMixerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** PNCR progress and air draw scale together; output amounts, pressure and recipes stay native. */
@Mixin(value = FluidMixerBlockEntity.class, remap = false)
public abstract class PneuProcessMixerMixin {
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = 100.0f))
    private float betterManufacturing$mixingProgress(float nativeStep) {
        return (float) (nativeStep * PneuProcessNative.rate((BlockEntity) (Object) this, "process_rate"));
    }
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = 2.5f))
    private float betterManufacturing$mixingAir(float nativeDraw) {
        return (float) (nativeDraw * PneuProcessNative.rate((BlockEntity) (Object) this, "process_rate"));
    }
}
