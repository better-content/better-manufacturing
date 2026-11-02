package com.bettercontent.manufacturing.integration.pneuprocess;

import com.bettercontent.manufacturing.api.MachineSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** A small bridge to already-baked settings; absent/corrupt records are native baseline. */
public final class PneuProcessNative {
    private PneuProcessNative() {}
    public interface Holder {
        MachineSettings betterManufacturing$processSettings();
        void betterManufacturing$setProcessSettings(MachineSettings settings);
    }
    public static String output(BlockEntity entity) {
        var id = ForgeRegistries.BLOCKS.getKey(entity.getBlockState().getBlock());
        return id == null ? "" : id.toString();
    }
    public static double rate(BlockEntity entity, String property) {
        if (!PneuProcessSettings.supports(output(entity)) || !(entity instanceof Holder holder)) return 1;
        var settings = holder.betterManufacturing$processSettings();
        double rate = settings == null ? 1 : settings.properties().getOrDefault(property, 1d);
        return Double.isFinite(rate) && rate >= .75 && rate <= 1.5 ? rate : 1;
    }
}
