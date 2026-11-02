package com.bettercontent.manufacturing.integration.bees;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Native BE Forge persistent data saves only final baked settings, never consumed parts. */
public final class BeesSettings {
    private BeesSettings() {}

    public static boolean supported(String id) {
        if (id.equals("complicated_bees:centrifuge")) return true;
        if (!id.startsWith("complicated_bees:gyrofuge_") || !id.endsWith("_unit")) return false;
        for (String kind : new String[]{"speed", "efficiency", "extraction", "processing"}) {
            if (id.equals("complicated_bees:gyrofuge_" + kind + "_unit")
                || id.equals("complicated_bees:gyrofuge_basic_" + kind + "_unit")
                || id.equals("complicated_bees:gyrofuge_advanced_" + kind + "_unit")) return true;
        }
        return false;
    }

    public static String id(BlockEntity be) {
        var id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        return id == null ? "" : id.toString();
    }

    private static MachineSettings valid(CompoundTag holder, String id) {
        if (!supported(id)) return null;
        MachineSettings settings = SettingsNbt.read(holder, id);
        if (settings == null || settings.properties().size() != 1) return null;
        Double rate = settings.properties().get("bees_rate");
        return rate != null && Double.isFinite(rate) && rate >= .75 && rate <= 1.5 ? settings : null;
    }

    public static MachineSettings read(BlockEntity be) {
        return valid(be.getPersistentData(), id(be));
    }

    public static double rate(BlockEntity be) {
        MachineSettings settings = read(be);
        return settings == null ? 1.0 : settings.properties().get("bees_rate");
    }

    public static void install(BlockEntity be, ItemStack stack) {
        String id = id(be);
        if (!supported(id)) return;
        MachineSettings baked = valid(stack.getTag(), id);
        CompoundTag data = be.getPersistentData();
        data.remove(SettingsNbt.KEY);
        if (baked != null) SettingsNbt.write(data, baked);
        be.setChanged();
        // Native assembly can precede BlockItem's final tag transfer. Recompute the
        // already assembled controller once, from its own logic and native energy budget.
        if (id.startsWith("complicated_bees:gyrofuge_")) BeesGyrofugeRefresh.afterInstall(be);
    }

    public static void copy(BlockEntity be, ItemStack stack) {
        String id = id(be);
        if (supported(id) && stack.getItem() == be.getBlockState().getBlock().asItem()) {
            MachineSettings settings = read(be);
            if (settings != null) SettingsNbt.write(stack.getOrCreateTag(), settings);
        }
    }
}
