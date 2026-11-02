package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import me.desht.pneumaticcraft.common.block.entity.RefineryControllerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Work threshold and heat draw change together; native fluid volumes, recipes and output distribution are unchanged. */
@Mixin(value = RefineryControllerBlockEntity.class, remap = false)
public abstract class PneuProcessRefineryMixin {
    @ModifyConstant(method = "tickServer", constant = @Constant(intValue = 20))
    private int betterManufacturing$refiningThreshold(int nativeThreshold) {
        // PNCR spends heat per native work tick. Round the shared threshold up so
        // faster fractional rates cannot finish a batch on less than 20 ticks'
        // equivalent heat; the same constant also subtracts completed work.
        return Math.max(1, (int) Math.ceil(nativeThreshold / PneuProcessNative.rate((BlockEntity) (Object) this, "process_rate")));
    }
    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lme/desht/pneumaticcraft/api/heat/IHeatExchangerLogic;addHeat(D)V", remap = false))
    private void betterManufacturing$refiningHeat(IHeatExchangerLogic exchanger, double heat) {
        exchanger.addHeat(heat * PneuProcessNative.rate((BlockEntity) (Object) this, "process_rate"));
    }
}
