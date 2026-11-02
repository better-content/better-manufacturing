package com.bettercontent.manufacturing.integration.eureka;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;

/** Eureka's drive assembly is consumed at crafting; only its bounded drive/work ratio survives. */
public final class EurekaEngineSettings {
    public static final String OUTPUT = "vs_eureka:engine";
    public static final String FUNCTION = "better_manufacturing:eureka/engine";
    public static final String ROLE = "working_assembly";
    private static final String PART = "tconstruct:tough_handle";
    private static final String TRAIT = "eureka_drive_rate";

    private EurekaEngineSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!OUTPUT.equals(output) || !FUNCTION.equals(function) || !inputs.keySet().equals(java.util.Set.of(ROLE))
            || !MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Unsupported Eureka engine assembly");
        ItemStack stack = inputs.get(ROLE);
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)
            || !PART.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Expected a Tinkers tough handle");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported handle material");
        String materialId = material.toString();
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        var policy = catalog.policies().getOrDefault(materialId, catalog.policies().get(material.getId().toString()));
        var iron = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || policy == null || iron == null || !policy.supportedParts().contains(PART))
            throw new IllegalArgumentException("No native drive assembly rating");
        double rating = policy.traits().getOrDefault(TRAIT, 0d);
        double reference = iron.traits().getOrDefault(TRAIT, 0d);
        if (!Double.isFinite(rating) || rating <= 0 || !Double.isFinite(reference) || reference <= 0)
            throw new IllegalArgumentException("No drive assembly rating");
        double rate = Math.max(.75, Math.min(1.5, rating / reference));
        // Same factor on ship force and native consumed heat: higher thrust pays proportionate work.
        return new MachineSettings(output, Map.of("drive_performance", rate, "consumption", rate), Map.of(ROLE, materialId), null);
    }

    /** A malformed or unrelated record never changes Eureka's baseline behavior. */
    public static boolean valid(MachineSettings settings) {
        if (settings == null || !OUTPUT.equals(settings.output()) || settings.harvestTier() != null
                || !settings.properties().keySet().equals(java.util.Set.of("drive_performance", "consumption"))
                || !settings.materials().keySet().equals(java.util.Set.of(ROLE))) return false;
        double drive = settings.properties().get("drive_performance");
        double consumed = settings.properties().get("consumption");
        return Double.isFinite(drive) && drive >= .75 && drive <= 1.5 && Double.compare(drive, consumed) == 0;
    }

    public static double rate(MachineSettings settings) {
        return valid(settings) ? settings.properties().get("drive_performance") : 1;
    }
}
