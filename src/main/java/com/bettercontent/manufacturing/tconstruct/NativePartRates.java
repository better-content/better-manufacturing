package com.bettercontent.manufacturing.tconstruct;

import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

/** Native tool-part statistics at recipe time. No material-specific machine ratings or permissions. */
public final class NativePartRates {
    private NativePartRates() {}

    public enum Measure {
        HEAD_MINING_SPEED, HEAD_DURABILITY, HEAD_ATTACK,
        HANDLE_MINING_SPEED, HANDLE_DURABILITY, HANDLE_ATTACK_SPEED, HANDLE_ATTACK_DAMAGE,
        LIMB_DURABILITY, LIMB_DRAW_SPEED, LIMB_VELOCITY
    }

    /** Compare the actual part material with the same native Tinkers stat on iron. */
    public static double relative(ToolPartItem part, ItemStack stack, Measure measure) {
        if (part == null || stack == null || stack.isEmpty() || stack.getItem() != part || measure == null
            || !MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Missing native part stats");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Part cannot use material");
        var registry = MaterialRegistry.getInstance();
        var iron = new MaterialId("tconstruct:iron");
        if (head(measure)) {
            if (!part.getStatType().equals(HeadMaterialStats.ID)) throw new IllegalArgumentException("Not a native head part");
            var selected = registry.<HeadMaterialStats>getMaterialStats(material.getId(), HeadMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("Missing native head stats"));
            var reference = registry.<HeadMaterialStats>getMaterialStats(iron, HeadMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("Missing native iron head stats"));
            return observed(stack, measure, headValue(selected, measure), headValue(reference, measure));
        }
        if (limb(measure)) {
            if (!part.getStatType().equals(LimbMaterialStats.ID)) throw new IllegalArgumentException("Not a native limb part");
            var selected = registry.<LimbMaterialStats>getMaterialStats(material.getId(), LimbMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("Missing native limb stats"));
            var reference = registry.<LimbMaterialStats>getMaterialStats(iron, LimbMaterialStats.ID)
                .orElseThrow(() -> new IllegalArgumentException("Missing native iron limb stats"));
            return observed(stack, measure, limbValue(selected, measure), limbValue(reference, measure));
        }
        if (!part.getStatType().equals(HandleMaterialStats.ID)) throw new IllegalArgumentException("Not a native handle part");
        var selected = registry.<HandleMaterialStats>getMaterialStats(material.getId(), HandleMaterialStats.ID)
            .orElseThrow(() -> new IllegalArgumentException("Missing native handle stats"));
        var reference = registry.<HandleMaterialStats>getMaterialStats(iron, HandleMaterialStats.ID)
            .orElseThrow(() -> new IllegalArgumentException("Missing native iron handle stats"));
        return observed(stack, measure, handleValue(selected, measure), handleValue(reference, measure));
    }

    private static double observed(ItemStack stack, Measure measure, double selected, double reference) {
        double rate = derive(measure, selected, reference);
        NativePartCapture.record(stack, measure, selected, reference);
        return rate;
    }

    /** Pure derivation for source-unit tests; machine physical envelope is unchanged. */
    public static double derive(Measure measure, double selected, double reference) {
        if (measure == null || !Double.isFinite(selected) || !Double.isFinite(reference))
            throw new IllegalArgumentException("Nonfinite native stat");
        double ratio;
        if (head(measure) || measure == Measure.LIMB_DURABILITY) {
            if (selected < 0 || reference <= 0) throw new IllegalArgumentException("Invalid native base stat");
            ratio = selected / reference;
        } else {
            if (selected <= -1 || reference <= -1) throw new IllegalArgumentException("Invalid native modifier");
            ratio = (1 + selected) / (1 + reference);
        }
        if (!Double.isFinite(ratio) || ratio <= 0) throw new IllegalArgumentException("Invalid native rate");
        return Math.max(.75, Math.min(1.5, ratio));
    }

    private static boolean head(Measure measure) {
        return switch (measure) {
            case HEAD_MINING_SPEED, HEAD_DURABILITY, HEAD_ATTACK -> true;
            default -> false;
        };
    }
    private static boolean limb(Measure measure) {
        return switch (measure) {
            case LIMB_DURABILITY, LIMB_DRAW_SPEED, LIMB_VELOCITY -> true;
            default -> false;
        };
    }
    private static double headValue(HeadMaterialStats stats, Measure measure) {
        return switch (measure) {
            case HEAD_MINING_SPEED -> stats.miningSpeed();
            case HEAD_DURABILITY -> stats.durability();
            case HEAD_ATTACK -> stats.attack();
            default -> throw new IllegalArgumentException("Not a head stat");
        };
    }
    private static double limbValue(LimbMaterialStats stats, Measure measure) {
        return switch (measure) {
            case LIMB_DURABILITY -> stats.durability();
            case LIMB_DRAW_SPEED -> stats.drawSpeed();
            case LIMB_VELOCITY -> stats.velocity();
            default -> throw new IllegalArgumentException("Not a limb stat");
        };
    }
    private static double handleValue(HandleMaterialStats stats, Measure measure) {
        return switch (measure) {
            case HANDLE_MINING_SPEED -> stats.miningSpeed();
            case HANDLE_DURABILITY -> stats.durability();
            case HANDLE_ATTACK_SPEED -> stats.meleeSpeed();
            case HANDLE_ATTACK_DAMAGE -> stats.attackDamage();
            default -> throw new IllegalArgumentException("Not a handle stat");
        };
    }
}
