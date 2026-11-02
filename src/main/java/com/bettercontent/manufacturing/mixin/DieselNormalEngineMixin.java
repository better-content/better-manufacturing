package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Capacity remains native fuel-specific and the original upgrade calculation stays intact. */
@Mixin(value = DieselEngineBlockEntity.class, remap = false)
public abstract class DieselNormalEngineMixin {
    @Inject(method = "calculateAddedStressCapacity", at = @At("RETURN"), cancellable = true)
    private void manufacturing$shaftLoad(CallbackInfoReturnable<Float> result) {
        result.setReturnValue((float) (result.getReturnValue() * DieselEngineData.capacity((DieselEngineBlockEntity) (Object) this)));
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/normal/DieselEngineBlockEntity;getFuelCapacity()F"))
    private float manufacturing$nativeCapacity(DieselEngineBlockEntity engine) {
        return (float) (engine.getFuelCapacity() * DieselEngineData.capacity(engine));
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/normal/DieselEngineBlockEntity;getFuelBurnRate()F"))
    private float manufacturing$nativeFuel(DieselEngineBlockEntity engine) {
        return (float) (engine.getFuelBurnRate() * DieselEngineData.burn(engine));
    }
}
