package com.bettercontent.manufacturing.tconstruct;

import com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Stat;
import com.bettercontent.manufacturing.api.MaterialEngineeringProfile.Semantics;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed native values only. No pressure, chemistry, insulation or universal tier inference. */
public final class TinkersStats {
    private TinkersStats() {}
    public static Map<Stat, Double> read(MaterialVariantId material) {
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalStateException("Tinkers material data not ready");
        var result = new LinkedHashMap<Stat, Double>();
        var registry = MaterialRegistry.getInstance();
        registry.<HeadMaterialStats>getMaterialStats(material.getId(), HeadMaterialStats.ID).ifPresent(head -> {
            result.put(new Stat(HeadMaterialStats.ID.toString(), "mining_speed", Semantics.BASE), (double) head.miningSpeed());
            result.put(new Stat(HeadMaterialStats.ID.toString(), "durability", Semantics.BASE), (double) head.durability());
            result.put(new Stat(HeadMaterialStats.ID.toString(), "attack", Semantics.BASE), (double) head.attack());
        });
        registry.<HandleMaterialStats>getMaterialStats(material.getId(), HandleMaterialStats.ID).ifPresent(handle -> {
            result.put(new Stat(HandleMaterialStats.ID.toString(), "mining_speed", Semantics.ADDITIVE_MODIFIER), (double) handle.miningSpeed());
            result.put(new Stat(HandleMaterialStats.ID.toString(), "durability", Semantics.ADDITIVE_MODIFIER), (double) handle.durability());
            result.put(new Stat(HandleMaterialStats.ID.toString(), "attack_speed", Semantics.ADDITIVE_MODIFIER), (double) handle.meleeSpeed());
            result.put(new Stat(HandleMaterialStats.ID.toString(), "attack_damage", Semantics.ADDITIVE_MODIFIER), (double) handle.attackDamage());
        });
        return Map.copyOf(result);
    }
}
