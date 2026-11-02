package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.bettercontent.manufacturing.integration.powergrid.PowergridFanMotor;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native current determines airflow. Lower motor resistance raises both airflow AND electrical draw/heat. */
@Mixin(targets = "org.patryk3211.powergrid.electricity.fan.ElectricFanBlockEntity", remap = false)
public abstract class PowergridFanLoadMixin implements PowergridFanMotor {
    @Shadow private ElectricWire motor;
    @Override public void manufacturing$setMotorResistance(float resistance) {
        if (motor != null) motor.setResistance(resistance);
    }
    @ModifyArg(method = "buildCircuit", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/base/IElectricEntity$CircuitBuilder;connect(FLorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;Lorg/patryk3211/powergrid/electricity/sim/node/IElectricNode;)Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;"), index = 0)
    private float manufacturing$load(float nativeResistance) {
        return (float) (nativeResistance / PowergridRatings.ratio((BlockEntity) (Object) this, "drive_ratio"));
    }
}
