package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureFuelCost;
import me.desht.pneumaticcraft.common.block.entity.AirCompressorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PNCR charges fuel in integer burn ticks: never allow rounded-down positive air to become free. */
@Mixin(value = AirCompressorBlockEntity.class, remap = false)
public abstract class PneupressureFuelCompressorMixin implements PneupressureFuelCost {
    @Shadow public int curFuelUsage;
    @Shadow public int burnTime;
    @Unique private static final String PNEUPRESSURE_FUEL_REMAINDER = "better_manufacturing:fuel_remainder";
    @Unique private double pneupressure$fuelRemainder;
    @Override public double pneupressure$fuelRemainder() { return pneupressure$fuelRemainder; }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void pneupressure$saveFuelRemainder(CompoundTag tag, CallbackInfo ci) {
        if (pneupressure$fuelRemainder > 0 && pneupressure$fuelRemainder < 1)
            tag.putDouble(PNEUPRESSURE_FUEL_REMAINDER, pneupressure$fuelRemainder);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void pneupressure$loadFuelRemainder(CompoundTag tag, CallbackInfo ci) {
        double stored = tag.contains(PNEUPRESSURE_FUEL_REMAINDER, Tag.TAG_DOUBLE)
            ? tag.getDouble(PNEUPRESSURE_FUEL_REMAINDER) : 0;
        pneupressure$fuelRemainder = Double.isFinite(stored) && stored >= 0 && stored < 1 ? stored : 0;
    }

    // Both native calculations use the same scaled base production; speed upgrades remain native.
    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lme/desht/pneumaticcraft/common/block/entity/AirCompressorBlockEntity;getBaseProduction()I"))
    private int pneupressure$productionAndFuel(AirCompressorBlockEntity compressor) {
        return Math.max(1, (int) Math.round(compressor.getBaseProduction() * PneupressureBuild.rate((BlockEntity) compressor, "compression_rate")));
    }

    // Native truncates (scaledProduction * nativeUsageMultiplier / 10) to an int. For
    // wood this is zero despite 8 air/t; for a fast material it is one despite 15 air/t.
    // Round the paid integer cost over successive work ticks, with at least one unit due
    // on every productive tick. Never change PNCR's burn/refill, heat or air code path.
    @Inject(method = "tickServer", at = @At(value = "FIELD", target =
        "Lme/desht/pneumaticcraft/common/block/entity/AirCompressorBlockEntity;curFuelUsage:I",
        opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void pneupressure$chargeRoundedFuel(CallbackInfo ci) {
        var compressor = (AirCompressorBlockEntity) (Object) this;
        double rating = PneupressureBuild.rate((BlockEntity) compressor, "compression_rate");
        if (rating == 1) { pneupressure$fuelRemainder = 0; return; }
        double expected = Math.max(1, Math.round(compressor.getBaseProduction() * rating))
            * (double) compressor.getSpeedUsageMultiplierFromUpgrades() / 10;
        if (!Double.isFinite(expected) || expected <= 0) return;
        double accumulated = expected + pneupressure$fuelRemainder;
        int charged = Math.max(1, (int) Math.floor(accumulated));
        // Only a tick with enough native burn time to perform work earns a carried
        // fractional debit; idle/unpowered ticks cannot pre-load or reset the credit.
        // For sub-unit costs the one-unit floor deliberately costs extra rather than
        // leaving an unpowered compressor capable of generating air for free.
        if (burnTime >= charged) pneupressure$fuelRemainder = Math.max(0, accumulated - charged);
        curFuelUsage = charged;
    }
}
