package com.bettercontent.manufacturing.integration.precisionquarry;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartCapture;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import java.util.Map;

/** Only crafting consults Tinkers materials; native machines consume the saved bounded result. */
public final class PrecisionQuarrySettings {
    private PrecisionQuarrySettings() {}
    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String required = switch (function) {
            case "better_manufacturing:ae2/precision" -> "ae2:inscriber";
            case "better_manufacturing:alexscaves/quarry_work" -> "alexscaves:quarry_smasher";
            case "better_manufacturing:rats/quarry_work" -> output.equals("rats:rat_quarry") || output.equals("rats:rat_upgrade_quarry") ? output : "";
            default -> "";
        };
        if (!output.equals(required) || inputs.size() != 1 || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Unknown precision/quarry machine or wrong part count");
        String role = function.equals("better_manufacturing:ae2/precision") ? "tooling" : "head";
        ItemStack stack = inputs.get(role);
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part))
            throw new IllegalArgumentException("Missing native working head");
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String expected = function.equals("better_manufacturing:rats/quarry_work") ? "tconstruct:pick_head" : "tconstruct:hammer_head";
        if (id == null || !id.toString().equals(expected)) throw new IllegalArgumentException("Wrong working head shape");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Invalid head material");
        var registry = MaterialRegistry.getInstance();
        var head = registry.<HeadMaterialStats>getMaterialStats(material.getId(), HeadMaterialStats.ID).orElseThrow(() -> new IllegalArgumentException("No native head stats"));
        var iron = registry.<HeadMaterialStats>getMaterialStats(new MaterialId("tconstruct:iron"), HeadMaterialStats.ID).orElseThrow();
        float value = function.equals("better_manufacturing:rats/quarry_work") ? head.miningSpeed() : head.attack();
        float baseline = function.equals("better_manufacturing:rats/quarry_work") ? iron.miningSpeed() : iron.attack();
        if (!Float.isFinite(value) || value <= 0 || !Float.isFinite(baseline) || baseline <= 0)
            throw new IllegalArgumentException("Invalid native working head stats");
        NativePartCapture.record(stack, function.equals("better_manufacturing:rats/quarry_work")
            ? NativePartRates.Measure.HEAD_MINING_SPEED : NativePartRates.Measure.HEAD_ATTACK, value, baseline);
        double rate = Math.max(.75, Math.min(1.5, value / baseline));
        boolean rat = function.equals("better_manufacturing:rats/quarry_work");
        var tier = rat ? TierSortingRegistry.getName(head.tier()) : null;
        if (rat && tier == null) throw new IllegalArgumentException("Unknown harvest tier");
        // Hardness is meaningful for a mining rat, not the quarry's platform-building radius.
        if (rat && output.equals("rats:rat_upgrade_quarry"))
            NativePartCapture.recordAuxiliary("native_head", "hardness_limit", head.tier().getLevel());
        var properties = rat && output.equals("rats:rat_upgrade_quarry")
            ? Map.of("work_rate", rate, "hardness_limit", (double) head.tier().getLevel())
            : Map.of("work_rate", rate);
        return new MachineSettings(output, properties, Map.of(role, material.toString()), tier == null ? null : tier.toString());
    }
    public static MachineSettings read(CompoundTag holder, String output) {
        var settings = SettingsNbt.read(holder, output);
        if (settings == null) return null;
        var properties = settings.properties();
        Double rate = properties.get("work_rate");
        if (rate == null || rate < .75 || rate > 1.5) return null;
        if (output.equals("rats:rat_upgrade_quarry")) {
            Double limit = properties.get("hardness_limit");
            if (limit == null || limit < 0 || limit > 100 || settings.harvestTier() == null) return null;
        } else if (properties.containsKey("hardness_limit")) return null;
        return settings;
    }
    public static void installBlock(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide || stack.isEmpty()) return;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !(id.toString().equals("ae2:inscriber") || id.toString().equals("rats:rat_quarry"))) return;
        if (level.getBlockEntity(pos) instanceof BlockReceiver receiver)
            receiver.manufacturing$install(read(stack.getTag(), id.toString()));
    }
    public interface BlockReceiver {
        void manufacturing$install(MachineSettings settings);
    }
    public interface EntityReceiver {
        void manufacturing$installHead(MachineSettings settings);
    }
}
