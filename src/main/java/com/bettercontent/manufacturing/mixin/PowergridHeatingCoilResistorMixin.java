package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridHeaterCircuit;
import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The actual heater circuit draws proportionate native current; no free heat multiplier. */
@Mixin(targets = "org.patryk3211.powergrid.electricity.heater.HeaterBlockEntity", remap = false)
public abstract class PowergridHeatingCoilResistorMixin implements PowergridHeaterCircuit {
    @Shadow private ElectricWire wire;
    @Override public void manufacturing$setHeaterResistance(float ohms) {
        if (wire != null) wire.setResistance(ohms);
    }
    @ModifyArg(method = "buildCircuit", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/base/IElectricEntity$CircuitBuilder;connect(FLorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;Lorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;)Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;"), index = 0)
    private float manufacturing$coilResistance(float nativeResistance) {
        return (float) (nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "heater_coil_ratio"));
    }
}
