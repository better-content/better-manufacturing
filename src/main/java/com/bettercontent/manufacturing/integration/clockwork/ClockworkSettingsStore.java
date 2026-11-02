package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Small Clockwork-only fixed-settings holder. Never evaluates materials during operation. */
public interface ClockworkSettingsStore {
    MachineSettings manufacturing$settings();
    CompoundTag manufacturing$settingsTag();
    void manufacturing$install(CompoundTag tag);

    static String output(BlockEntity entity) {
        var id = ForgeRegistries.BLOCKS.getKey(entity.getBlockState().getBlock());
        return id == null ? "" : id.toString();
    }
    static MachineSettings settings(Object entity) {
        return entity instanceof ClockworkSettingsStore holder ? holder.manufacturing$settings() : null;
    }
    static double rate(Object entity, String key) {
        var settings = settings(entity);
        if (settings == null) return 1;
        double value = settings.properties().getOrDefault(key, 1d);
        return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1;
    }
    static void put(CompoundTag target, BlockEntity entity) {
        if (entity instanceof ClockworkSettingsStore holder && holder.manufacturing$settingsTag() != null)
            target.put(SettingsNbt.KEY, holder.manufacturing$settingsTag().copy());
    }
    static ItemStack drop(ItemStack stack, BlockEntity entity) {
        if (entity instanceof ClockworkSettingsStore holder && holder.manufacturing$settings() != null)
            SettingsNbt.write(stack.getOrCreateTag(), holder.manufacturing$settings());
        return stack;
    }
}
