package com.bettercontent.manufacturing.integration.create;

import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.tconstruct.TinkersStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** One role, native cutter stats, live iron reference, no new yield or kinetic energy source. */
public final class DrillEngineering {
    public static final String CHASSIS = "create:mechanical_drill", FUNCTION = "better_manufacturing:create/cutting";
    private static final String PROFILE = "better_manufacturing:create/drill";
    private static final MaterialEngineeringProfile.Stat SPEED = new MaterialEngineeringProfile.Stat(
        "tconstruct:head", "mining_speed", MaterialEngineeringProfile.Semantics.BASE);
    private static final ProfileCache<State> CACHE = new ProfileCache<>(256);
    private static final AtomicLong EPOCH = new AtomicLong();
    public record State(double rate, Tier tier, MachineBuild build, String blockedReason) {
        public boolean allowed() { return blockedReason.isEmpty(); }
        public boolean canCut(BlockState target) { return allowed() && (tier == null || TierSortingRegistry.isCorrectTierForDrops(tier, target)); }
    }
    private DrillEngineering() {}
    /** Reviewed drill item surface only; shared by retooling and schematic consumption. */
    public static boolean safeItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.hasTag()) {
            if (!Set.of(BuildNbt.KEY, "display").containsAll(stack.getTag().getAllKeys())) return false;
            if (stack.getTag().contains("display")) {
                if (!stack.getTag().contains("display", net.minecraft.nbt.Tag.TAG_COMPOUND)) return false;
                try { com.bettercontent.manufacturing.persistence.BoundedNbt.check(stack.getTag().getCompound("display")); }
                catch (IllegalArgumentException unreviewed) { return false; }
            }
        }
        var serialized = stack.serializeNBT();
        return !serialized.contains("ForgeCaps") || (serialized.contains("ForgeCaps", net.minecraft.nbt.Tag.TAG_COMPOUND) && serialized.getCompound("ForgeCaps").isEmpty());
    }
    public static void invalidate() { EPOCH.incrementAndGet(); CACHE.clear(); }
    public static long epoch() { return EPOCH.get(); }
    public static State evaluate(CompoundTag holder) {
        var parsed = BuildNbt.read(holder, CHASSIS, FUNCTION);
        if (!parsed.valid()) return new State(0, null, null, parsed.error());
        if (!MaterialRegistry.isFullyLoaded()) return new State(0, null, parsed.build(), "material_data_not_ready");
        return CACHE.resolve(parsed.build(), PROFILE, epoch(), () -> compute(parsed.build()));
    }
    private static State compute(MachineBuild build) {
        if (!build.hasProvenance()) return new State(1, null, build, "");
        var registry = MaterialRegistry.getInstance();
        var reference = registry.<HeadMaterialStats>getMaterialStats(new MaterialId("tconstruct:iron"), HeadMaterialStats.ID);
        if (reference.isEmpty() || reference.get().miningSpeed() <= 0) return new State(0, null, build, "missing_iron_reference");
        var profile = new MachineProfile(PROFILE, CHASSIS, FUNCTION,
            Map.of("head", new MachineProfile.Role(Set.of("tconstruct:pick_head"), "tconstruct:head")),
            List.of(new MachineProfile.Axis("cutting_rate", "head", SPEED, null, reference.get().miningSpeed(), 1, .75, 1.5)),
            Map.of("cutting_rate", 1.0));
        var materials = new HashMap<String, MaterialEngineeringProfile>();
        for (var part : build.components().values()) {
            var id = MaterialVariantId.tryParse(part.materialVariant());
            // Variant engineering support must be authored, not silently inherited from a base material.
            if (id == null || id.hasVariant()) continue;
            var nativeStats = TinkersStats.read(id);
            if (!nativeStats.containsKey(SPEED)) continue;
            materials.put(part.materialVariant(), new MaterialEngineeringProfile(part.materialVariant(), Set.of("tconstruct:pick_head"), nativeStats, Map.of(), Set.of()));
        }
        var result = EngineeringEvaluator.evaluate(build, profile, materials);
        if (!result.allowed()) return new State(0, null, build, result.blockedReason());
        var part = build.components().get("head");
        if (part.materialQuanta() != 180 || !part.wholePart()) return new State(0, null, build, "drill_requires_one_whole_head");
        var head = registry.<HeadMaterialStats>getMaterialStats(MaterialVariantId.parse(part.materialVariant()).getId(), HeadMaterialStats.ID);
        if (head.isEmpty()) return new State(0, null, build, "missing_head_stats");
        return new State(result.values().get("cutting_rate"), head.get().tier(), build, "");
    }
}
