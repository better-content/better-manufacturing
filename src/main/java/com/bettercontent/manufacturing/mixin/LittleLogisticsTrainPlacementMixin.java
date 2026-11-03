package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native track validation, orientation, owner and consume flow; attach settings before spawn. */
@Mixin(targets = "dev.murad.shipping.item.TrainCarItem", remap = false)
public abstract class LittleLogisticsTrainPlacementMixin {
    @Redirect(method = "useOn", remap = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true))
    private boolean manufacturing$place(Level level, Entity vehicle, UseOnContext context) {
        LittleLogisticsRuntime.apply(vehicle, context.getItemInHand());
        return level.addFreshEntity(vehicle);
    }
}
