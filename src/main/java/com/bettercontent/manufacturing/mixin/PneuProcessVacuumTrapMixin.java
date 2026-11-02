package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import me.desht.pneumaticcraft.common.block.entity.VacuumTrapBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the native target-list rescan cadence is rated. Capture, mob motion, XP and core genetics remain untouched. */
@Mixin(value = VacuumTrapBlockEntity.class, remap = false)
public abstract class PneuProcessVacuumTrapMixin {
    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J", remap = true))
    private long betterManufacturing$rescanClock(Level level) {
        long nativeTime = level.getGameTime();
        double rate = PneuProcessNative.rate((BlockEntity) (Object) this, "transfer_rate");
        if (rate == 1 || nativeTime > Long.MAX_VALUE / 2) return nativeTime;
        return (long) Math.floor(nativeTime * rate);
    }
}
