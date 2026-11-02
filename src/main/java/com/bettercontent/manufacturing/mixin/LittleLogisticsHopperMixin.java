package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import dev.murad.shipping.block.rapidhopper.RapidHopperTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native HopperBlockEntity.tryMoveItems still controls transfer, filtering, items and overflow. */
@Mixin(value = RapidHopperTileEntity.class, remap = false)
public abstract class LittleLogisticsHopperMixin {
    @Shadow private int rapidCooldown;

    @Inject(method = "pushItemsTick", at = @At("HEAD"), cancellable = true)
    private static void manufacturing$slowerNativeTransfers(Level level, BlockPos pos, BlockState state, RapidHopperTileEntity hopper, CallbackInfo ci) {
        double rate = LittleLogisticsRuntime.rate(hopper);
        // Preserve the native one-item transaction; skip a bounded fraction of ticks for slower builds.
        if (rate < 1 && Math.floorMod(level.getGameTime() * 7, 20) < Math.round((1 - rate) * 20)) {
            hopper.setCooldown(0);
            ci.cancel();
        }
    }

    @Inject(method = "pushItemsTick", at = @At("TAIL"))
    private static void manufacturing$extraNativeTransfer(Level level, BlockPos pos, BlockState state, RapidHopperTileEntity hopper, CallbackInfo ci) {
        double rate = LittleLogisticsRuntime.rate(hopper);
        if (rate <= 1 || ((LittleLogisticsHopperMixin) (Object) hopper).rapidCooldown != 1
                || Math.floorMod(level.getGameTime() * 7, 20) >= Math.round((rate - 1) * 20)) return;
        // Repeat the same vanilla transfer path, with its native inventory/overflow checks intact.
        hopper.setCooldown(0);
        LittleLogisticsHopperInvoker.manufacturing$tryMoveItems(level, pos, state, hopper, () -> HopperBlockEntity.suckInItems(level, hopper));
    }

}
