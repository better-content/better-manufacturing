package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsAssemblyHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AssemblyDrillBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AssemblyDrillBlockEntity.class, remap = false)
public abstract class PneumatictoolsAssemblyDrillMixin implements PneumatictoolsAssemblyHolder {
    @Shadow private int drillStep;
    @Unique private MachineSettings betterManufacturing$drillingSettings;

    @Override public MachineSettings betterManufacturing$settings() { return betterManufacturing$drillingSettings; }
    @Override public void betterManufacturing$setSettings(MachineSettings settings) {
        betterManufacturing$drillingSettings = settings != null && settings.output().equals("pneumaticcraft:assembly_drill") ? settings : null;
        ((AssemblyDrillBlockEntity)(Object)this).setChanged();
    }
    @Override public boolean betterManufacturing$isWorking() { return drillStep > 0 && drillStep <= 5; }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void betterManufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (betterManufacturing$drillingSettings != null) SettingsNbt.write(tag, betterManufacturing$drillingSettings);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void betterManufacturing$load(CompoundTag tag, CallbackInfo ci) {
        betterManufacturing$drillingSettings = SettingsNbt.read(tag, "pneumaticcraft:assembly_drill");
    }

}
