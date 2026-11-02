package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.createbulk.BulkNative;
import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Independently charges the ejector's windup and each crushing wheel's baked native load. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class CreatebulkEjectorStressMixin {
    @Shadow protected float lastStressApplied;
    @Inject(method = "calculateStressApplied", at = @At("RETURN"), cancellable = true)
    private void manufacturing$launcherStress(CallbackInfoReturnable<Float> callback) {
        if ((Object) this instanceof EjectorBlockEntity ejector) {
            lastStressApplied = BulkNative.launcherStress(ejector, callback.getReturnValueF());
            callback.setReturnValue(lastStressApplied);
        } else if ((Object) this instanceof CrushingWheelBlockEntity wheel) {
            var variant = KineticVariantBehaviour.get(wheel);
            var settings = variant == null ? null : variant.settings();
            if (settings == null) return;
            double load = settings.properties().getOrDefault("load", Double.NaN);
            if (!Double.isFinite(load) || load < .75 || load > 1.5) return;
            // The shared generic kinetic hook excludes wheels; only the separately
            // baked load multiplies Create's native stress, exactly once.
            lastStressApplied = (float) (callback.getReturnValueF() * load);
            callback.setReturnValue(lastStressApplied);
        }
    }
}
