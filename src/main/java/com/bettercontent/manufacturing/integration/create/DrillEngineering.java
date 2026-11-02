package com.bettercontent.manufacturing.integration.create;

import com.bettercontent.manufacturing.api.MachineBuild;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;

/** Runtime reads only the crafting-time settings. Material reload cannot change a built drill. */
public final class DrillEngineering {
    public static final String CHASSIS = "create:mechanical_drill", FUNCTION = "better_manufacturing:create/cutting";
    public record State(double rate, Tier tier) {
        public boolean allowed() { return true; }
        public String blockedReason() { return ""; }
        public boolean canCut(BlockState target) { return tier == null || TierSortingRegistry.isCorrectTierForDrops(tier, target); }
        /** Retained-part recipes are inactive; no original components exist in a configured variant. */
        public MachineBuild build() { return null; }
    }
    private DrillEngineering() {}
    public static State evaluate(CompoundTag holder) { return evaluate(holder, CHASSIS); }
    public static State evaluate(CompoundTag holder, String output) {
        return fromSettings(SettingsNbt.read(holder, output));
    }
    public static State fromSettings(com.bettercontent.manufacturing.api.MachineSettings settings) {
        // A valid v1 record for another Create family must never become drill speed merely
        // because MachineSettings.rate() can also read airflow/pressure as a convenience.
        if (settings == null || !CHASSIS.equals(settings.output())
            || !settings.properties().keySet().equals(java.util.Set.of("work_rate"))
            || settings.harvestTier() == null) return new State(1, null);
        Tier tier = TierSortingRegistry.byName(new ResourceLocation(settings.harvestTier()));
        if (tier == null) return new State(1, null);
        return new State(settings.rate(), tier);
    }
    public static State evaluate(CompoundTag holder, boolean clientSide) { return evaluate(holder); }
    // Kept for dormant experimental fixtures; neither method drives live machine state anymore.
    public static void invalidate() {}
    public static long epoch() { return 0; }
    public static boolean safeItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.hasTag() && !java.util.Set.of(SettingsNbt.KEY, "display").containsAll(stack.getTag().getAllKeys())) return false;
        var nbt = stack.serializeNBT();
        return !nbt.contains("ForgeCaps") || nbt.getCompound("ForgeCaps").isEmpty();
    }
}
