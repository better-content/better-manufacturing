package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The two native 40F factors are air production and the matching FE/t draw. */
@Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.FluxCompressorBlockEntity", remap = false)
public abstract class PneupressureFluxCompressorMixin {
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = 40.0F), require = 2)
    private float pneupressure$airAndEnergy(float nativeFactor) {
        return nativeFactor * (float) PneupressureBuild.rate((BlockEntity) (Object) this, "compression_rate");
    }
}
