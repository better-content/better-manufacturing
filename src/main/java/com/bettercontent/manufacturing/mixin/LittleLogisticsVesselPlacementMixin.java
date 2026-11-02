package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native vessel placement still owns spawn and consumption; copy only the baked result. */
@Mixin(targets = "dev.murad.shipping.item.VesselItem", remap = false)
public abstract class LittleLogisticsVesselPlacementMixin {
    @Inject(method = "getEntity", at = @At("RETURN"))
    private void manufacturing$copySettings(Level level, ItemStack stack, BlockHitResult hit, CallbackInfoReturnable<Entity> cir) {
        LittleLogisticsRuntime.apply(cir.getReturnValue(), stack);
    }
}
