package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native series/parallel winding counts and topology stay intact; only each actual coil's resistance changes. */
@Mixin(value = WindingBlockEntity.class, remap = false)
public abstract class PowergridWindingResistanceMixin {
    @Redirect(method = "calculateElectricalParameters", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/kinetics/generator/winding/WindingBlockEntity;resistance()F"))
    private float manufacturing$ratedWire(WindingBlockEntity segment) {
        return (float) (segment.resistance() / PowergridRatings.windingRatio(segment));
    }
}
