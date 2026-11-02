package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Calculates Clockwork working assemblies at crafting only. Native gas/vehicle simulation remains the owner. */
public final class ClockworkPartSettings {
    public static final String GAS = "better_manufacturing:clockwork/gas_thermal";
    public static final String FLIGHT = "better_manufacturing:clockwork/flight_actuators";
    public static final String REACTIONWHEEL_PREP = "better_manufacturing:clockwork/reactionwheel_prep";
    private static final Set<String> GAS_OUTPUTS = Set.of(
        "vs_clockwork:air_compressor", "vs_clockwork:coal_burner",
        "vs_clockwork:duct", "vs_clockwork:exhaust", "vs_clockwork:gas_engine",
        "vs_clockwork:gas_nozzle", "vs_clockwork:gas_thruster", "vs_clockwork:pump_duct",
        "vs_clockwork:valve_duct");
    private static final Set<String> FLIGHT_OUTPUTS = Set.of(
        "vs_clockwork:andesite_flap_bearing", "vs_clockwork:brass_propeller_bearing", "vs_clockwork:gyro",
        "vs_clockwork:juryrigged_propeller_bearing", "vs_clockwork:propeller_blade",
        "vs_clockwork:reactionwheel", "vs_clockwork:smart_flap_bearing", "vs_clockwork:wide_propeller_blade");
    private ClockworkPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        boolean gas = function.equals(GAS), prep = function.equals(REACTIONWHEEL_PREP);
        if (prep) {
            if (!output.equals("create:flywheel") || !inputs.keySet().equals(Set.of("blank", "blade", "handle")))
                throw new IllegalArgumentException("Reaction wheel prep requires the native flywheel and both working parts");
            ItemStack blank = inputs.get("blank");
            if (blank == null || blank.isEmpty() || !"create:flywheel".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(blank.getItem())))
                || blank.hasTag() && blank.getTag().contains(SettingsNbt.KEY))
                throw new IllegalArgumentException("Already configured or non-flywheel precursor");
        } else {
            if (!(gas ? GAS_OUTPUTS : FLIGHT_OUTPUTS).contains(output) || (!gas && !function.equals(FLIGHT)))
                throw new IllegalArgumentException("Wrong Clockwork function/output");
            if (!inputs.keySet().equals(gas ? Set.of("plate", "handle") : Set.of("blade", "handle")))
                throw new IllegalArgumentException("Clockwork requires its working plate/blade and handle");
        }
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Tinkers materials not loaded");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        if (!catalog.ready()) throw new IllegalArgumentException("Engineering ratings not loaded");
        var reference = catalog.policies().get("tconstruct:iron");
        if (reference == null) throw new IllegalArgumentException("Missing iron reference");
        var labels = new LinkedHashMap<String, String>();
        double first = 0, second = 0;
        for (String role : gas ? new String[]{"plate", "handle"} : new String[]{"blade", "handle"}) {
            ItemStack stack = inputs.get(role);
            String shape = role.equals("plate") ? "tconstruct:large_plate" : role.equals("blade") ? "tconstruct:broad_blade" : "tconstruct:tough_handle";
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)
                || !shape.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
                throw new IllegalArgumentException("Wrong Clockwork part: " + role);
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported part material");
            var materialId = material.toString(); labels.put(role, materialId);
            var policy = catalog.policies().getOrDefault(materialId, catalog.policies().get(material.getId().toString()));
            if (policy == null || !policy.supportedParts().contains(shape)) throw new IllegalArgumentException("Unrated part");
            String rating = gas ? role.equals("plate") ? "gas_flow" : "gas_rotor" : role.equals("blade") ? "blade_response" : "actuator_drive";
            double value = policy.traits().getOrDefault(rating, 0.0);
            double base = reference.traits().getOrDefault(rating, 0.0);
            if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(base) || base <= 0)
                throw new IllegalArgumentException("Missing role-specific rating: " + rating);
            double ratio = value / base;
            if (role.equals("plate") || role.equals("blade")) first = ratio; else second = ratio;
        }
        // Separate native roles: fluid path/blade area dominates, drive material contributes the rest.
        double rate = Math.max(.75, Math.min(1.5, .65 * first + .35 * second));
        return new MachineSettings(output,
            Map.of(gas ? "gas_transfer" : "actuator_response", rate), labels, null);
    }
}
