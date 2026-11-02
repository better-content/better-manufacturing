package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Quality changes the cooldown between hand-pump steps, not air created per stroke or native volume limits. */
@Mixin(targets = "me.desht.pneumaticcraft.common.block.entity.ManualCompressorBlockEntity", remap = false)
public abstract class PneupressureManualMixin {
    @ModifyConstant(method = "onPumpCycleStep", constant = @Constant(intValue = 4), require = 1)
    private int pneupressure$pumpStepInterval(int nativeTicks) {
        return Math.max(3, Math.min(5, (int) Math.round(nativeTicks /
            PneupressureBuild.rate((BlockEntity) (Object) this, "compression_rate"))));
    }
}
