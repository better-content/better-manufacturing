package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.simibubi.create.content.kinetics.fan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = AirCurrent.class, remap = false)
public abstract class FanCurrentMixin {
    @Redirect(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/IAirCurrentSource;getSpeed()F"))
    private float manufacturing$airSpeed(IAirCurrentSource source) {
        var settings = KineticVariantBehaviour.get(source);
        return (float) (source.getSpeed() * (settings == null ? 1 : settings.rate()));
    }
}
