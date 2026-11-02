package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesPressurizerSettings;
import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesRates;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import de.ellpeck.prettypipes.pressurizer.PressurizerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One scaled cost for both energy simulation and actual extraction. */
@Mixin(value = PressurizerBlockEntity.class, remap = false)
public abstract class PrettypipesPressurizerMixin implements PrettypipesPressurizerSettings {
    @Unique private MachineSettings betterManufacturing$settings;

    @Override public MachineSettings betterManufacturing$getSettings() { return betterManufacturing$settings; }
    @Override public void betterManufacturing$setSettings(MachineSettings settings) { betterManufacturing$settings = settings; }

    @ModifyConstant(method = "pressurizeItem", constant = @Constant(intValue = 100))
    private int betterManufacturing$energyPerItem(int nativeCost) {
        return (int) Math.round(nativeCost * PrettypipesRates.pressurizerRate(betterManufacturing$settings));
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void betterManufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (betterManufacturing$settings != null) SettingsNbt.write(tag, betterManufacturing$settings);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void betterManufacturing$load(CompoundTag tag, CallbackInfo ci) {
        betterManufacturing$settings = SettingsNbt.read(tag, "prettypipes:pressurizer");
    }
}
