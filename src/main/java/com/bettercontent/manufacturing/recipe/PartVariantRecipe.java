package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Explicit recipe slots select the parts. Only calculated settings reach the output. */
public final class PartVariantRecipe extends ShapedRecipe {
    public record Part(String role, int slot) {
        public Part { Identifiers.role(role); if (slot < 0 || slot > 8) throw new IllegalArgumentException("Invalid part slot"); }
    }
    private final ShapedRecipe base;
    private final String function;
    private final List<Part> parts;
    public PartVariantRecipe(ShapedRecipe base, String function, List<Part> parts) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(), base.getResultItem(RegistryAccess.EMPTY), base.showNotification());
        this.base = base; this.function = Identifiers.id(function); this.parts = List.copyOf(parts);
        if (parts.isEmpty() || parts.size() > 9) throw new IllegalArgumentException("Invalid part count");
        var slots = new HashSet<Integer>(); var roles = new HashSet<String>();
        for (var part : parts) if (part.slot() >= getWidth() * getHeight() || getIngredients().get(part.slot()).isEmpty()
                || !slots.add(part.slot()) || !roles.add(part.role())) throw new IllegalArgumentException("Duplicate/empty part binding");
    }
    public ShapedRecipe base() { return base; }
    public String function() { return function; }
    public List<Part> parts() { return parts; }
    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.VARIANT.get(); }
    @Override public boolean matches(CraftingContainer grid, Level level) { return !assemble(grid, level.registryAccess()).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        for (int y = 0; y <= grid.getHeight() - getHeight(); y++) for (int x = 0; x <= grid.getWidth() - getWidth(); x++)
            for (boolean mirror : new boolean[]{false, true}) if (matchesAt(grid, x, y, mirror)) {
                var inputs = new LinkedHashMap<String, ItemStack>();
                for (var part : parts) {
                    int px = part.slot() % getWidth(), py = part.slot() / getWidth();
                    inputs.put(part.role(), grid.getItem(x + (mirror ? getWidth() - px - 1 : px) + (y + py) * grid.getWidth()));
                }
                var output = base.assemble(grid, registries);
                var id = ForgeRegistries.ITEMS.getKey(output.getItem());
                if (id == null) return ItemStack.EMPTY;
                // A native pressure/thermo bit is a finishing blank, not a retoolable
                // configured bit. The former's native lubricant/pressure history remains
                // untouched; any previously baked bit must not enter this recipe again.
                if (function.equals("better_manufacturing:pneumaticcraft/drilling") && Set.of(
                        "pneumaticcraft:drill_bit_iron", "pneumaticcraft:drill_bit_compressed_iron",
                        "pneumaticcraft:drill_bit_diamond", "pneumaticcraft:drill_bit_netherite").contains(id.toString())) {
                    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
                        var blank = grid.getItem(slot);
                        if (blank.is(output.getItem()) && blank.hasTag() && blank.getTag().contains(SettingsNbt.KEY))
                            return ItemStack.EMPTY;
                    }
                }
                // Sophisticated Core's next-tier recipe preserves the installed upgrade's filters,
                // directions and other configuration. Copy those native fields before replacing
                // only the freshly baked actuator setting of the advanced hopper upgrade.
                if (id.toString().equals("sophisticatedstorage:advanced_hopper_upgrade")) {
                    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
                        var predecessor = grid.getItem(slot);
                        var predecessorId = ForgeRegistries.ITEMS.getKey(predecessor.getItem());
                        if (predecessorId != null && predecessorId.toString().equals("sophisticatedstorage:hopper_upgrade") && predecessor.hasTag()) {
                            output.setTag(predecessor.getTag().copy());
                            break;
                        }
                    }
                }
                // Upgrading an installed PrettyPipes module must not erase its native
                // filters, routing, inventory or other configuration. The single medium
                // predecessor is physical input; the new handle supplies the new rating.
                String mediumModule = switch (id.toString()) {
                    case "prettypipes:high_extraction_module" -> "prettypipes:medium_extraction_module";
                    case "prettypipes:high_speed_module" -> "prettypipes:medium_speed_module";
                    default -> null;
                };
                if (mediumModule != null) {
                    ItemStack predecessor = ItemStack.EMPTY;
                    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
                        ItemStack candidate = grid.getItem(slot);
                        var candidateId = ForgeRegistries.ITEMS.getKey(candidate.getItem());
                        if (candidateId != null && mediumModule.equals(candidateId.toString())) {
                            if (!predecessor.isEmpty()) return ItemStack.EMPTY;
                            predecessor = candidate;
                        }
                    }
                    if (predecessor.isEmpty()) return ItemStack.EMPTY;
                    if (predecessor.hasTag()) output.setTag(predecessor.getTag().copy());
                }
                try {
                    // Overwrite only the predecessor's Manufacturing setting with the
                    // newly baked final value; all unrelated native NBT stays intact.
                    SettingsNbt.write(output.getOrCreateTag(), PartSettings.bake(id.toString(), function, inputs));
                    return output;
                } catch (IllegalArgumentException unsupported) { return ItemStack.EMPTY; }
            }
        return ItemStack.EMPTY;
    }
    private boolean matchesAt(CraftingContainer grid, int sx, int sy, boolean mirror) {
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
            int px = x - sx, py = y - sy;
            var ingredient = Ingredient.EMPTY;
            if (px >= 0 && py >= 0 && px < getWidth() && py < getHeight()) ingredient = getIngredients().get((mirror ? getWidth() - px - 1 : px) + py * getWidth());
            if (!ingredient.test(grid.getItem(x + y * grid.getWidth()))) return false;
        }
        return true;
    }
}
