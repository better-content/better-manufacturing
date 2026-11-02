package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.tconstruct.NativeParts;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Explicit ingredient-index bindings. Ambiguous assignments with different provenance reject. */
public final class MaterialShapelessRecipe extends ShapelessRecipe {
    public record Binding(String role, int ingredient) {
        public Binding { Identifiers.role(role); if (ingredient < 0 || ingredient > 8) throw new IllegalArgumentException("Invalid ingredient index"); }
    }
    private final ShapelessRecipe base;
    private final String function;
    private final List<Binding> bindings;
    private final Set<String> qualifications;
    private final TransferPlan plan;
    public MaterialShapelessRecipe(ShapelessRecipe base, String function, List<Binding> bindings, Set<String> qualifications) {
        super(base.getId(), base.getGroup(), base.category(), base.getResultItem(RegistryAccess.EMPTY).copy(), base.getIngredients());
        this.base = base; this.function = Identifiers.id(function); this.bindings = List.copyOf(bindings); this.qualifications = Set.copyOf(qualifications);
        int count = base.getIngredients().size();
        if (count < 1 || count > 9 || base.getResultItem(RegistryAccess.EMPTY).hasTag()) throw new IllegalArgumentException("Unsupported shapeless result/ingredients");
        if (bindings.isEmpty() || bindings.size() > count) throw new IllegalArgumentException("Invalid binding count");
        for (var binding : bindings) if (binding.ingredient() >= count) throw new IllegalArgumentException("Missing transfer ingredient");
        this.plan = new TransferPlan(bindings.stream().map(binding -> new TransferPlan.Move(binding.ingredient(), "part", binding.role())).toList(), List.of(), qualifications);
    }
    public ShapelessRecipe base() { return base; }
    public String function() { return function; }
    public List<Binding> bindings() { return bindings; }
    public Set<String> qualifications() { return qualifications; }
    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.SHAPELESS.get(); }
    @Override public boolean matches(CraftingContainer inventory, Level level) { return !assemble(inventory, level.registryAccess()).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess registries) {
        var stacks = new ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (!inventory.getItem(slot).isEmpty()) stacks.add(inventory.getItem(slot));
        int n = getIngredients().size();
        if (stacks.size() != n) return ItemStack.EMPTY;
        // This serializer currently binds native parts, not subassemblies. Never silently
        // consume a provenance-bearing intermediate in an unbound ordinary ingredient.
        if (stacks.stream().anyMatch(stack -> stack.hasTag() && stack.getTag().contains(BuildNbt.KEY))) return ItemStack.EMPTY;
        // Evaluate native ingredients once. Matching thereafter is bounded bitmask DP, not n! permutations.
        boolean[][] edges = new boolean[n][n];
        for (int ingredient = 0; ingredient < n; ingredient++)
            for (int slot = 0; slot < n; slot++) edges[ingredient][slot] = getIngredients().get(ingredient).test(stacks.get(slot));
        if (!possible(edges, -1, -1)) return ItemStack.EMPTY;
        try {
            var sources = new LinkedHashMap<Integer, TransferPlan.Source>();
            for (var binding : bindings) {
                ComponentProvenance selected = null;
                for (int slot = 0; slot < n; slot++) {
                    if (!edges[binding.ingredient()][slot] || !possible(edges, binding.ingredient(), slot)) continue;
                    var part = NativeParts.read(stacks.get(slot));
                    if (selected != null && !selected.equals(part)) return ItemStack.EMPTY;
                    selected = part;
                }
                if (selected == null) return ItemStack.EMPTY;
                sources.put(binding.ingredient(), TransferPlan.Source.part(selected));
            }
            ItemStack result = base.assemble(inventory, registries);
            if (result.isEmpty()) return ItemStack.EMPTY;
            ResourceLocation chassis = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (chassis == null) return ItemStack.EMPTY;
            BuildNbt.write(result.getOrCreateTag(), plan.apply(chassis.toString(), function, sources, result.getCount()));
            return result;
        } catch (IllegalArgumentException unsupported) { return ItemStack.EMPTY; }
    }
    private static boolean possible(boolean[][] edges, int forcedIngredient, int forcedSlot) {
        byte[] memo = new byte[1 << edges.length];
        return match(edges, forcedIngredient, forcedSlot, 0, memo);
    }
    private static boolean match(boolean[][] edges, int forcedIngredient, int forcedSlot, int used, byte[] memo) {
        int ingredient = Integer.bitCount(used), n = edges.length;
        if (ingredient == n) return true;
        if (memo[used] != 0) return memo[used] == 1;
        for (int slot = 0; slot < n; slot++) {
            if ((used & (1 << slot)) != 0 || !edges[ingredient][slot]) continue;
            if ((ingredient == forcedIngredient && slot != forcedSlot) || (slot == forcedSlot && ingredient != forcedIngredient)) continue;
            if (match(edges, forcedIngredient, forcedSlot, used | (1 << slot), memo)) { memo[used] = 1; return true; }
        }
        memo[used] = -1; return false;
    }
}
