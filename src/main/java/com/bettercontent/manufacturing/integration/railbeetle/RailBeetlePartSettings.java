package com.bettercontent.manufacturing.integration.railbeetle;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Rail machinery is calculated once, from the two working parts; installed stacks retain only settings. */
public final class RailBeetlePartSettings {
    public static final String DRIVES = "better_manufacturing:rail_beetle/drives";
    public static final String MODULES = "better_manufacturing:rail_beetle/modules";
    private static final String PREFIX = "better_rail_beetle:";
    private static final Map<String, String> PROPERTIES = Map.ofEntries(
            Map.entry("better_rail_beetle", "drive_performance"),
            Map.entry("steam_drive", "drive_performance"), Map.entry("flux_traction_motor", "drive_performance"),
            Map.entry("source_impeller", "drive_performance"), Map.entry("lifeforce_ram", "drive_performance"),
            Map.entry("pneumatic_expansion_motor", "drive_performance"), Map.entry("soul_combustor", "drive_performance"),
            Map.entry("spirit_warping_engine", "drive_performance"),
            Map.entry("high_speed_governor_1", "speed_limit"), Map.entry("high_speed_governor_2", "speed_limit"),
            Map.entry("exhaust_recuperator_1", "recovery"), Map.entry("exhaust_recuperator_2", "recovery"),
            Map.entry("brake_manifold_1", "deceleration"), Map.entry("brake_manifold_2", "deceleration"),
            Map.entry("adhesion_sanders_1", "adhesion_delivery"), Map.entry("adhesion_sanders_2", "adhesion_delivery"),
            Map.entry("compound_torque_clutch_1", "torque_transfer"), Map.entry("compound_torque_clutch_2", "torque_transfer"),
            Map.entry("reinforced_drawgear_1", "towing_limit"), Map.entry("reinforced_drawgear_2", "towing_limit"),
            Map.entry("trestle_erector_1", "work_rate"), Map.entry("trestle_erector_2", "work_rate"));
    private static final Map<String, String> RATINGS = Map.of(
            "drive_performance", "rail_drive", "speed_limit", "rail_governor",
            "recovery", "rail_recuperator", "deceleration", "rail_brakes",
            "adhesion_delivery", "rail_adhesion", "torque_transfer", "rail_torque",
            "towing_limit", "rail_drawgear", "work_rate", "rail_trestle");

    private RailBeetlePartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String property = output.startsWith(PREFIX) ? PROPERTIES.get(output.substring(PREFIX.length())) : null;
        if (property == null || !function.equals(property.equals("drive_performance") ? DRIVES : MODULES)
                || !inputs.keySet().equals(Set.of("drive_shaft", "working_surface")) || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Unsupported Beetle assembly");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        if (!catalog.ready()) throw new IllegalArgumentException("Material ratings unavailable");
        String rating = RATINGS.get(PROPERTIES.get(output.substring(PREFIX.length())));
        var reference = catalog.policies().get("tconstruct:iron");
        if (reference == null || reference.traits().getOrDefault(rating, 0d) <= 0)
            throw new IllegalArgumentException("Missing Beetle reference rating");
        var materials = new LinkedHashMap<String, String>();
        double result = 0;
        for (String role : new String[]{"drive_shaft", "working_surface"}) {
            var stack = inputs.get(role);
            if (stack == null || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Missing Tinkers working part");
            String partId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (!partId.equals(role.equals("drive_shaft") ? "tconstruct:tough_handle" : "tconstruct:large_plate"))
                throw new IllegalArgumentException("Wrong Beetle part shape");
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unusable Beetle part material");
            String materialId = material.toString();
            var policy = catalog.policies().getOrDefault(materialId, catalog.policies().get(material.getId().toString()));
            if (policy == null || !policy.supportedParts().contains(partId)) throw new IllegalArgumentException("No supported Beetle part rating");
            double value = policy.traits().getOrDefault(rating, 0d);
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Missing role rating");
            materials.put(role, materialId);
            result += (role.equals("drive_shaft") ? 0.45 : 0.55) * value / reference.traits().get(rating);
        }
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Nonfinite Beetle rating");
        return new MachineSettings(output, Map.of(PROPERTIES.get(output.substring(PREFIX.length())),
                Math.max(.75, Math.min(1.5, result))), materials, null);
    }
}
