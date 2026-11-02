package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import me.desht.pneumaticcraft.common.block.entity.ElectrostaticCompressorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native lightning and grid sharing remain authoritative; only each recipient's charge is rated. */
@Mixin(value = ElectrostaticCompressorBlockEntity.class, remap = false)
public abstract class PneupressureElectrostaticMixin {
    // The pinned native caller invokes member.onStruckByLightning(member, grid.size()):
    // receiver and charge recipient are identical on that path. Only the first
    // addAir call is positive lightning charge; the second is native overpressure venting.
    @ModifyArg(method = "onStruckByLightning", at = @At(value = "INVOKE", target =
        "Lme/desht/pneumaticcraft/common/block/entity/ElectrostaticCompressorBlockEntity;addAir(I)V", ordinal = 0), index = 0, require = 1)
    private int pneupressure$lightningAir(int nativeAir) {
        return Math.max(0, (int) Math.round(nativeAir * Math.min(1,
            PneupressureBuild.rate((ElectrostaticCompressorBlockEntity) (Object) this, "compression_rate"))));
    }
}
