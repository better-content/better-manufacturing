package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.common.block.entity.VacuumPumpBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Preserve the native vacuum limit/pressure gate and pay at least the native whole-air cost per displaced unit. */
@Mixin(value = VacuumPumpBlockEntity.class, remap = false)
public abstract class PneuProcessVacuumPumpMixin {
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = -4.0f))
    private float betterManufacturing$vacuumTransfer(float nativeAir) {
        return (float) (nativeAir * PneuProcessNative.rate((BlockEntity) (Object) this, "transfer_rate"));
    }
    @ModifyConstant(method = "tickServer", constant = @Constant(floatValue = -10.0f))
    private float betterManufacturing$vacuumAirDraw(float nativeDraw) {
        // PNCR truncates this float to an integer before addAir(). Round negative cost
        // away from zero so a fractional rating never gets less air per whole vacuum
        // unit than the native -10 air / -4 vacuum transfer budget.
        return (float) Math.floor(nativeDraw * PneuProcessNative.rate((BlockEntity) (Object) this, "transfer_rate"));
    }
}
