package com.bettercontent.manufacturing.integration.industrialheat;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;
import java.util.Set;

/** Approximate bounded thermal response from a consumed native TCon plate at craft time. */
public final class IndustrialHeatSettings {
    public static final String FUNCTION = "better_manufacturing:industrialheat/thermal";
    private static final Set<String> OUTPUTS = Set.of(
        "better_industrial_heat:boiler_heater", "better_industrial_heat:coolant_exchanger",
        "better_industrial_heat:heat_pipe", "better_industrial_heat:thermal_firebox");
    private static final String PART = "tconstruct:large_plate";
    private IndustrialHeatSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !OUTPUTS.contains(output) || !inputs.keySet().equals(Set.of("thermal_surface")))
            throw new IllegalArgumentException("Unsupported thermal build");
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material data not loaded");
        ItemStack stack = inputs.get("thermal_surface");
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)
            || !PART.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Thermal surface requires a large plate");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported plate material");
        // These native tool stats are APPROXIMATE work/structural proxies, not
        // measured conductivity or a literal maximum temperature. IH still owns
        // the source debit, heat conservation, 80-unit ceiling and receiver limits.
        double work = NativePartRates.relative(part, stack, NativePartRates.Measure.HEAD_MINING_SPEED);
        double envelope = NativePartRates.relative(part, stack, NativePartRates.Measure.HEAD_DURABILITY);
        return new MachineSettings(output, Map.of("heat_transfer", work, "thermal_envelope", envelope),
            Map.of("thermal_surface", material.toString()), null);
    }
}
