package com.bettercontent.manufacturing.integration.diesel;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Native Tinkers part stats are frozen at crafting; engines never consult a material-rating table. */
public final class DieselSettings {
    public static final String FUNCTION = "better_manufacturing:createdieselgenerators/engines";
    public static final String PISTON = "createdieselgenerators:engine_piston";
    public static final String TURBO = "createdieselgenerators:engine_turbocharger";
    private static final Set<String> OUTPUTS = Set.of(PISTON, TURBO,
        "createdieselgenerators:diesel_engine", "createdieselgenerators:large_diesel_engine",
        "createdieselgenerators:huge_diesel_engine");
    private DieselSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !OUTPUTS.contains(output) || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Unsupported diesel part variant");
        boolean piston = output.equals(PISTON), turbo = output.equals(TURBO);
        if (!inputs.keySet().containsAll(Set.of("rod", "chamber")) || inputs.size() != (piston || output.endsWith("huge_diesel_engine") ? 2 : turbo || output.endsWith("large_diesel_engine") ? 3 : 4))
            throw new IllegalArgumentException("Missing engine working parts");
        if (!piston && !turbo && !output.endsWith("huge_diesel_engine") && !output.endsWith("large_diesel_engine")
            && !inputs.keySet().equals(Set.of("rod", "chamber", "piston_left", "piston_right")))
            throw new IllegalArgumentException("Diesel engine requires two configured pistons");
        if (output.endsWith("large_diesel_engine") && !inputs.keySet().equals(Set.of("rod", "chamber", "engine")))
            throw new IllegalArgumentException("Large engine requires its engine core");
        if ((piston || output.endsWith("huge_diesel_engine")) && !inputs.keySet().equals(Set.of("rod", "chamber")))
            throw new IllegalArgumentException("Wrong engine parts");
        if (turbo && !inputs.keySet().equals(Set.of("rod", "chamber", "impeller")))
            throw new IllegalArgumentException("Turbocharger requires its configured propeller");

        var labels = new LinkedHashMap<String, String>();
        double rodConversion = 0, chamberConversion = 0, rodLoad = 0, chamberLoad = 0;
        double conversion, load, thermal;
        double childConversion = 0, childLoad = 0, childThermal = 0;
        int children = 0;
        double impeller = 1;
        for (var entry : inputs.entrySet()) {
            String role = entry.getKey();
            ItemStack stack = entry.getValue();
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            if (role.equals("rod") || role.equals("chamber")) {
                String required = role.equals("rod") ? "tconstruct:tough_handle" : "tconstruct:large_plate";
                if (!id.equals(required) || !(stack.getItem() instanceof ToolPartItem part))
                    throw new IllegalArgumentException("Wrong engine part for " + role);
                var material = part.getMaterial(stack);
                if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Invalid engine part material");
                String materialId = material.toString();
                labels.put(role, materialId);
                double nativeConversion = NativePartRates.relative(part, stack, role.equals("rod")
                    ? NativePartRates.Measure.HANDLE_MINING_SPEED : NativePartRates.Measure.HEAD_MINING_SPEED);
                double nativeLoad = NativePartRates.relative(part, stack, role.equals("rod")
                    ? NativePartRates.Measure.HANDLE_DURABILITY : NativePartRates.Measure.HEAD_DURABILITY);
                if (role.equals("rod")) { rodConversion = nativeConversion; rodLoad = nativeLoad; }
                else { chamberConversion = nativeConversion; chamberLoad = nativeLoad; }
            } else if (role.equals("impeller")) {
                if (!turbo || !id.equals("create:propeller")) throw new IllegalArgumentException("Wrong turbo impeller");
                MachineSettings propeller = configured(stack, "create:propeller", "airflow");
                impeller = value(propeller, "airflow");
                labels.put(role, "create:propeller");
            } else {
                String required = role.startsWith("piston_") ? PISTON : "createdieselgenerators:diesel_engine";
                if (!id.equals(required)) throw new IllegalArgumentException("Wrong configured engine subassembly");
                MachineSettings child = configured(stack, required, "conversion", "load", "thermal_envelope");
                // A vanilla/native subassembly is reference-grade, not an empty or unbounded value.
                childConversion += value(child, "conversion");
                childLoad += value(child, "load");
                childThermal += value(child, "thermal_envelope");
                children++;
                labels.put(role, child == null ? required : "configured " + required);
            }
        }
        conversion = mechanicalRate(rodConversion, chamberConversion);
        load = mechanicalRate(rodLoad, chamberLoad);
        // Approximate the bounded thermal envelope from native structural
        // durability, not a second material rating or a source of free heat.
        thermal = load;
        if (children > 0) {
            conversion = .5 * (conversion + childConversion / children);
            load = .5 * (load + childLoad / children);
            thermal = .5 * (thermal + childThermal / children);
        }
        // A configured propeller supplies its baked airflow scalar, not its original blade graph.
        if (turbo) load = .75 * load + .25 * impeller;
        return new MachineSettings(output, Map.of("conversion", bound(conversion), "load", bound(load),
            "thermal_envelope", bound(thermal)), labels, null);
    }

    /** A stock subassembly is baseline; a present but malformed/incomplete record cannot mint a rated craft. */
    private static MachineSettings configured(ItemStack stack, String output, String... properties) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(SettingsNbt.KEY)) return null;
        MachineSettings settings = SettingsNbt.read(tag, output);
        if (settings == null) throw new IllegalArgumentException("Malformed configured engine component: " + output);
        for (String key : properties) {
            Double value = settings.properties().get(key);
            if (value == null || !Double.isFinite(value) || value < .75 || value > 1.5)
                throw new IllegalArgumentException("Invalid configured engine component property: " + key);
        }
        return settings;
    }

    /** Same physical shaft/chamber weighting for native conversion and structural load. */
    public static double mechanicalRate(double shaft, double chamber) {
        return bound(.45 * shaft + .55 * chamber);
    }
    private static double bound(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite engine rating");
        return Math.max(.75, Math.min(1.5, value));
    }
    public static double value(MachineSettings settings, String key) {
        if (settings == null) return 1;
        double value = settings.properties().getOrDefault(key, 1.0);
        return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1;
    }
}
