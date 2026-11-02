package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesPressurizerSettings;
import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import de.ellpeck.prettypipes.pipe.PipeBlockEntity;
import de.ellpeck.prettypipes.pressurizer.PressurizerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Native energy availability check remains the gate for the pressure speed bonus. */
@Mixin(value = PipeBlockEntity.class, remap = false)
public abstract class PrettypipesPressureSpeedMixin {
    @Shadow public PressurizerBlockEntity pressurizer;

    @ModifyConstant(method = "getItemSpeed", constant = @Constant(floatValue = .45f))
    private float betterManufacturing$pressureSpeed(float nativeBonus) {
        if (!(pressurizer instanceof PrettypipesPressurizerSettings settings)) return nativeBonus;
        return (float) (nativeBonus * PrettypipesRates.pressurizerRate(settings.betterManufacturing$getSettings()));
    }
}
