package com.bettercontent.manufacturing.integration.bees;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
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
        String part, trait;
        Set<String> roles;
        if (function.equals("better_manufacturing:bees/separation") && output.equals("complicated_bees:centrifuge")) {
            roles = Set.of("rotor"); part = "tconstruct:tough_handle"; trait = "bees_rotor_rate";
        } else if (function.startsWith("better_manufacturing:bees/gyrofuge/")
            && gyrofugeOutput(output, function.substring("better_manufacturing:bees/gyrofuge/".length()))) {
            roles = Set.of("rotor", "plate"); part = null; trait = null;
        } else throw new IllegalArgumentException("Unsupported bees part function/output");
        if (!inputs.keySet().equals(roles)) throw new IllegalArgumentException("Wrong bees working parts");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        var reference = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || reference == null) throw new IllegalArgumentException("No bees material ratings");
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
            var policy = catalog.policies().get(materialId);
            if (policy == null) policy = catalog.policies().get(material.getId().toString());
            if (policy == null || !policy.supportedParts().contains(required)) throw new IllegalArgumentException("No supported bees part rating");
            String roleTrait = trait != null ? trait : entry.getKey().equals("rotor") ? "bees_rotor_rate" : "bees_plate_rate";
            double value = policy.traits().getOrDefault(roleTrait, 0.0);
            double iron = reference.traits().getOrDefault(roleTrait, 0.0);
            if (!(value > 0) || !(iron > 0) || !Double.isFinite(value)) throw new IllegalArgumentException("No bees part rate");
            rate = Math.min(rate, Math.max(.75, Math.min(1.5, value / iron)));
            labels.put(entry.getKey(), materialId);
        }
        // All keys are family-specific: native progress/consumable/energy hooks decide what is meaningful.
        return new MachineSettings(output, Map.of("bees_rate", rate), labels, null);
    }

    private static boolean gyrofugeOutput(String output, String kind) {
        if (!Set.of("speed", "efficiency", "extraction", "processing").contains(kind)) return false;
        return output.equals("complicated_bees:gyrofuge_" + kind + "_unit")
            || output.equals("complicated_bees:gyrofuge_basic_" + kind + "_unit")
            || output.equals("complicated_bees:gyrofuge_advanced_" + kind + "_unit");
    }
}
