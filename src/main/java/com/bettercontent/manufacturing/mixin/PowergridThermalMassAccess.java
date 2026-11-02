package com.bettercontent.manufacturing.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Existing native thermal mass, read once when a crafted plate is installed. */
@Mixin(targets = "org.patryk3211.powergrid.electricity.base.ThermalBehaviour", remap = false)
public interface PowergridThermalMassAccess {
    @Accessor("thermalMass") float manufacturing$getThermalMass();
}
