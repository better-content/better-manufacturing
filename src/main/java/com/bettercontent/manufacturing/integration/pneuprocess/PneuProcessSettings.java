package com.bettercontent.manufacturing.integration.pneuprocess;

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

/** Chemistry and thermal ratings are frozen at assembly. No live material lookup or containment policy. */
public final class PneuProcessSettings {
    private static final Set<String> CHEMISTRY = Set.of("pneumaticcraft:etching_tank", "pneumaticcraft:fluid_mixer",
            "pneumaticcraft:refinery", "pneumaticcraft:refinery_output", "pneumaticcraft:thermopneumatic_processing_plant");
    private static final Set<String> THERMAL = Set.of("pneumaticcraft:heat_pipe", "pneumaticcraft:heat_sink",
            "pneumaticcraft:vacuum_pump", "pneumaticcraft:vacuum_trap", "pneumaticcraft:vortex_tube");
    private PneuProcessSettings() {}
    public static boolean supports(String output) { return CHEMISTRY.contains(output) || THERMAL.contains(output); }

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        boolean chemistry = function.equals("better_manufacturing:pneuprocess/chemistry") && CHEMISTRY.contains(output);
        boolean thermal = function.equals("better_manufacturing:pneuprocess/vacuum_thermal") && THERMAL.contains(output);
        if (!chemistry && !thermal) throw new IllegalArgumentException("Unsupported PneumaticCraft process machine");
        Set<String> required = output.equals("pneumaticcraft:fluid_mixer") ? Set.of("liner", "rotor")
                : output.equals("pneumaticcraft:vacuum_pump") ? Set.of("plate", "handle", "rotor_a", "rotor_b")
                : chemistry ? Set.of("liner") : Set.of("plate", "handle");
        if (!inputs.keySet().equals(required) || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Wrong process part bindings or material registry unavailable");
        var snapshot = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        if (!snapshot.ready()) throw new IllegalArgumentException("Engineering ratings unavailable");
        var iron = snapshot.policies().get("tconstruct:iron");
        if (iron == null) throw new IllegalArgumentException("Missing iron reference");
        var materials = new LinkedHashMap<String, String>();
        double rate = 1.5;
        double rotorProduct = 1;
        int rotorCount = 0;
        for (var entry : inputs.entrySet()) {
            String role = entry.getKey();
            if (role.startsWith("rotor")) {
                var stack = entry.getValue();
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null || !id.toString().equals("pneumaticcraft:turbine_rotor")) throw new IllegalArgumentException("Wrong rotor component");
                var rotor = SettingsNbt.read(stack.getTag(), "pneumaticcraft:turbine_rotor");
                double rotorRate = rotor == null ? 1 : rotor.properties().getOrDefault("rotation_rate", 1d);
                if (!Double.isFinite(rotorRate) || rotorRate < .75 || rotorRate > 1.5) rotorRate = 1;
                rotorProduct *= rotorRate;
                rotorCount++;
                continue;
            }
            String requiredShape = role.equals("handle") ? "tconstruct:tough_handle" : "tconstruct:large_plate";
            var stack = entry.getValue();
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !id.toString().equals(requiredShape) || !(stack.getItem() instanceof ToolPartItem part))
                throw new IllegalArgumentException("Wrong process part shape");
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported part material");
            String label = material.toString();
            var policy = snapshot.policies().getOrDefault(label, snapshot.policies().get(material.getId().toString()));
            if (policy == null || !policy.supportedParts().contains(requiredShape)) throw new IllegalArgumentException("No material rating");
            String trait = chemistry ? "pneu_process_rate" : "pneu_thermal_transfer";
            double value = policy.traits().getOrDefault(trait, 0d), reference = iron.traits().getOrDefault(trait, 0d);
            if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(reference) || reference <= 0)
                throw new IllegalArgumentException("Missing process rating");
            rate = Math.min(rate, Math.max(.75, Math.min(1.5, value / reference)));
            materials.put(role, label);
        }
        // Rotor is an already-configured component; transfer only its final rating, not its former parts.
        if (rotorCount > 0) rate = Math.sqrt(rate * Math.pow(rotorProduct, 1d / rotorCount));
        // These are performance settings, NOT new restrictions on fluids, heat, pressure or chemistry.
        return new MachineSettings(output, Map.of(chemistry ? "process_rate" : output.equals("pneumaticcraft:vacuum_pump")
                || output.equals("pneumaticcraft:vacuum_trap") ? "transfer_rate" : "thermal_transfer", rate), materials, null);
    }
}
