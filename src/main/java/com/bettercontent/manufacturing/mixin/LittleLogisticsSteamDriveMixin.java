package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adjust only native running burn-time debit; native item fuel values and fuel inventory stay authoritative. */
@Mixin(targets = {
    "dev.murad.shipping.entity.custom.vessel.tug.SteamTugEntity",
    "dev.murad.shipping.entity.custom.train.locomotive.SteamLocomotiveEntity"
}, remap = false)
public abstract class LittleLogisticsSteamDriveMixin {
    @Shadow protected int burnTime;
    @Unique private static final String MANUFACTURING_BURN_ADJUSTMENT = "better_manufacturing:burn_adjustment";
    @Unique private double manufacturing$burnAdjustment;
    @Unique private boolean manufacturing$wasBurning;

    @Inject(method = "tickFuel", at = @At("HEAD"))
    private void manufacturing$beforeFuel(CallbackInfoReturnable<Boolean> cir) {
        manufacturing$wasBurning = burnTime > 0;
    }

    @Inject(method = "tickFuel", at = @At("RETURN"), cancellable = true)
    private void manufacturing$chargedBurn(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        double rate = LittleLogisticsRuntime.rate((Entity) (Object) this);
        if (rate == 1) return; // Keep native unconfigured fuel and first-refill semantics.
        // Native refilling returns success without debiting a tick; account for that
        // first successful movement as well as every subsequently charged tick.
        double owed = manufacturing$burnAdjustment + rate - (manufacturing$wasBurning ? 1 : 0);
        if (owed >= 1) {
            int extra = (int) Math.floor(owed);
            if (burnTime < extra) {
                // Do not allow rated movement when the extra paid burn is unavailable.
                cir.setReturnValue(false);
                return;
            }
            burnTime -= extra;
            owed -= extra;
        } else if (owed <= -1) {
            burnTime++;
            owed += 1;
        }
        manufacturing$burnAdjustment = owed;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), remap = true)
    private void manufacturing$saveAdjustment(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$burnAdjustment != 0) tag.putDouble(MANUFACTURING_BURN_ADJUSTMENT, manufacturing$burnAdjustment);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), remap = true)
    private void manufacturing$loadAdjustment(CompoundTag tag, CallbackInfo ci) {
        double saved = tag.contains(MANUFACTURING_BURN_ADJUSTMENT, Tag.TAG_DOUBLE)
                ? tag.getDouble(MANUFACTURING_BURN_ADJUSTMENT) : 0;
        manufacturing$burnAdjustment = Double.isFinite(saved) && saved > -1 && saved < 1 ? saved : 0;
    }
}
