package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Solar output remains conditional on native sunlight, temperature and heat costs. */
@Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.SolarCompressorBlockEntity", remap = false)
public abstract class PneupressureSolarMixin {
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = 2.0F), require = 1)
    private float pneupressure$solarFactor(float nativeFactor) {
        return nativeFactor * (float) PneupressureBuild.rate((BlockEntity) (Object) this, "compression_rate");
    }
}
