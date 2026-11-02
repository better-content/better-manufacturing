package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridHeaterCircuit;
import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native basin current and thermal wire power both change; Create heat states still use native temperature. */
@Mixin(targets = "org.patryk3211.powergrid.electricity.basinheater.BasinHeaterBlockEntity", remap = false)
public abstract class PowergridBasinResistorMixin implements PowergridHeaterCircuit {
    @Shadow private ElectricWire coil;
    @Override public void manufacturing$setHeaterResistance(float ohms) {
        if (coil != null) coil.setResistance(ohms);
    }
    @ModifyArg(method = "buildCircuit", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/base/IElectricEntity$CircuitBuilder;connect(FLorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;Lorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;)Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;"), index = 0)
    private float manufacturing$coilResistance(float nativeResistance) {
        return (float) (nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "heater_coil_ratio"));
    }
}
