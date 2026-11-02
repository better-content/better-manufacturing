package com.bettercontent.manufacturing.integration.sifter;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Craft-time frame rating. The installed Sifter stores only the resulting settings, never its parts. */
public final class SifterFrameSettings {
    public static final String FUNCTION = "better_manufacturing:sifter/machine";
    private static final Set<String> OUTPUTS = Set.of("createsifter:sifter", "createsifter:brass_sifter");
    private SifterFrameSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !OUTPUTS.contains(output)) throw new IllegalArgumentException("Unsupported Sifter frame");
        boolean brass = output.equals("createsifter:brass_sifter");
        if (!inputs.keySet().equals(brass ? Set.of("left", "right", "core") : Set.of("left", "right")))
            throw new IllegalArgumentException("Wrong Sifter frame parts");
        var labels = new LinkedHashMap<String, String>();
        double[] handles = new double[2];
        for (int i = 0; i < 2; i++) {
            String role = i == 0 ? "left" : "right";
            ItemStack stack = inputs.get(role);
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part))
                throw new IllegalArgumentException("Wrong Sifter frame shape");
            var partId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (partId == null || !partId.toString().equals("tconstruct:tool_handle"))
                throw new IllegalArgumentException("Wrong Sifter frame shape");
            // Screen stroke frequency follows the native handle mining-speed modifier;
            // iron is the same-stat reference, not a screen-rate material table.
            handles[i] = NativePartRates.relative(part, stack, Measure.HANDLE_MINING_SPEED);
            labels.put(role, part.getMaterial(stack).toString());
        }
        double predecessor = 1;
        if (brass) {
            ItemStack core = inputs.get("core");
            if (core == null || core.isEmpty()) throw new IllegalArgumentException("Brass Sifter requires a Sifter");
            var coreId = ForgeRegistries.ITEMS.getKey(core.getItem());
            if (coreId == null || !coreId.toString().equals("createsifter:sifter"))
                throw new IllegalArgumentException("Brass Sifter requires a Sifter");
            var previous = SettingsNbt.read(core.getTag(), coreId.toString());
            // Only an actually unconfigured native Sifter contributes 1x. A
            // present but malformed/wrong-function record cannot be laundered
            // into a new brass frame or supply an airflow/pressure work rate.
            if (core.hasTag() && core.getTag().contains(SettingsNbt.KEY)
                && (previous == null || !previous.properties().keySet().equals(Set.of("work_rate"))))
                throw new IllegalArgumentException("Invalid configured Sifter predecessor");
            predecessor = previous == null ? 1 : previous.rate(); // Unconfigured native predecessor is 1x.
            if (previous != null) previous.materials().forEach((role, material) -> labels.put("core_" + role, material));
        }
        return new MachineSettings(output, Map.of("work_rate", frameRate(handles[0], handles[1], brass, predecessor)), labels, null);
    }

    /** Native handle rates and, for brass, the predecessor share one bounded work envelope. */
    public static double frameRate(double left, double right, boolean brass, double predecessor) {
        return bounded((bounded(left) + bounded(right) + (brass ? bounded(predecessor) : 0)) / (brass ? 3 : 2));
    }

    /** Two native parts of the same Sifter share one total work/stress envelope, not multiplied bonuses. */
    public static double combined(double frameRate, double installedMeshRate, boolean hasMesh) {
        return hasMesh ? bounded((bounded(frameRate) + bounded(installedMeshRate)) / 2) : bounded(frameRate);
    }
    private static double bounded(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid screen rate");
        return Math.max(.75, Math.min(1.5, value));
    }
}
