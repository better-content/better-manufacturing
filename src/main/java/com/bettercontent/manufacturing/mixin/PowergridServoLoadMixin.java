package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridMotorCoil;
import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Servo retains both idle and active states; changed speed draws proportionate native circuit power. */
@Mixin(targets = "org.patryk3211.powergrid.kinetics.servo.ServoBlockEntity", remap = false)
public abstract class PowergridServoLoadMixin implements PowergridMotorCoil {
    @Shadow private ElectricWire coil;
    @Override public void manufacturing$setMotorResistance(float resistance) {
        if (coil != null) coil.setResistance(resistance);
    }
    @ModifyArg(method = "buildCircuit", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/base/IElectricEntity$CircuitBuilder;connect(FLorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;Lorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;)Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;", ordinal = 0), index = 0)
    private float manufacturing$initialLoad(float nativeResistance) {
        return (float) (nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "drive_ratio"));
    }
    @ModifyArg(method = {"tick", "read"}, at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;setResistance(D)V"), index = 0)
    private double manufacturing$stateLoad(double nativeResistance) {
        return nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "drive_ratio");
    }
}
