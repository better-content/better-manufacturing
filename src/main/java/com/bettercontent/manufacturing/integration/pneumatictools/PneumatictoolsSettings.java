package com.bettercontent.manufacturing.integration.pneumatictools;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

import java.util.Map;
import java.util.Set;

/** Native pick-head cutting speed and tier are sampled once during crafting. */
public final class PneumatictoolsSettings {
    public static final String FUNCTION = "better_manufacturing:pneumaticcraft/drilling";
    private static final String CYLINDER = "pneumaticcraft:pneumatic_cylinder";
    private static final Set<String> CYLINDER_ROLES = Set.of("cylinder_a", "cylinder_b", "cylinder_c");
    private static final Set<String> OUTPUTS = Set.of(
            "pneumaticcraft:assembly_drill", "pneumaticcraft:jackhammer",
            "pneumaticcraft:drill_bit_iron", "pneumaticcraft:drill_bit_compressed_iron",
            "pneumaticcraft:drill_bit_diamond", "pneumaticcraft:drill_bit_netherite");

    private PneumatictoolsSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !OUTPUTS.contains(output) ||
                !inputs.keySet().equals(output.equals("pneumaticcraft:assembly_drill")
                        ? Set.of("head", "cylinder_a", "cylinder_b", "cylinder_c") : Set.of("head")) ||
                !MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Unsupported pneumatic drilling part");
        ItemStack head = inputs.get("head");
        if (head == null || head.isEmpty() || head.getCount() != 1
                || !(head.getItem() instanceof ToolPartItem part)
                || !"tconstruct:pick_head".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(head.getItem()))))
            throw new IllegalArgumentException("Expected one real Tinkers pick head");
        var material = part.getMaterial(head);
        if (material == null || !part.canUseMaterial(material.getId()))
            throw new IllegalArgumentException("Unsupported pick-head material");
        var registry = MaterialRegistry.getInstance();
        var stats = registry.<HeadMaterialStats>getMaterialStats(material.getId(), HeadMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("Missing native cutting stats"));
        var reference = registry.<HeadMaterialStats>getMaterialStats(new MaterialId("tconstruct:iron"), HeadMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("Missing reference cutting stats"));
        float speed = stats.miningSpeed(), iron = reference.miningSpeed();
        if (!Float.isFinite(speed) || speed <= 0 || !Float.isFinite(iron) || iron <= 0)
            throw new IllegalArgumentException("Invalid native cutting speed");
        var tier = TierSortingRegistry.getName(stats.tier());
        if (tier == null) throw new IllegalArgumentException("Unknown native harvest tier");
        double rate = Math.max(.75, Math.min(1.5, speed / iron));
        if (output.equals("pneumaticcraft:assembly_drill")) {
            // Each cylinder has already consumed its Tinkers blade/handle. The assembly drill
            // uses only the completed cylinder's baked rating and consumes the item normally.
            // Native/unconfigured cylinder is reference 1; never stack multiple bonuses.
            for (String role : CYLINDER_ROLES) {
                ItemStack cylinder = inputs.get(role);
                if (cylinder == null || cylinder.isEmpty() ||
                        !CYLINDER.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(cylinder.getItem()))))
                    throw new IllegalArgumentException("Expected native pneumatic cylinder");
                var configured = SettingsNbt.read(cylinder.getTag(), CYLINDER);
                double rotation = configured == null ? 1 : configured.properties().getOrDefault("rotation_rate", 1.0);
                if (rotation < .75 || rotation > 1.5) rotation = 1;
                rate = Math.min(rate, rotation);
            }
        }
        return new MachineSettings(output, Map.of("work_rate", rate), Map.of("head", material.toString()), tier.toString());
    }
}
