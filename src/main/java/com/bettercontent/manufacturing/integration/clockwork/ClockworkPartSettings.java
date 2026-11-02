package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
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
            double nativeRate = NativePartRates.relative(part, stack, role.equals("plate") || role.equals("blade")
                ? NativePartRates.Measure.HEAD_MINING_SPEED : NativePartRates.Measure.HANDLE_MINING_SPEED);
            if (role.equals("plate") || role.equals("blade")) first = nativeRate; else second = nativeRate;
        }
        // Approximate gas-path/flight working response from native head speed
        // and handle actuation speed, NOT measured gas flow or pressure. Native
        // fuel, pressure, kinetic-work and transfer consumers remain authoritative.
        double rate = workingRate(first, second);
        return new MachineSettings(output,
            Map.of(gas ? "gas_transfer" : "actuator_response", rate), labels, null);
    }

    /** Bounded approximate response from the physical working face and drive handle. */
    public static double workingRate(double face, double handle) {
        if (!Double.isFinite(face) || !Double.isFinite(handle)) throw new IllegalArgumentException("Nonfinite native work rate");
        return Math.max(.75, Math.min(1.5, .65 * face + .35 * handle));
    }
}
