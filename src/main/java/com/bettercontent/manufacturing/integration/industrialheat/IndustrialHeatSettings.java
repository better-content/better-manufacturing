package com.bettercontent.manufacturing.integration.industrialheat;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;
import java.util.Set;

/** Thermal surface ratings are resolved once at crafting, never during native heat transfer. */
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
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        String materialId = material.toString();
        var policy = catalog.policies().get(materialId);
        if (policy == null) policy = catalog.policies().get(material.getId().toString());
        var reference = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || policy == null || reference == null || !policy.supportedParts().contains(PART))
            throw new IllegalArgumentException("No thermal surface rating");
        double value = policy.traits().getOrDefault("heat_transfer", 0.0);
        double base = reference.traits().getOrDefault("heat_transfer", 0.0);
        if (!Double.isFinite(value) || !Double.isFinite(base) || value <= 0 || base <= 0)
            throw new IllegalArgumentException("No thermal transfer rating");
        double rate = Math.max(.75, Math.min(1.5, value / base));
        // Thermal envelope is a separate role-specific rating; no live material lookup occurs at runtime.
        double envelope = policy.traits().getOrDefault("thermal_envelope", 0.0);
        double referenceEnvelope = reference.traits().getOrDefault("thermal_envelope", 0.0);
        if (!Double.isFinite(envelope) || !Double.isFinite(referenceEnvelope) || envelope <= 0 || referenceEnvelope <= 0)
            throw new IllegalArgumentException("No thermal envelope rating");
        double boundedEnvelope = Math.max(.75, Math.min(1.5, envelope / referenceEnvelope));
        return new MachineSettings(output, Map.of("heat_transfer", rate, "thermal_envelope", boundedEnvelope),
            Map.of("thermal_surface", materialId), null);
    }
}
