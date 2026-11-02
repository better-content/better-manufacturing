package com.bettercontent.manufacturing.integration.pneupressure;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import me.desht.pneumaticcraft.common.block.entity.PressureChamberValveBlockEntity;
import me.desht.pneumaticcraft.common.block.IBlockPressureChamber;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/** Per-block immutable-at-crafting settings, stored apart from the native air and upgrade NBT. */
public interface PneupressureBuild {
    Set<String> COMPRESSOR_IDS = Set.of("air_compressor", "advanced_air_compressor", "manual_compressor",
        "liquid_compressor", "advanced_liquid_compressor", "flux_compressor", "electrostatic_compressor",
        "solar_compressor", "thermal_compressor");
    Set<String> PRESSURE_IDS = Set.of("pressure_tube", "advanced_pressure_tube", "reinforced_pressure_tube",
        "tube_junction", "pressure_chamber_wall", "pressure_chamber_glass", "pressure_chamber_valve",
        "pressure_chamber_interface");
    Set<String> ROTATION_IDS = Set.of("turbine_blade", "turbine_rotor", "pneumatic_cylinder");

    Tag pneupressure$raw();
    void pneupressure$install(Tag raw);

    static MachineSettings settings(BlockEntity block) {
        if (!(block instanceof PneupressureBuild build)) return null;
        var id = ForgeRegistries.BLOCKS.getKey(block.getBlockState().getBlock());
        if (id == null) return null;
        var tag = new CompoundTag();
        if (build.pneupressure$raw() != null) tag.put(SettingsNbt.KEY, build.pneupressure$raw().copy());
        var settings = SettingsNbt.read(tag, id.toString());
        if (settings == null || !id.getNamespace().equals("pneumaticcraft")) return null;
        var properties = settings.properties();
        String name = id.getPath();
        if (COMPRESSOR_IDS.contains(name)) {
            if (!properties.keySet().equals(Set.of("compression_rate", "pressure_envelope"))) return null;
            return bounded(properties.get("compression_rate"), 1.5) && bounded(properties.get("pressure_envelope"), 1)
                ? settings : null;
        }
        if (PRESSURE_IDS.contains(name)) {
            return properties.keySet().equals(Set.of("pressure_envelope"))
                && bounded(properties.get("pressure_envelope"), 1) ? settings : null;
        }
        if (ROTATION_IDS.contains(name)) {
            return properties.keySet().equals(Set.of("rotation_rate"))
                && bounded(properties.get("rotation_rate"), 1.5) ? settings : null;
        }
        return null;
    }
    private static boolean bounded(Double value, double max) {
        return value != null && Double.isFinite(value) && value >= .75 && value <= max;
    }
    static double rate(BlockEntity block, String property) {
        var settings = settings(block);
        if (settings == null) return 1;
        double value = settings.properties().getOrDefault(property, 1.0);
        return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1;
    }

    /** The weakest wall, pane or valve limits the chamber; native formed geometry remains authoritative. */
    static float pressureEnvelope(BlockEntity owner) {
        float limit = (float) Math.min(1, rate(owner, "pressure_envelope"));
        if (!(owner instanceof PressureChamberValveBlockEntity valve) || owner.getLevel() == null) return limit;
        int size = valve.multiBlockSize;
        if (size < 3 || size > 5) return limit;
        int minX = valve.multiBlockX, minY = valve.multiBlockY, minZ = valve.multiBlockZ;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++) {
            if (x != 0 && x != size - 1 && y != 0 && y != size - 1 && z != 0 && z != size - 1) continue;
            pos.set(minX + x, minY + y, minZ + z);
            if (owner.getLevel().getBlockState(pos).getBlock() instanceof IBlockPressureChamber)
                limit = (float) Math.min(limit, rate(owner.getLevel().getBlockEntity(pos), "pressure_envelope"));
        }
        return limit;
    }
}
