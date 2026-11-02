package com.bettercontent.manufacturing.integration.powergrid;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.bettercontent.manufacturing.mixin.PowergridThermalMassAccess;
import com.bettercontent.manufacturing.mixin.PowergridWindingPartsAccess;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.fan.ElectricFanBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.kinetics.servo.ServoBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.InductionRotorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.registries.ForgeRegistries;

/** Read only persisted build settings; never recompute against live material policy. */
public final class PowergridRatings {
    private static final Set<String> PLACED = Set.of("basin_heater", "heating_coil", "electric_motor",
        "constant_speed_motor", "servo", "electric_fan", "generator_clutch", "generator_commutator",
        "generator_induction_rotor", "generator_vertical_commutator");
    private PowergridRatings() {}
    /** Called only after actual native placement; never copies a component stack into an inventory. */
    public static void install(BlockEntity block, ItemStack craftedItem) {
        if (block == null || craftedItem.isEmpty()) return;
        var id = ForgeRegistries.BLOCKS.getKey(block.getBlockState().getBlock());
        if (id == null || !id.getNamespace().equals("powergrid") || !PLACED.contains(id.getPath())) return;
        var settings = SettingsNbt.read(craftedItem.getTag(), id.toString());
        if (settings == null) return;
        SettingsNbt.write(block.getPersistentData(), settings);
        if (block instanceof SmartBlockEntity smart) {
            if (id.getPath().equals("basin_heater") || id.getPath().equals("heating_coil")) {
                ThermalBehaviour thermal = BlockEntityBehaviour.get(smart, ThermalBehaviour.TYPE);
                if (thermal != null) thermal.setThermalMass(((PowergridThermalMassAccess) thermal).manufacturing$getThermalMass() / (float) ratio(block, "thermal_mass_ratio"));
            }
            if (block instanceof IElectricEntity electric && block instanceof PowergridHeaterCircuit heater)
                heater.manufacturing$setHeaterResistance((float) (electric.resistance() / ratio(block, "heater_coil_ratio")));
            if (block instanceof ElectricFanBlockEntity fan && fan instanceof PowergridFanMotor bridge) bridge.manufacturing$setMotorResistance((float) (fan.resistance() / ratio(block, "drive_ratio")));
            if (block instanceof IElectricEntity electric && block instanceof PowergridMotorCoil bridge) {
                float nativeResistance = block instanceof ServoBlockEntity servo
                    ? servo.resistance(servo.getGeneratedSpeed() == 0 ? "idle" : "on") : electric.resistance();
                bridge.manufacturing$setMotorResistance((float) (nativeResistance / ratio(block, "drive_ratio")));
            }
            if (block instanceof PowergridGeneratorCoupling coupling) coupling.manufacturing$applyInternalResistance(ratio(block, "generator_ratio"));
            if (block instanceof InductionRotorBlockEntity rotor) rotor.totalField.invalidate();
            if (block instanceof GeneratorClutchBlockEntity clutch && clutch.getLevel() != null && !clutch.getLevel().isClientSide && clutch.hasNetwork())
                clutch.getOrCreateNetwork().updateStressFor(clutch, clutch.calculateStressApplied());
        }
        block.setChanged();
        if (block instanceof SmartBlockEntity smart && block.getLevel() != null && !block.getLevel().isClientSide) smart.sendData();
    }
    /** Each native coil has an individual crafting-time setting; absent/invalid coils are baseline. */
    public static double coilRatio(ItemStack coil) {
        var id = ForgeRegistries.ITEMS.getKey(coil.getItem());
        if (id == null || !id.toString().equals("powergrid:copper_coil")) return 1;
        var settings = SettingsNbt.read(coil.getTag(), "powergrid:copper_coil");
        if (settings == null) return 1;
        double rating = settings.properties().getOrDefault("generator_ratio", 1d);
        return Double.isFinite(rating) && rating >= .75 && rating <= 1.5 ? rating : 1;
    }
    /** This BE hosts the installed copper coil. Its native loot returns a copper coil, not a winding item. */
    public static double windingRatio(BlockEntity block) {
        if (!(block instanceof WindingBlockEntity winding)) return 1;
        // Native getCoilCount() populates this exact series segment set immediately before native resistance().
        var segments = ((PowergridWindingPartsAccess) winding).manufacturing$collectedParts();
        if (segments == null || segments.isEmpty()) return singleCoilRatio(winding);
        double inverse = 0;
        for (var segment : segments) inverse += 1 / singleCoilRatio(segment);
        return segments.size() / inverse; // equivalent resistance of heterogeneous actual consumed coils
    }
    private static double singleCoilRatio(BlockEntity block) {
        var settings = SettingsNbt.read(block.getPersistentData(), "powergrid:copper_coil");
        if (settings == null) return 1;
        double rating = settings.properties().getOrDefault("generator_ratio", 1d);
        return Double.isFinite(rating) && rating >= .75 && rating <= 1.5 ? rating : 1;
    }
    public static void installWinding(BlockEntity block, double rating) {
        if (!(block instanceof WindingBlockEntity winding) || !Double.isFinite(rating) || rating < .75 || rating > 1.5) return;
        SettingsNbt.write(block.getPersistentData(), new MachineSettings("powergrid:copper_coil", Map.of("generator_ratio", rating), Map.of(), null));
        winding.setChanged();
        // The owner schedules its native series/parallel graph recalculation on the next tick.
        winding.onNeighborChanged(winding.getBlockPos());
        if (winding.getLevel() != null && !winding.getLevel().isClientSide) winding.sendData();
    }
    public static MachineSettings settings(BlockEntity block) {
        if (block == null) return null;
        var id = ForgeRegistries.BLOCKS.getKey(block.getBlockState().getBlock());
        if (id == null || !id.getNamespace().equals("powergrid") || !PLACED.contains(id.getPath())) return null;
        return SettingsNbt.read(block.getPersistentData(), id.toString());
    }
    public static double ratio(BlockEntity block, String property) {
        MachineSettings settings = settings(block);
        if (settings == null) return 1;
        double value = settings.properties().getOrDefault(property, 1d);
        return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1;
    }
}
