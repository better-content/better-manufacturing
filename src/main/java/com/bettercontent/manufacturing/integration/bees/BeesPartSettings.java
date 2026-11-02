package com.bettercontent.manufacturing.integration.bees;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bake only material ratings; native bee recipes, tolerance categories and yields remain authoritative. */
public final class BeesPartSettings {
    private BeesPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material data not loaded");
        String part;
        Set<String> roles;
        if (function.equals("better_manufacturing:bees/separation") && output.equals("complicated_bees:centrifuge")) {
            roles = Set.of("rotor"); part = "tconstruct:tough_handle";
        } else if (function.startsWith("better_manufacturing:bees/gyrofuge/")
            && gyrofugeOutput(output, function.substring("better_manufacturing:bees/gyrofuge/".length()))) {
            roles = Set.of("rotor", "plate"); part = null;
        } else throw new IllegalArgumentException("Unsupported bees part function/output");
        if (!inputs.keySet().equals(roles)) throw new IllegalArgumentException("Wrong bees working parts");
        var labels = new LinkedHashMap<String, String>();
        double rate = 1.5;
        for (var entry : inputs.entrySet()) {
            String required = part != null ? part : entry.getKey().equals("rotor") ? "tconstruct:tough_handle" : "tconstruct:large_plate";
            ItemStack stack = entry.getValue();
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Missing bees working part");
            String shape = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            if (!shape.equals(required) || !(stack.getItem() instanceof ToolPartItem tool)) throw new IllegalArgumentException("Wrong bees part shape");
            var material = tool.getMaterial(stack);
            if (!tool.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported bees material");
            String materialId = material.toString();
            // Native rotational work, not bee genetics, output chance, or
            // a second material-to-machine trait table, defines this setting.
            rate = weakest(rate, NativePartRates.relative(tool, stack, entry.getKey().equals("rotor")
                ? NativePartRates.Measure.HANDLE_MINING_SPEED : NativePartRates.Measure.HEAD_MINING_SPEED));
            labels.put(entry.getKey(), materialId);
        }
        // All keys are family-specific: native progress/consumable/energy hooks decide what is meaningful.
        return new MachineSettings(output, Map.of("bees_rate", rate), labels, null);
    }

    /** The slowest native moving part limits rotational work; no yield/genetics bonus. */
    public static double weakest(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) throw new IllegalArgumentException("Nonfinite bee rate");
        return Math.min(first, second);
    }

    private static boolean gyrofugeOutput(String output, String kind) {
        if (!Set.of("speed", "efficiency", "extraction", "processing").contains(kind)) return false;
        return output.equals("complicated_bees:gyrofuge_" + kind + "_unit")
            || output.equals("complicated_bees:gyrofuge_basic_" + kind + "_unit")
            || output.equals("complicated_bees:gyrofuge_advanced_" + kind + "_unit");
    }
}
