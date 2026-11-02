package com.bettercontent.manufacturing.integration.createbulk;

import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Baked values only; native recipes, destinations, controllers and boiler supply are not replaced. */
public final class BulkNative {
    private BulkNative() {}
    public static float pairWork(CrushingWheelControllerBlockEntity controller, float nativeProgress) {
        if (controller.getLevel() == null || !controller.getBlockState().hasProperty(CrushingWheelControllerBlock.VALID)
                || !controller.getBlockState().getValue(CrushingWheelControllerBlock.VALID)) return nativeProgress;
        var level = controller.getLevel();
        double rate = 1.5; int seen = 0;
        for (var direction : Direction.values()) {
            var pos = controller.getBlockPos().relative(direction);
            var state = level.getBlockState(pos);
            if (!(level.getBlockEntity(pos) instanceof CrushingWheelBlockEntity wheel)
                    || !state.hasProperty(BlockStateProperties.AXIS)
                    || state.getValue(BlockStateProperties.AXIS) == direction.getAxis()) continue;
            var variant = KineticVariantBehaviour.get(wheel);
            var settings = variant == null ? null : variant.settings();
            double each = settings == null ? 1 : settings.properties().getOrDefault("work_rate", 1.0);
            rate = Math.min(rate, bounded(each)); seen++;
        }
        // Never confer a one-wheel bonus or duplicate two-wheel bonuses on a controller.
        // Create has already clamped native progress to [.25, 20]. Reapplying its
        // minimum AFTER a paid sub-1x rating would erase that wheel's slower work
        // exactly at the native floor; only retain the native maximum per tick.
        return seen == 2 ? (float) Math.min(20, nativeProgress * rate) : nativeProgress;
    }
    public static float launcherCharge(EjectorBlockEntity ejector, float nativeCharge) {
        var variant = KineticVariantBehaviour.get(ejector);
        var settings = variant == null ? null : variant.settings();
        if (settings == null || !"create:weighted_ejector".equals(settings.output())) return nativeCharge;
        double recharge = bounded(settings.properties().getOrDefault("recharge_rate", 1.0));
        double envelope = bounded(settings.properties().getOrDefault("launch_envelope", 1.0));
        // Extra-long native trajectories demand more spring work; launch physics and target stay Create-owned.
        var target = ejector.getTargetPosition().subtract(ejector.getBlockPos());
        double distance = Math.max(Math.abs(target.getX()), Math.max(Math.abs(target.getY()), Math.abs(target.getZ())));
        double distanceWeight = Math.min(1, Math.max(0, distance - 1) / 15);
        // Valid inputs may compose beyond the input range; clamp earned work instead
        // of treating that valid product as malformed and resetting it to native 1x.
        return (float) (nativeCharge * Math.max(.75, Math.min(1.5,
            recharge * (1 + (envelope - 1) * distanceWeight))));
    }
    public static float launcherStress(EjectorBlockEntity ejector, float nativeStress) {
        var variant = KineticVariantBehaviour.get(ejector);
        var settings = variant == null ? null : variant.settings();
        if (settings == null || !"create:weighted_ejector".equals(settings.output())) return nativeStress;
        double recharge = bounded(settings.properties().getOrDefault("recharge_rate", 1.0));
        double envelope = bounded(settings.properties().getOrDefault("launch_envelope", 1.0));
        // Pay for the *maximum* possible charge speed, even when currently aimed nearby.
        // Clamp the same valid-product range as charge. At every target distance,
        // paid stress is at least the bounded windup multiplier.
        return (float) (nativeStress * Math.max(.75, Math.min(1.5,
            recharge * Math.max(1, envelope))));
    }
    public static float engineCapacity(PoweredShaftBlockEntity shaft, float nativeStress) {
        if (shaft.getLevel() == null || shaft.enginePos == null || shaft.capacityKey == null || nativeStress <= 0) return nativeStress;
        var engine = shaft.getLevel().getBlockEntity(shaft.getBlockPos().subtract(shaft.enginePos));
        var variant = SteamVariantBehaviour.get(engine);
        var settings = variant == null ? null : variant.settings();
        if (settings == null) return nativeStress;
        double conversion = bounded(settings.properties().getOrDefault("conversion", 1.0));
        double limit = bounded(settings.properties().getOrDefault("mechanical_limit", 1.0));
        // Retain native boiler efficiency and native speed steps. The shaft can neither exceed
        // its engine's rated conversion nor its material-dependent mechanical limit.
        double nativeCeiling = BlockStressValues.getCapacity(shaft.capacityKey);
        double speedStep = Math.max(1, Math.abs(shaft.getGeneratedSpeed()) / 16.0);
        return (float) Math.min(nativeStress * conversion, nativeCeiling * limit / speedStep);
    }
    private static double bounded(double value) { return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1; }
}
