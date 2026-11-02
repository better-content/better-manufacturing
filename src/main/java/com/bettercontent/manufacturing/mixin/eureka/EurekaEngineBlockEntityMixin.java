package com.bettercontent.manufacturing.mixin.eureka;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.eureka.EurekaEngineData;
import com.bettercontent.manufacturing.integration.eureka.EurekaEngineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.blockentity.EngineBlockEntity;
import org.valkyrienskies.eureka.ship.EurekaShipControl;

/** Scale Eureka's own two ship force contributions and the matching heat/work charge, never fuel items or ship physics. */
@Mixin(value = EngineBlockEntity.class, remap = false)
public abstract class EurekaEngineBlockEntityMixin implements EurekaEngineData {
    @Unique private MachineSettings manufacturing$engineSettings;

    @Override public MachineSettings manufacturing$getEngineSettings() { return manufacturing$engineSettings; }
    @Override public void manufacturing$setEngineSettings(MachineSettings settings) { manufacturing$engineSettings = settings; }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void manufacturing$saveEngineSettings(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$engineSettings != null) SettingsNbt.write(tag, manufacturing$engineSettings);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void manufacturing$readEngineSettings(CompoundTag tag, CallbackInfo ci) {
        MachineSettings settings = SettingsNbt.read(tag, EurekaEngineSettings.OUTPUT);
        manufacturing$engineSettings = EurekaEngineSettings.valid(settings) ? settings : null;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lorg/joml/Math;lerp(FFF)F", remap = false), remap = false)
    private float manufacturing$driveAtNativeHeat(float min, float max, float heatFraction) {
        return org.joml.Math.lerp(min, max, heatFraction)
            * (float) EurekaEngineSettings.rate(manufacturing$engineSettings);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lorg/valkyrienskies/eureka/ship/EurekaShipControl;getConsumed()F", remap = false), remap = false)
    private float manufacturing$chargeNativeWork(EurekaShipControl control) {
        return control.getConsumed() * (float) EurekaEngineSettings.rate(manufacturing$engineSettings);
    }
}
