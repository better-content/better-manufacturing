package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.*;
import com.simibubi.create.content.kinetics.fan.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = EncasedFanBlockEntity.class, remap = false)
public abstract class FanSourceMixin implements IAirCurrentSource {
    @Override public float getMaxDistance() {
        var settings = KineticVariantBehaviour.get(this);
        return new RatedAirSource(this, settings == null ? 1 : settings.rate()).getMaxDistance();
    }
}
