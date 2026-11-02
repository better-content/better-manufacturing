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
        // The dedicated Create bulk hook owns both native costs. Even a 1x setter here
        // cancels later RETURN injectors before they can charge launcher/wheel work.
        if ((Object) this instanceof com.simibubi.create.content.logistics.depot.EjectorBlockEntity
            || (Object) this instanceof com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity) return;
        if (this instanceof com.bettercontent.manufacturing.integration.sifter.ScreenAccess screen) {
            var state = screen.manufacturing$screenState();
            if (state.allowed()) {
                lastStressApplied = (float) (callback.getReturnValueF() * state.rate()); callback.setReturnValue(lastStressApplied);
            }
        }
        // ScreenAccess already combines frame and mesh once; multiplying the frame again
        // would double-charge stress without any corresponding native processing work.
        if (!(this instanceof com.bettercontent.manufacturing.integration.sifter.ScreenAccess)) {
            var variant = com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour.get(this);
            if (variant != null) {
                lastStressApplied = (float) (callback.getReturnValueF() * variant.rate()); callback.setReturnValue(lastStressApplied);
            }
        }
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null && behaviour.state().allowed()) {
            lastStressApplied = (float) (callback.getReturnValueF() * behaviour.state().rate());
            callback.setReturnValue(lastStressApplied);
        }
    }
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void manufacturing$explain(List<Component> lines, boolean sneaking, CallbackInfoReturnable<Boolean> callback) {
        if (CreateManufacturing.goggles((KineticBlockEntity) (Object) this, lines)) callback.setReturnValue(true);
        var variant = com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour.get(this);
        if (!(this instanceof com.bettercontent.manufacturing.integration.sifter.ScreenAccess) && variant != null && variant.settings() != null) {
            lines.add(Component.translatable(variant.settings().properties().containsKey("airflow") ? "better_manufacturing.variant.airflow" : variant.settings().properties().containsKey("pressure") ? "better_manufacturing.variant.pressure" : "better_manufacturing.variant.rate", String.format(java.util.Locale.ROOT, "%.2f", variant.rate())));
            callback.setReturnValue(true);
        }
        if (this instanceof com.bettercontent.manufacturing.integration.sifter.ScreenAccess screen) {
            var state = screen.manufacturing$screenState();
            lines.addAll(com.bettercontent.manufacturing.integration.sifter.MeshEngineering.describe(state));
            callback.setReturnValue(true);
        }
    }
}
