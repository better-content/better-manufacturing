package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsAssemblyHolder;
import me.desht.pneumaticcraft.common.block.entity.AbstractAssemblyRobotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Only the drill's robot receives the baked rate; controller upgrades still set the base speed. */
@Mixin(value = AbstractAssemblyRobotBlockEntity.class, remap = false)
public abstract class PneumatictoolsAssemblyMotionMixin {
    @ModifyVariable(method = "setSpeed", at = @At("HEAD"), argsOnly = true, remap = false)
    private float betterManufacturing$drillSpeed(float nativeSpeed) {
        if ((Object)this instanceof PneumatictoolsAssemblyHolder holder && holder.betterManufacturing$settings() != null)
            return (float)(nativeSpeed * holder.betterManufacturing$settings().rate());
        return nativeSpeed;
    }
}
