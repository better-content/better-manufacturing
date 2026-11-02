package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.createbulk.BulkNative;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adjust capacity before Create records/sends it; never changes boiler efficiency or RPM tiers. */
@Mixin(value = PoweredShaftBlockEntity.class, remap = false)
public abstract class CreatebulkSteamShaftMixin {
    @ModifyVariable(method = "calculateAddedStressCapacity", at = @At("STORE"), ordinal = 0)
    private float manufacturing$convertedCapacity(float nativeStress) {
        return BulkNative.engineCapacity((PoweredShaftBlockEntity) (Object) this, nativeStress);
    }
}
