package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsRuntime;
import me.desht.pneumaticcraft.common.item.DrillBitItem;
import me.desht.pneumaticcraft.common.item.JackHammerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native dig modes, upgrades, air reservation and physical-head wear remain PNCR/BCF-owned. */
@Mixin(value = JackHammerItem.class, remap = false)
public abstract class PneumatictoolsJackhammerMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true, remap = true)
    private void betterManufacturing$cuttingRate(ItemStack jackhammer, BlockState state, CallbackInfoReturnable<Float> cir) {
        if (PneumatictoolsRuntime.hasPhysicalHead(jackhammer)) return;
        var bit = PneumatictoolsRuntime.installedBit(jackhammer);
        if (!(bit.getItem() instanceof DrillBitItem drillBit)) return;
        if (PneumatictoolsRuntime.settings(jackhammer) == null && !PneumatictoolsRuntime.isConfiguredBit(bit)) return;
        if (((JackHammerItem)(Object)this).getAir(jackhammer) <= 0) return;
        float original = cir.getReturnValue();
        if (!Float.isFinite(original) || original <= 0) return;
        // Eliminate redundant 6/7/8/9 grade efficiency, not the grade's real dig modes or tier.
        cir.setReturnValue((float)(original * (6.0 / drillBit.getType().getBaseEfficiency()) * PneumatictoolsRuntime.rate(jackhammer)));
    }

    @Inject(method = "isCorrectToolForDrops", at = @At("RETURN"), cancellable = true, remap = false)
    private void betterManufacturing$headTier(ItemStack jackhammer, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !PneumatictoolsRuntime.canHarvest(jackhammer, state)) cir.setReturnValue(false);
    }

    @ModifyConstant(method = "lambda$onBlockStartBreak$1", constant = @Constant(floatValue = 50.0f), remap = false)
    private float betterManufacturing$matchedAir(float baseCost, ItemStack jackhammer) {
        if (PneumatictoolsRuntime.hasPhysicalHead(jackhammer)) return baseCost;
        var bit = PneumatictoolsRuntime.installedBit(jackhammer);
        if (PneumatictoolsRuntime.settings(jackhammer) == null && !PneumatictoolsRuntime.isConfiguredBit(bit)) return baseCost;
        return (float)(baseCost * PneumatictoolsRuntime.rate(jackhammer));
    }
}
