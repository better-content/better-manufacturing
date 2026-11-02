package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.Shadow;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import com.bettercontent.manufacturing.integration.powergrid.PowergridMotorCoil;

/** Power Grid computes motor speed / usable stress from actual circuit power, not a bonus multiplier. */
@Mixin(targets = {
    "org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity",
    "org.patryk3211.powergrid.kinetics.motor.ConstantSpeedMotorBlockEntity"
}, remap = false)
public abstract class PowergridMotorLeverageMixin implements PowergridMotorCoil {
    @Shadow private ElectricWire coil;
    @Override public void manufacturing$setMotorResistance(float resistance) {
        if (coil != null) coil.setResistance(resistance);
    }
    @ModifyArg(method = "buildCircuit", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/base/IElectricEntity$CircuitBuilder;connect(FLorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;Lorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;)Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;"), index = 0)
    private float manufacturing$electricalLoad(float nativeResistance) {
        return (float) (nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "drive_ratio"));
    }
}
