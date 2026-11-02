package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.createbulk.BulkNative;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Changes only the controller's native processing progress, not drops or recipe selection. */
@Mixin(value = CrushingWheelControllerBlockEntity.class, remap = false)
public abstract class CreatebulkCrushingControllerMixin {
    // Second float local is native item-processing progress after Create's .25..20 cap.
    // The first is wheel speed, also used for entity motion: leave that untouched.
    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 1)
    private float manufacturing$pairProgress(float nativeProgress) {
        return BulkNative.pairWork((CrushingWheelControllerBlockEntity) (Object) this, nativeProgress);
    }
}
