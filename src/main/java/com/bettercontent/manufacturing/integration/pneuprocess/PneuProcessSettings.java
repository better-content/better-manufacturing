package com.bettercontent.manufacturing.integration.pneuprocess;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
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
            materials.put(role, material.toString());
            // Approximate operating throughput from native tool stats: plate
            // working speed, moving pump-handle speed, or structural support
            // durability. This is not a fluid/temperature admission rule.
            Measure measure = role.equals("handle")
                ? output.equals("pneumaticcraft:vacuum_pump") ? Measure.HANDLE_MINING_SPEED : Measure.HANDLE_DURABILITY
                : Measure.HEAD_MINING_SPEED;
            rate = Math.min(rate, NativePartRates.relative(part, stack, measure));
        }
        // Rotor is an already-configured component; transfer only its final rating, not its former parts.
        rate = motionRate(rate, rotorProduct, rotorCount);
        // Keep original saved property keys and native operating/cost limits;
        // these bounded stat proxies are not measured chemistry or heat safety.
        return new MachineSettings(output, Map.of(chemistry ? "process_rate" : output.equals("pneumaticcraft:vacuum_pump")
                || output.equals("pneumaticcraft:vacuum_trap") ? "transfer_rate" : "thermal_transfer", rate), materials, null);
    }

    /** Already-baked native rotor motion can contribute without rereading its consumed parts. */
    public static double motionRate(double handleRate, double rotorProduct, int rotorCount) {
        if (!Double.isFinite(handleRate) || handleRate < .75 || handleRate > 1.5
            || rotorCount < 0 || rotorCount > 2 || !Double.isFinite(rotorProduct) || rotorProduct <= 0)
            throw new IllegalArgumentException("Invalid moving component rate");
        return rotorCount == 0 ? handleRate : Math.sqrt(handleRate * Math.pow(rotorProduct, 1d / rotorCount));
    }
}
