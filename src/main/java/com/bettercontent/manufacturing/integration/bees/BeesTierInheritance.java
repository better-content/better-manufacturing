package com.bettercontent.manufacturing.integration.bees;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/** Keep a consumed unit's native configuration when fitting a new set of working parts. */
public final class BeesTierInheritance {
    private BeesTierInheritance() {}

    public static boolean copy(String outputId, CraftingContainer grid, ItemStack result) {
        String predecessorId = predecessor(outputId);
        if (predecessorId == null) return true;
        ItemStack predecessor = ItemStack.EMPTY;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack candidate = grid.getItem(slot);
            var id = ForgeRegistries.ITEMS.getKey(candidate.getItem());
            if (id != null && predecessorId.equals(id.toString())) {
                if (!predecessor.isEmpty()) return false;
                predecessor = candidate;
            }
        }
        if (predecessor.isEmpty()) return false;
        var tag = predecessor.getTag();
        if (tag != null && tag.contains(SettingsNbt.KEY)) {
            if (!BeesSettings.supported(predecessorId)) return false;
            var settings = SettingsNbt.read(tag, predecessorId);
            if (settings == null || !settings.properties().keySet().equals(Set.of("bees_rate"))) return false;
            double rate = settings.properties().get("bees_rate");
            if (!Double.isFinite(rate) || rate < .75 || rate > 1.5) return false;
        }
        if (tag != null) result.setTag(tag.copy());
        return true;
    }

    private static String predecessor(String outputId) {
        if (!outputId.startsWith("complicated_bees:gyrofuge_") || !outputId.endsWith("_unit")) return null;
        String unit = outputId.substring("complicated_bees:gyrofuge_".length());
        String kind = unit;
        String prefix;
        if (unit.startsWith("advanced_")) {
            prefix = "";
            kind = unit.substring("advanced_".length());
        } else if (!unit.startsWith("basic_")) {
            prefix = "basic_";
        } else if (unit.startsWith("basic_")) {
            kind = unit.substring("basic_".length());
            if (!Set.of("speed_unit", "efficiency_unit", "extraction_unit", "processing_unit").contains(kind)) return null;
            return "complicated_bees:gyrofuge_base";
        } else return null;
        if (!Set.of("speed_unit", "efficiency_unit", "extraction_unit", "processing_unit").contains(kind)) return null;
        return "complicated_bees:gyrofuge_" + prefix + kind;
    }
}
