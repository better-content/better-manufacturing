package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve modular height, controller identity and native fuel-type/upgrade behavior. */
@Mixin(value = ModularDieselEngineBlockEntity.class, remap = false)
public abstract class DieselModularEngineMixin {
    @Inject(method = "calculateAddedStressCapacity", at = @At("RETURN"), cancellable = true)
    private void manufacturing$shaftLoad(CallbackInfoReturnable<Float> result) {
        result.setReturnValue((float) (result.getReturnValue() * DieselEngineData.capacity((ModularDieselEngineBlockEntity) (Object) this)));
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/modular/ModularDieselEngineBlockEntity;getFuelCapacity()F"))
    private float manufacturing$nativeCapacity(ModularDieselEngineBlockEntity engine) {
        return (float) (engine.getFuelCapacity() * DieselEngineData.capacity(engine));
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/modular/ModularDieselEngineBlockEntity;getFuelBurnRate()F"))
    private float manufacturing$nativeFuel(ModularDieselEngineBlockEntity engine) {
        return (float) (engine.getFuelBurnRate() * DieselEngineData.burn(engine));
    }
}
