package com.bettercontent.manufacturing.integration.littlelogistics;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Entity ForgeData survives native save/load; native cargo, routes, fuel and ownership are untouched. */
public final class LittleLogisticsRuntime {
    private LittleLogisticsRuntime() {}
    public static void apply(Entity entity, ItemStack source) {
        if (entity == null || source.isEmpty()) return;
        var id = ForgeRegistries.ITEMS.getKey(source.getItem());
        if (id == null) return;
        MachineSettings settings = SettingsNbt.read(source.getTag(), id.toString());
        if (validDrive(settings) && (settings.output().equals("littlelogistics:energy_locomotive")
                || settings.output().equals("littlelogistics:steam_locomotive")
                || settings.output().equals("littlelogistics:energy_tug")
                || settings.output().equals("littlelogistics:tug")))
            SettingsNbt.write(entity.getPersistentData(), settings);
    }
    public static void apply(BlockEntity tile, ItemStack source) {
        if (tile == null || source.isEmpty() || !"littlelogistics:rapid_hopper".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(source.getItem())))) return;
        MachineSettings settings = SettingsNbt.read(source.getTag(), "littlelogistics:rapid_hopper");
        if (validTransfer(settings)) {
            SettingsNbt.write(tile.getPersistentData(), settings);
            tile.setChanged();
        }
    }
    public static double rate(Entity entity) {
        // In the pinned native registry, each of these four vehicle entity IDs matches its item ID.
        // Avoid allocating/dressing a pick stack every tick just to identify the vehicle.
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null) return 1;
        MachineSettings settings = SettingsNbt.read(entity.getPersistentData(), id.toString());
        return validDrive(settings) ? settings.properties().get("drive_rate") : 1;
    }
    public static double rate(BlockEntity tile) {
        MachineSettings settings = SettingsNbt.read(tile.getPersistentData(), "littlelogistics:rapid_hopper");
        return validTransfer(settings) ? settings.properties().get("transfer_rate") : 1;
    }
    public static ItemStack keep(Entity entity, ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            MachineSettings settings = SettingsNbt.read(entity.getPersistentData(), id.toString());
            if (validDrive(settings))
                SettingsNbt.write(stack.getOrCreateTag(), settings);
        }
        return stack;
    }
    private static boolean validDrive(MachineSettings settings) {
        return settings != null && settings.harvestTier() == null
            && settings.properties().keySet().equals(java.util.Set.of("drive_rate"))
            && settings.materials().keySet().equals(java.util.Set.of("drive"))
            && valid(settings.properties().get("drive_rate"));
    }
    private static boolean validTransfer(MachineSettings settings) {
        return settings != null && settings.harvestTier() == null
            && settings.properties().keySet().equals(java.util.Set.of("transfer_rate"))
            && settings.materials().keySet().equals(java.util.Set.of("actuator"))
            && valid(settings.properties().get("transfer_rate"));
    }
    private static boolean valid(Double rate) {
        return rate != null && Double.isFinite(rate) && rate >= .75 && rate <= 1.5;
    }
}
