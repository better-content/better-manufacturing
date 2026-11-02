package com.bettercontent.manufacturing.integration.railbeetle;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
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
    private RailBeetlePartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String property = output.startsWith(PREFIX) ? PROPERTIES.get(output.substring(PREFIX.length())) : null;
        if (property == null || !function.equals(property.equals("drive_performance") ? DRIVES : MODULES)
                || !inputs.keySet().equals(Set.of("drive_shaft", "working_surface")) || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Unsupported Beetle assembly");
        var materials = new LinkedHashMap<String, String>();
        double shaftRate = 1, surfaceRate = 1;
        for (String role : new String[]{"drive_shaft", "working_surface"}) {
            var stack = inputs.get(role);
            if (stack == null || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Missing Tinkers working part");
            String partId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (!partId.equals(role.equals("drive_shaft") ? "tconstruct:tough_handle" : "tconstruct:large_plate"))
                throw new IllegalArgumentException("Wrong Beetle part shape");
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unusable Beetle part material");
            String materialId = material.toString();
            materials.put(role, materialId);
            // Native head/handle working speed approximates active delivery
            // and recuperation cadence; durability approximates braking and
            // load-bearing structure, without a separate material policy.
            boolean speed = property.equals("drive_performance") || property.equals("speed_limit")
                || property.equals("work_rate") || property.equals("recovery") || property.equals("adhesion_delivery");
            NativePartRates.Measure measure = role.equals("drive_shaft")
                ? (speed ? NativePartRates.Measure.HANDLE_MINING_SPEED : NativePartRates.Measure.HANDLE_DURABILITY)
                : (speed ? NativePartRates.Measure.HEAD_MINING_SPEED : NativePartRates.Measure.HEAD_DURABILITY);
            double nativeRate = NativePartRates.relative(part, stack, measure);
            if (role.equals("drive_shaft")) shaftRate = nativeRate; else surfaceRate = nativeRate;
        }
        return new MachineSettings(output, Map.of(property, weightedRate(shaftRate, surfaceRate)), materials, null);
    }

    /** Native handle/plate ratio combined only for mechanical rail work. */
    public static double weightedRate(double shaft, double surface) {
        if (!Double.isFinite(shaft) || !Double.isFinite(surface)) throw new IllegalArgumentException("Nonfinite Beetle rating");
        return Math.max(.75, Math.min(1.5, .45 * shaft + .55 * surface));
    }
}
