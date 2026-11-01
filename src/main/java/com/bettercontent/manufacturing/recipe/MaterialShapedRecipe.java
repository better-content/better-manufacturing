package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.tconstruct.NativeParts;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.LinkedHashMap;
import java.util.List;

/** Vanilla crafting type, deterministic previews and explicit normalized-pattern source slots. */
public final class MaterialShapedRecipe extends ShapedRecipe {
    public record Transfer(String role, int slot) {
        public Transfer { Identifiers.role(role); if (slot < 0 || slot > 8) throw new IllegalArgumentException("Invalid source slot"); }
    }
    private record Match(int x, int y, boolean mirrored) {}
    private final ShapedRecipe base;
    private final String function;
    private final List<Transfer> transfers;
    public MaterialShapedRecipe(ShapedRecipe base, String function, List<Transfer> transfers) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(),
              base.getResultItem(RegistryAccess.EMPTY).copy(), base.showNotification());
        this.base = base;
        this.function = Identifiers.id(function);
        this.transfers = List.copyOf(transfers);
        if (transfers.isEmpty() || transfers.size() > 9) throw new IllegalArgumentException("Invalid transfer count");
        var slots = new java.util.HashSet<Integer>(); var roles = new java.util.HashSet<String>();
        for (Transfer transfer : transfers) {
            if (transfer.slot() >= base.getWidth() * base.getHeight() || base.getIngredients().get(transfer.slot()).isEmpty()
                    || !slots.add(transfer.slot()) || !roles.add(transfer.role())) throw new IllegalArgumentException("Duplicate/empty source slot or role");
        }
        if (base.getResultItem(RegistryAccess.EMPTY).hasTag()) throw new IllegalArgumentException("Output metadata requires an explicit transfer adapter");
    }
    public ShapedRecipe base() { return base; }
    public String function() { return function; }
    public List<Transfer> transfers() { return transfers; }
    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.SHAPED.get(); }
    @Override public boolean matches(CraftingContainer inventory, Level level) { return !assemble(inventory, level.registryAccess()).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess registries) {
        Match match = findMatch(inventory);
        if (match == null) return ItemStack.EMPTY;
        ItemStack result = base.assemble(inventory, registries);
        if (result.isEmpty()) return ItemStack.EMPTY;
        var parts = new LinkedHashMap<String, ComponentProvenance>();
        try {
            for (Transfer transfer : transfers) {
                int px = transfer.slot() % getWidth(), py = transfer.slot() / getWidth();
                int x = match.x() + (match.mirrored() ? getWidth() - px - 1 : px);
                ItemStack source = inventory.getItem(x + (match.y() + py) * inventory.getWidth());
                parts.put(transfer.role(), NativeParts.read(source).divide(result.getCount()));
            }
            var id = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (id == null) return ItemStack.EMPTY;
            BuildNbt.write(result.getOrCreateTag(), new MachineBuild(MachineBuild.VERSION, id.toString(), function, parts));
            return result;
        } catch (IllegalArgumentException malformedOrUnsupported) { return ItemStack.EMPTY; }
    }
    private Match findMatch(CraftingContainer inventory) {
        for (int y = 0; y <= inventory.getHeight() - getHeight(); y++) {
            for (int x = 0; x <= inventory.getWidth() - getWidth(); x++) {
                for (boolean mirror : new boolean[]{false, true}) {
                    if (matchesAt(inventory, x, y, mirror)) return new Match(x, y, mirror);
                }
            }
        }
        return null;
    }
    private boolean matchesAt(CraftingContainer inventory, int startX, int startY, boolean mirrored) {
        for (int y = 0; y < inventory.getHeight(); y++) {
            for (int x = 0; x < inventory.getWidth(); x++) {
                int localX = x - startX, localY = y - startY;
                var ingredient = net.minecraft.world.item.crafting.Ingredient.EMPTY;
                if (localX >= 0 && localX < getWidth() && localY >= 0 && localY < getHeight()) {
                    int sourceX = mirrored ? getWidth() - localX - 1 : localX;
                    ingredient = getIngredients().get(sourceX + localY * getWidth());
                }
                if (!ingredient.test(inventory.getItem(x + y * inventory.getWidth()))) return false;
            }
        }
        return true;
    }
}
