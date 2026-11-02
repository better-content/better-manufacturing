package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsAssemblyHolder;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.block.entity.AssemblyControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debit native controller air, keeping only sub-unit air prepaid by an earlier real debit. */
@Mixin(value = AssemblyControllerBlockEntity.class, remap = false)
public abstract class PneumatictoolsAssemblyAirMixin {
    @Unique private static final String BETTER_MANUFACTURING_PREPAID_AIR = "better_manufacturing:prepaid_drill_air";
    @Shadow private AssemblyControllerBlockEntity.AssemblySystem assemblySystem;
    @Unique private double betterManufacturing$prepaidAir;

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void betterManufacturing$saveAir(CompoundTag tag, CallbackInfo ci) {
        if (Double.isFinite(betterManufacturing$prepaidAir)
                && betterManufacturing$prepaidAir > 0 && betterManufacturing$prepaidAir < 1)
            tag.putDouble(BETTER_MANUFACTURING_PREPAID_AIR, betterManufacturing$prepaidAir);
        else tag.remove(BETTER_MANUFACTURING_PREPAID_AIR);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void betterManufacturing$loadAir(CompoundTag tag, CallbackInfo ci) {
        double stored = tag.contains(BETTER_MANUFACTURING_PREPAID_AIR, Tag.TAG_DOUBLE)
            ? tag.getDouble(BETTER_MANUFACTURING_PREPAID_AIR) : 0;
        betterManufacturing$prepaidAir = Double.isFinite(stored) && stored >= 0 && stored < 1 ? stored : 0;
    }

    // The pinned controller only invokes this after its native pressure/program gate.
    // Keep inactive, unconfigured and native zero-cost paths exactly native.
    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target =
        "Lme/desht/pneumaticcraft/common/block/entity/AssemblyControllerBlockEntity;addAir(I)V"), require = 1)
    private void betterManufacturing$drillAir(AssemblyControllerBlockEntity controller, int nativeAir) {
        if (nativeAir >= 0 || assemblySystem == null
                || !(assemblySystem.getDrill() instanceof PneumatictoolsAssemblyHolder holder)
                || !holder.betterManufacturing$isWorking() || holder.betterManufacturing$settings() == null) {
            controller.addAir(nativeAir);
            return;
        }
        // Credit is at most one integer air unit, and only represents earlier
        // overpayment. A zero-debit tick is possible only when already prepaid.
        double required = -((double) nativeAir) * holder.betterManufacturing$settings().rate();
        if (!Double.isFinite(required) || required <= 0 || required > Integer.MAX_VALUE) {
            betterManufacturing$prepaidAir = 0;
            controller.addAir(nativeAir);
            return;
        }
        double prepaid = Double.isFinite(betterManufacturing$prepaidAir)
            && betterManufacturing$prepaidAir >= 0 && betterManufacturing$prepaidAir < 1
                ? betterManufacturing$prepaidAir : 0;
        int debit = (int) Math.max(0, Math.ceil(required - prepaid));
        IAirHandlerMachine handler = controller.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY, null)
            .orElse(null);
        int before = handler == null ? -1 : handler.getAir();
        controller.addAir(-debit); // Actual native resource debit, never an item credit.
        int after = handler == null ? -1 : handler.getAir();
        // Do not mint a fractional credit from an unpowered, clamped or unavailable handler.
        betterManufacturing$prepaidAir = before >= debit && after >= 0 && before - after >= debit
            ? Math.max(0, Math.min(Math.nextDown(1.0), prepaid + debit - required)) : 0;
    }
}
