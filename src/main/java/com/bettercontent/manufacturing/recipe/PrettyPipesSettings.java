package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;
import java.util.Set;

/** Pretty Pipes settings are derived once from the working handle during crafting. */
public final class PrettyPipesSettings {
    private PrettyPipesSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String trait;
        String partId;
        String role;
        if (function.equals("better_manufacturing:prettypipes/extraction") && output.matches("prettypipes:(low|medium|high)_extraction_module")) {
            trait = "extraction_rate"; partId = "tconstruct:tool_handle"; role = "actuator";
        } else if (function.equals("better_manufacturing:prettypipes/speed") && output.matches("prettypipes:(low|medium|high)_speed_module")) {
            trait = "speed_rate"; partId = "tconstruct:tool_handle"; role = "actuator";
        } else if (function.equals("better_manufacturing:prettypipes/pressurizer") && output.equals("prettypipes:pressurizer")) {
            trait = "pressurizer_rate"; partId = "tconstruct:tough_handle"; role = "working_assembly";
        } else throw new IllegalArgumentException("Unsupported Pretty Pipes variant");
        if (!MaterialRegistry.isFullyLoaded() || !inputs.keySet().equals(Set.of(role)))
            throw new IllegalArgumentException("Missing working handle or material data");
        ItemStack stack = inputs.get(role);
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)
            || !partId.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Wrong working handle");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unusable handle material");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        String materialId = material.toString();
        var policy = catalog.policies().get(materialId);
        if (policy == null) policy = catalog.policies().get(material.getId().toString());
        var iron = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || policy == null || iron == null || !policy.supportedParts().contains(partId))
            throw new IllegalArgumentException("Missing family handle rating");
        double value = policy.traits().getOrDefault(trait, 0.0);
        double reference = iron.traits().getOrDefault(trait, 0.0);
        if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(reference) || reference <= 0)
            throw new IllegalArgumentException("Invalid family handle rating");
        double rate = Math.max(.75, Math.min(1.5, value / reference));
        Map<String, Double> properties;
        switch (trait) {
            case "extraction_rate" -> {
                // Keep the native low/medium/high 20/15/10-tick and 1/8/64-item tiers.
                // The saved handle ratio modifies that tier at use time, never replaces it
                // with a universal interval or stack limit.
                properties = Map.of("extraction_rate", rate);
            }
            case "speed_rate" -> properties = Map.of("speed_rate", rate);
            case "pressurizer_rate" -> properties = Map.of("pressurizer_rate", rate);
            default -> throw new IllegalArgumentException("Unsupported family rating");
        }
        return new MachineSettings(output, properties, Map.of(role, materialId), null);
    }
}
