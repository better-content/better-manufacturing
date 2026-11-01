package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class DrillKineticsMixin {
    @Shadow protected float lastStressApplied;
    @Inject(method = "calculateStressApplied", at = @At("RETURN"), cancellable = true)
    private void manufacturing$load(CallbackInfoReturnable<Float> callback) {
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null && behaviour.state().allowed()) {
            lastStressApplied = (float) (callback.getReturnValueF() * behaviour.state().rate());
            callback.setReturnValue(lastStressApplied);
        }
    }
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void manufacturing$explain(List<Component> lines, boolean sneaking, CallbackInfoReturnable<Boolean> callback) {
        if (CreateManufacturing.goggles((KineticBlockEntity) (Object) this, lines)) callback.setReturnValue(true);
    }
}
