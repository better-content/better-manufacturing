package com.bettercontent.manufacturing.integration.powergrid;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.*;

/** Fixed per-build settings; neither the network nor a placed block reads materials. */
public final class PowergridPartSettings {
    private PowergridPartSettings() {}
    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String property;
        Map<String, String> roles;
        switch (function) {
            case "better_manufacturing:powergrid/heating" -> {
                if (!Set.of("powergrid:basin_heater", "powergrid:heating_coil").contains(output)) throw new IllegalArgumentException("Wrong heater");
                property = "thermal_mass_ratio";
                roles = Map.of("thermal_support", "tconstruct:large_plate");
            }
            case "better_manufacturing:powergrid/motors" -> {
                if (!Set.of("powergrid:electric_motor", "powergrid:constant_speed_motor", "powergrid:servo", "powergrid:electric_fan").contains(output)) throw new IllegalArgumentException("Wrong motor");
                property = "drive_ratio";
                roles = Map.of("rotor", "tconstruct:tough_handle");
            }
            case "better_manufacturing:powergrid/generation" -> {
                if (!Set.of("powergrid:copper_coil", "powergrid:resistive_coil", "powergrid:generator_clutch", "powergrid:generator_commutator", "powergrid:generator_induction_rotor", "powergrid:generator_vertical_commutator").contains(output)) throw new IllegalArgumentException("Wrong generator part");
                property = "generator_ratio";
                roles = Map.of("shaft", "tconstruct:tough_handle", "surface", "tconstruct:large_plate");
            }
            default -> throw new IllegalArgumentException("Wrong function");
        }
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Materials not loaded");
        boolean heater = function.equals("better_manufacturing:powergrid/heating");
        int coilCount = heater ? output.equals("powergrid:basin_heater") ? 5 : 2 : 0;
        var expected = new HashSet<>(roles.keySet());
        for (int i = 0; i < coilCount; i++) expected.add("coil_" + i);
        if (!inputs.keySet().equals(expected)) throw new IllegalArgumentException("Wrong part bindings");
        var labels = new HashMap<String, String>();
        double rotorRate = 1, shaftRate = 1, surfaceRate = 1, thermalRate = 1;
        for (var role : roles.entrySet()) {
            var stack = inputs.get(role.getKey());
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Missing native part");
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !id.toString().equals(role.getValue())) throw new IllegalArgumentException("Wrong part shape");
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported material");
            var label = material.toString();
            // Approximate heat response from the native head's structural
            // durability; this changes thermal mass, never creates heat.
            NativePartRates.Measure measure = role.getKey().equals("surface") || role.getKey().equals("thermal_support")
                ? NativePartRates.Measure.HEAD_DURABILITY : NativePartRates.Measure.HANDLE_MINING_SPEED;
            double nativeRate = NativePartRates.relative(part, stack, measure);
            if (role.getKey().equals("thermal_support")) thermalRate = nativeRate;
            else if (role.getKey().equals("shaft")) shaftRate = nativeRate;
            else if (role.getKey().equals("surface")) surfaceRate = nativeRate;
            else rotorRate = nativeRate;
            labels.put(role.getKey(), label);
        }
        var values = new HashMap<String, Double>();
        values.put(property, heater ? thermalRate : roles.size() == 2 ? generatorRate(shaftRate, surfaceRate) : rotorRate);
        if (heater) {
            double coilRatio = 1.5;
            for (int i = 0; i < coilCount; i++) {
                var stack = inputs.get("coil_" + i);
                var id = stack == null ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null || !id.toString().equals("powergrid:resistive_coil")) throw new IllegalArgumentException("Wrong heater resistor");
                // The weakest installed resistor bounds current; absent pre-conversion settings mean native baseline.
                var baked = SettingsNbt.read(stack.getTag(), "powergrid:resistive_coil");
                double component = baked == null ? 1 : baked.properties().getOrDefault("generator_ratio", 1d);
                coilRatio = Math.min(coilRatio, component >= .75 && component <= 1.5 ? component : 1);
            }
            values.put("heater_coil_ratio", coilRatio);
        }
        return new MachineSettings(output, values, labels, null);
    }

    /** Two native working parts in the generator's existing shaft/surface geometry. */
    public static double generatorRate(double shaft, double surface) {
        if (!Double.isFinite(shaft) || !Double.isFinite(surface)) throw new IllegalArgumentException("Nonfinite generator rate");
        return Math.max(.75, Math.min(1.5, (shaft + surface) / 2));
    }
}
