package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkBladeRating;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData;

/** Native lift calculation owns pressure, density, blade angle/width, ship force and actual geometry. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.forces.PropellerController", remap = false)
public abstract class ClockworkBladeForceMixin {
    @Redirect(method = "computeBladeForce", at = @At(value = "INVOKE",
        target = "Lorg/valkyrienskies/clockwork/content/contraptions/propeller/blades/BladeData;getLength()D"))
    private double manufacturing$bladeLiftArea(BladeData blade) {
        // Modify only effective lifting response in the native force calculation. Geometry and
        // blade stress continue to use unmodified native length; matching stress scales separately.
        Object data = blade;
        return blade.getLength() * (data instanceof ClockworkBladeRating rated ? rated.manufacturing$bladeResponse() : 1);
    }
}
