package com.bettercontent.manufacturing.integration.littlelogistics;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;
import java.util.Set;

/** Only the native-role material rating is sampled, at crafting. The part is consumed. */
public final class LittleLogisticsSettings {
    private static final Set<String> PROPULSION = Set.of("littlelogistics:energy_locomotive", "littlelogistics:steam_locomotive", "littlelogistics:energy_tug", "littlelogistics:tug");
    private LittleLogisticsSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        boolean drive = function.equals("better_manufacturing:littlelogistics/propulsion") && PROPULSION.contains(output);
        boolean transfer = function.equals("better_manufacturing:littlelogistics/transfer") && output.equals("littlelogistics:rapid_hopper");
        if (!drive && !transfer) throw new IllegalArgumentException("Unsupported Little Logistics machine");
        String role = drive ? "drive" : "actuator";
        if (!inputs.keySet().equals(Set.of(role)) || !MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Missing working part");
        ItemStack stack = inputs.get(role);
        String partId = drive ? "tconstruct:tough_handle" : "tconstruct:tool_handle";
        if (stack == null || stack.isEmpty() || !partId.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())))
            || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Wrong handle shape");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Invalid handle material");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        String materialId = material.toString();
        var policy = catalog.policies().getOrDefault(materialId, catalog.policies().get(material.getId().toString()));
        var reference = catalog.policies().get("tconstruct:iron");
        String trait = drive ? "drive_rate" : "transfer_rate";
        if (!catalog.ready() || policy == null || reference == null || !policy.supportedParts().contains(partId))
            throw new IllegalArgumentException("No compatible handle rating");
        double value = policy.traits().getOrDefault(trait, 0.0), baseline = reference.traits().getOrDefault(trait, 0.0);
        if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(baseline) || baseline <= 0)
            throw new IllegalArgumentException("Invalid handle rating");
        double rate = Math.max(.75, Math.min(1.5, value / baseline));
        return new MachineSettings(output, Map.of(drive ? "drive_rate" : "transfer_rate", rate), Map.of(role, materialId), null);
    }
}
