package com.bettercontent.manufacturing.integration.prettypipes;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Read only persisted craft-time settings, never material policy at runtime. */
public final class PrettypipesRates {
    private PrettypipesRates() {}

    public static MachineSettings module(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : SettingsNbt.read(stack.getTag(), id.toString());
    }

    private static double extractionRate(MachineSettings settings) {
        return settings == null ? Double.NaN : settings.properties().getOrDefault("extraction_rate", Double.NaN);
    }

    private static double speedRate(MachineSettings settings) {
        return settings == null ? Double.NaN : settings.properties().getOrDefault("speed_rate", Double.NaN);
    }

    private static boolean boundedRate(double rate) {
        return Double.isFinite(rate) && rate >= .75 && rate <= 1.5;
    }

    /** Plain native interval is 20/15/10 ticks for low/medium/high. */
    public static int ratedExtractionInterval(int nativeInterval, double rate) {
        if (nativeInterval <= 0 || !boundedRate(rate)) return nativeInterval;
        return Math.max(1, (int) Math.round(nativeInterval / rate));
    }

    /** Preserve the native 1/8/64-item tier and the ordinary 64-item stack ceiling. */
    public static int ratedExtractionLimit(int nativeLimit, double rate) {
        if (nativeLimit <= 0 || !boundedRate(rate)) return nativeLimit;
        return (int) Math.max(1, Math.min(Math.max(64, nativeLimit), Math.round(nativeLimit * rate)));
    }

    /** Native speed bonuses are .05/.1/.2 for low/medium/high, not one universal .1. */
    public static float ratedSpeedBonus(float nativeBonus, double rate) {
        if (!Float.isFinite(nativeBonus) || nativeBonus <= 0 || !boundedRate(rate)) return nativeBonus;
        return (float) (nativeBonus * rate);
    }

    public static int extractionInterval(ItemStack stack, int nativeInterval) {
        return ratedExtractionInterval(nativeInterval, extractionRate(module(stack)));
    }

    public static int extractionLimit(ItemStack stack, int nativeLimit) {
        return ratedExtractionLimit(nativeLimit, extractionRate(module(stack)));
    }

    public static float speedBonus(ItemStack stack, float nativeBonus) {
        return ratedSpeedBonus(nativeBonus, speedRate(module(stack)));
    }

    public static double pressurizerRate(MachineSettings settings) {
        if (settings == null || !settings.output().equals("prettypipes:pressurizer")) return 1;
        double value = settings.properties().getOrDefault("pressurizer_rate", Double.NaN);
        return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1;
    }
}
