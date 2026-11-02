package com.bettercontent.manufacturing.integration.createactors;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

import java.util.Map;

/** Role-specific material ratings evaluated once when the actor is crafted. */
public final class CreateActorsPartSettings {
    private CreateActorsPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String required = switch (output) {
            case "create:mechanical_plough" -> "tconstruct:small_axe_head";
            case "create:mechanical_roller" -> "tconstruct:large_plate";
            default -> throw new IllegalArgumentException("Unsupported Create actor");
        };
        String requiredFunction = output.equals("create:mechanical_roller")
            ? "better_manufacturing:create/rolling" : "better_manufacturing:create/field_work";
        if (!requiredFunction.equals(function) || !inputs.keySet().equals(java.util.Set.of("working_surface"))
            || !MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Invalid Create actor parts");
        ItemStack stack = inputs.get("working_surface");
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)
            || !required.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Wrong actor working surface");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported part material");
        String materialId = material.toString();
        double rate;
        if (output.equals("create:mechanical_roller")) {
            var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
            var policy = catalog.policies().get(materialId);
            if (policy == null) policy = catalog.policies().get(material.getId().toString());
            var reference = catalog.policies().get("tconstruct:iron");
            if (!catalog.ready() || policy == null || reference == null || !policy.supportedParts().contains(required))
                throw new IllegalArgumentException("No roller material rating");
            double value = policy.traits().getOrDefault("rolling_rate", 0.0);
            double base = reference.traits().getOrDefault("rolling_rate", 0.0);
            if (!Double.isFinite(value) || !Double.isFinite(base) || value <= 0 || base <= 0)
                throw new IllegalArgumentException("Invalid roller material rating");
            rate = value / base;
        } else {
            var registry = MaterialRegistry.getInstance();
            var stats = registry.<HeadMaterialStats>getMaterialStats(material.getId(), HeadMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("No native head stats"));
            var iron = registry.<HeadMaterialStats>getMaterialStats(new MaterialId("tconstruct:iron"), HeadMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("No reference head stats"));
            if (!Float.isFinite(stats.miningSpeed()) || stats.miningSpeed() <= 0
                || !Float.isFinite(iron.miningSpeed()) || iron.miningSpeed() <= 0)
                throw new IllegalArgumentException("Invalid native head speed");
            rate = stats.miningSpeed() / iron.miningSpeed();
        }
        if (!Double.isFinite(rate)) throw new IllegalArgumentException("Nonfinite actor rate");
        return new MachineSettings(output, Map.of("work_rate", Math.max(.75, Math.min(1.5, rate))),
            Map.of("working_surface", materialId), null);
    }
}
