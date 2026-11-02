package com.bettercontent.manufacturing.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = KineticBlockEntity.class, remap = false)
public interface ClockworkStressAccess {
    @Accessor("lastStressApplied") void manufacturing$setLastStressApplied(float stress);
}
