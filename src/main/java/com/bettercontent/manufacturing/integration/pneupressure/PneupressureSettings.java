package com.bettercontent.manufacturing.integration.pneupressure;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Baked at crafting only; pressure ratings never exceed the native tier's limit. */
public final class PneupressureSettings {
    public static final String COMPRESSION = "better_manufacturing:pneupressure/compression";
    public static final String PRESSURE = "better_manufacturing:pneupressure/pressure";
    public static final String ROTATION = "better_manufacturing:pneupressure/rotation";
    public static final String VALVE_BLANK = "better_manufacturing:pneupressure/valve_blank";
    public static final String VALVE_BLANK_ID = "better_manufacturing:compressed_iron_valve_blank";
    private static final Set<String> COMPRESSORS = Set.of("air_compressor", "manual_compressor", "liquid_compressor", "flux_compressor", "electrostatic_compressor", "solar_compressor", "thermal_compressor");
    private static final Set<String> BODIES = Set.of("pressure_tube", "tube_junction", "pressure_chamber_wall", "pressure_chamber_glass", "pressure_chamber_valve", "pressure_chamber_interface");
    private static final Set<String> ROTORS = Set.of("turbine_blade", "turbine_rotor", "pneumatic_cylinder");
    private PneupressureSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material data not loaded");
        if (VALVE_BLANK.equals(function)) {
            if (!VALVE_BLANK_ID.equals(output) || !inputs.keySet().equals(Set.of("body")))
                throw new IllegalArgumentException("Unsupported valve blank parts");
            var labels = new LinkedHashMap<String, String>();
            double body = rating(inputs.get("body"), "tconstruct:large_plate", "pressure_body", labels, "body");
            return new MachineSettings(output, Map.of("pressure_envelope", Math.min(1, body)), labels, null);
        }
        if (!output.startsWith("pneumaticcraft:")) throw new IllegalArgumentException("Wrong output namespace");
        String name = output.substring("pneumaticcraft:".length());
        boolean compressor = function.equals(COMPRESSION) && COMPRESSORS.contains(name);
        boolean body = function.equals(PRESSURE) && BODIES.contains(name);
        boolean rotor = function.equals(ROTATION) && ROTORS.contains(name);
        if (!compressor && !body && !rotor) throw new IllegalArgumentException("Unsupported pressure variant");
        if (!inputs.keySet().equals(compressor ? (name.equals("flux_compressor") || name.equals("electrostatic_compressor") ? Set.of("handle", "body", "rotor") : Set.of("handle", "body"))
            : rotor && name.equals("turbine_rotor") ? Set.of("handle", "body", "blade_south", "blade_east")
            : rotor ? Set.of("handle", "body") : Set.of("body")))
            throw new IllegalArgumentException("Wrong pressure part roles");
        var labels = new LinkedHashMap<String, String>();
        double bodyRate = rating(inputs.get("body"), rotor ? "tconstruct:small_blade" : "tconstruct:large_plate",
            rotor ? "airflow_rate" : compressor ? "compressed_air_rate" : "pressure_body", labels, "body");
        double pressureBody = compressor ? rating(inputs.get("body"), "tconstruct:large_plate", "pressure_body", labels, "body") : bodyRate;
        double handleRate = (compressor || rotor) ? rating(inputs.get("handle"), "tconstruct:tough_handle",
            "compressor_handle", labels, "handle") : 1;
        if (compressor) {
            double rate = Math.sqrt(bodyRate * handleRate);
            if (inputs.containsKey("rotor")) {
                var rotorStack = inputs.get("rotor");
                var rotorId = ForgeRegistries.ITEMS.getKey(rotorStack.getItem());
                if (rotorId == null || !rotorId.toString().equals("pneumaticcraft:turbine_rotor"))
                    throw new IllegalArgumentException("Wrong compressor rotor");
                Double rotation = rotationRate(rotorStack, rotorId.toString());
                if (rotation != null) rate = Math.cbrt(bodyRate * handleRate * rotation);
            }
            rate = Math.max(.75, Math.min(1.5, rate));
            if (name.equals("manual_compressor")) {
                int interval = Math.max(3, Math.min(5, (int) Math.round(4 / rate)));
                rate = 4.0 / interval; // Display the actual whole-tick manual cooldown ratio.
            }
            return new MachineSettings(output, Map.of("compression_rate", rate,
                "pressure_envelope", Math.min(1, pressureBody)), labels, null);
        }
        if (body) return new MachineSettings(output, Map.of("pressure_envelope", Math.min(1, bodyRate)), labels, null);
        double rotation = Math.sqrt(bodyRate * handleRate);
        if (name.equals("turbine_rotor")) {
            double product = bodyRate * handleRate;
            for (String role : Set.of("blade_south", "blade_east")) {
                var stack = inputs.get(role);
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null || !id.toString().equals("pneumaticcraft:turbine_blade"))
                    throw new IllegalArgumentException("Missing native turbine blade");
                Double blade = rotationRate(stack, id.toString());
                product *= blade == null ? 1 : blade;
            }
            rotation = Math.sqrt(Math.sqrt(product));
        }
        return new MachineSettings(output, Map.of("rotation_rate", Math.max(.75, Math.min(1.5, rotation))), labels, null);
    }

    /** Only an exact, bounded saved rotation setting may influence a later consumed rotor. */
    private static Double rotationRate(ItemStack stack, String output) {
        var settings = SettingsNbt.read(stack.getTag(), output);
        if (settings == null || !settings.properties().keySet().equals(Set.of("rotation_rate"))) return null;
        double rate = settings.properties().get("rotation_rate");
        return Double.isFinite(rate) && rate >= .75 && rate <= 1.5 ? rate : null;
    }

    private static double rating(ItemStack stack, String shape, String trait, Map<String, String> labels, String role) {
        if (stack == null || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Missing Tinkers part");
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.toString().equals(shape)) throw new IllegalArgumentException("Wrong part shape");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported part material");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        var policy = catalog.policies().get(material.toString());
        if (policy == null) policy = catalog.policies().get(material.getId().toString());
        var iron = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || policy == null || iron == null || !policy.supportedParts().contains(shape))
            throw new IllegalArgumentException("No part engineering rating");
        double value = policy.traits().getOrDefault(trait, 0.0), reference = iron.traits().getOrDefault(trait, 0.0);
        if (!Double.isFinite(value) || value <= 0 || reference <= 0) throw new IllegalArgumentException("No working-part rating");
        labels.put(role, material.toString());
        return Math.max(.75, Math.min(1.5, value / reference));
    }
}
