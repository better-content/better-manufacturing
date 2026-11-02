package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import me.desht.pneumaticcraft.common.block.entity.LiquidCompressorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native fluid fuel buffer and air both consume this same base production. */
@Mixin(value = LiquidCompressorBlockEntity.class, remap = false)
public abstract class PneupressureLiquidCompressorMixin {
    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lme/desht/pneumaticcraft/common/block/entity/LiquidCompressorBlockEntity;getBaseProduction()I"))
    private int pneupressure$productionAndFuel(LiquidCompressorBlockEntity compressor) {
        return Math.max(1, (int) Math.round(compressor.getBaseProduction() * PneupressureBuild.rate((BlockEntity) compressor, "compression_rate")));
    }
}
