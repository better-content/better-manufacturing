package com.bettercontent.manufacturing.tconstruct;

import com.bettercontent.manufacturing.api.ComponentProvenance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;
import java.util.Set;

/** Explicit 3.11.2.166 costs. Native fixtures verify them against Part Builder recipes. */
public final class NativeParts {
    public static final Map<String, Integer> COSTS = Map.ofEntries(
        Map.entry("pick_head", 2), Map.entry("hammer_head", 8), Map.entry("small_blade", 2),
        Map.entry("broad_blade", 8), Map.entry("small_axe_head", 2), Map.entry("broad_axe_head", 8),
        Map.entry("tool_handle", 1), Map.entry("tough_handle", 3), Map.entry("tool_binding", 1),
        Map.entry("tough_binding", 3), Map.entry("large_plate", 4), Map.entry("bow_limb", 2),
        Map.entry("bow_grip", 2), Map.entry("bowstring", 1));
    private static final Set<String> RETAINED = Set.of("display", "BetterContentJackhammerWear",
        "better_tinkers_loot_affixes_affixes", "better_tinkers_loot_affixes_mutation_locked",
        "better_tinkers_loot_affixes_salvage_band", "better_tinkers_loot_affixes_salvage_seed",
        "better_tinkers_loot_affixes_salvage_spent", "better_tinkers_loot_affixes_origin",
        "better_tinkers_loot_affixes_provenance", "better_tinkers_loot_affixes_natural",
        "better_tinkers_loot_affixes_data_version");
    private NativeParts() {}
    /** Refund a whole attributable native part, retaining reviewed metadata; never guess fractional stock. */
    public static ItemStack recover(ComponentProvenance component) {
        if (!component.wholePart()) throw new IllegalArgumentException("Fractional component needs a material recovery recipe");
        var item = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(component.item()));
        if (!(item instanceof ToolPartItem part)) throw new IllegalArgumentException("Unknown recovered part");
        var result = part.withMaterial(slimeknights.tconstruct.library.materials.definition.MaterialVariantId.parse(component.materialVariant()));
        if (!component.retainedData().isEmpty()) {
            if (!component.retainedData().keySet().equals(Set.of("better_manufacturing:source_part_tag")))
                throw new IllegalArgumentException("Unknown retained part data");
            try {
                var retained = net.minecraft.nbt.TagParser.parseTag(component.retainedData().get("better_manufacturing:source_part_tag"));
                com.bettercontent.manufacturing.persistence.BoundedNbt.check(retained);
                if (!RETAINED.containsAll(retained.getAllKeys())) throw new IllegalArgumentException("Unreviewed retained fields");
                result.getOrCreateTag().merge(retained);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new IllegalArgumentException("Invalid retained component NBT", invalid); }
        }
        if (!read(result).equals(component)) throw new IllegalArgumentException("Recovered native part does not match attributed identity/quantity/state");
        return result;
    }
    public static ComponentProvenance read(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part) || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Not a loaded native Tinkers part");
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("tconstruct") || !COSTS.containsKey(id.getPath()))
            throw new IllegalArgumentException("Unsupported part identity");
        if (stack.hasTag()) com.bettercontent.manufacturing.persistence.BoundedNbt.check(stack.getTag());
        // Capabilities are outside ItemStack.getTag(). Never discard or embed an unknown inventory/capability.
        var serialized = stack.serializeNBT();
        if (serialized.contains("ForgeCaps") && !serialized.getCompound("ForgeCaps").isEmpty())
            throw new IllegalArgumentException("Unsupported component capability data");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported material/stat role");
        String variant = material.getId().toString() + (material.hasVariant() ? "#" + material.getVariant() : "");
        CompoundTag kept = new CompoundTag();
        if (stack.hasTag()) {
            for (String key : stack.getTag().getAllKeys()) {
                if (key.equals(ToolPartItem.MATERIAL_TAG)) continue;
                if (!RETAINED.contains(key)) throw new IllegalArgumentException("Unsupported component metadata: " + key);
                kept.put(key, stack.getTag().get(key).copy());
            }
        }
        Map<String, String> data = kept.isEmpty() ? Map.of() : Map.of("better_manufacturing:source_part_tag", kept.toString());
        return new ComponentProvenance(id.toString(), variant, part.getStatType().toString(),
            COSTS.get(id.getPath()) * ComponentProvenance.QUANTA_PER_UNIT, true, data);
    }
}
