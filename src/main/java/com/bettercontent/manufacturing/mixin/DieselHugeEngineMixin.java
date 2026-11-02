package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Huge engines send rated capacity to their real shaft without changing placement geometry. */
@Mixin(value = HugeDieselEngineBlockEntity.class, remap = false)
public abstract class DieselHugeEngineMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/huge/HugeDieselEngineBlockEntity;getFuelCapacity()F"))
    private float manufacturing$nativeCapacity(HugeDieselEngineBlockEntity engine) {
        return (float) (engine.getFuelCapacity() * DieselEngineData.capacity(engine));
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/huge/HugeDieselEngineBlockEntity;getFuelBurnRate()F"))
    private float manufacturing$nativeFuel(HugeDieselEngineBlockEntity engine) {
        return (float) (engine.getFuelBurnRate() * DieselEngineData.burn(engine));
    }
}
