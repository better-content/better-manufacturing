package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import com.bettercontent.manufacturing.integration.clockwork.ClockworkBladeRating;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scale the native controller's angular-speed input, without replacing its blade geometry or ship force. */
@Mixin(targets = "org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlockEntity", remap = false)
public abstract class ClockworkPropellerBearingMixin {
    @Shadow private List<BladeData> blades;
    @Redirect(method = "newUpdateData()Lorg/valkyrienskies/clockwork/content/contraptions/propeller/data/PropUpdateData;",
        at = @At(value = "FIELD", target = "Lorg/valkyrienskies/clockwork/content/contraptions/propeller/PropellerBearingBlockEntity;currentOmega:D"))
    private double manufacturing$rotorResponse(PropellerBearingBlockEntity bearing) {
        return bearing.getCurrentOmega() * ClockworkSettingsStore.rate(this, "actuator_response");
    }
    // Clockwork computes native stress from exact installed sail/blade geometry. Keep that multiplier
    // rather than bypassing it with an invented force/area constant.
    @Inject(method = "calculateStressApplied", at = @At("RETURN"), cancellable = true)
    private void manufacturing$rotorCost(CallbackInfoReturnable<Float> callback) {
        var kinetic = (KineticBlockEntity) (Object) this;
        if (kinetic.getLevel() == null || kinetic.getLevel().isClientSide()) return;
        var bearing = (PropellerBearingBlockEntity) (Object) this;
        double bladeRate = 1;
        if (bearing.getSailPositions().isEmpty() && blades != null && !blades.isEmpty()) {
            double weighted = 0, area = 0;
            for (var blade : blades) {
                double weight = Math.abs(blade.getLength() * Math.sin(Math.toRadians(blade.getAngle()))
                    * (blade.getWide() ? 1.5 : 1));
                Object data = blade;
                weighted += weight * (data instanceof ClockworkBladeRating rated ? rated.manufacturing$bladeResponse() : 1);
                area += weight;
            }
            if (area > 0) bladeRate = weighted / area;
        }
        float stress = (float) (callback.getReturnValueF() * ClockworkSettingsStore.rate(this, "actuator_response") * bladeRate);
        ((ClockworkStressAccess) this).manufacturing$setLastStressApplied(stress);
        callback.setReturnValue(stress);
    }
}
