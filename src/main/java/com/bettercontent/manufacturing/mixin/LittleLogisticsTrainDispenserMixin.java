package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.core.BlockSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Dispenser-spawned rail vehicles retain their construction, not the original parts. */
@Mixin(targets = "dev.murad.shipping.item.TrainCarItem$1", remap = false)
public abstract class LittleLogisticsTrainDispenserMixin {
    @Redirect(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true))
    private boolean manufacturing$place(Level level, Entity vehicle, BlockSource source, ItemStack stack) {
        LittleLogisticsRuntime.apply(vehicle, stack);
        return level.addFreshEntity(vehicle);
    }
}
