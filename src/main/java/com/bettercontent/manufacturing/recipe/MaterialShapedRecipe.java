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
    public record Transfer(String role, int slot, String sourceRole, String sourceFunction) {
        public Transfer(String role, int slot) { this(role, slot, "part", null); }
        public Transfer {
            Identifiers.role(role); Identifiers.role(sourceRole);
            if (slot < 0 || slot > 8) throw new IllegalArgumentException("Invalid source slot");
            if (sourceFunction != null) Identifiers.id(sourceFunction);
            else if (!sourceRole.equals("part")) throw new IllegalArgumentException("Subassembly role requires an explicit source function");
        }
    }
    private record Match(int x, int y, boolean mirrored) {}
    private final ShapedRecipe base;
    private final String function;
    private final List<Transfer> transfers;
    private final java.util.Set<String> qualifications;
    private final TransferPlan provenance;
    public MaterialShapedRecipe(ShapedRecipe base, String function, List<Transfer> transfers) {
        this(base, function, transfers, java.util.Set.of());
    }
    public MaterialShapedRecipe(ShapedRecipe base, String function, List<Transfer> transfers, java.util.Set<String> qualifications) {
        this(base, function, transfers, qualifications, List.of());
    }
    public MaterialShapedRecipe(ShapedRecipe base, String function, List<Transfer> transfers, java.util.Set<String> qualifications,
                                List<TransferPlan.QualificationMove> inheritedQualifications) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(),
              base.getResultItem(RegistryAccess.EMPTY).copy(), base.showNotification());
        this.base = base;
        this.function = Identifiers.id(function);
        this.transfers = List.copyOf(transfers);
        this.qualifications = java.util.Set.copyOf(qualifications);
        if (qualifications.size() > MachineBuild.MAX_QUALIFICATIONS) throw new IllegalArgumentException("Too many qualifications");
        qualifications.forEach(Identifiers::id);
        this.provenance = new TransferPlan(transfers.stream().map(transfer -> new TransferPlan.Move(transfer.slot(), transfer.sourceRole(), transfer.role())).toList(), inheritedQualifications, qualifications);
        if (transfers.isEmpty() || transfers.size() > MachineBuild.MAX_ROLES) throw new IllegalArgumentException("Invalid transfer count");
        var slots = new java.util.HashMap<Integer, Transfer>(); var roles = new java.util.HashSet<String>();
        for (Transfer transfer : transfers) {
            if (transfer.slot() >= base.getWidth() * base.getHeight() || base.getIngredients().get(transfer.slot()).isEmpty()
                    || !roles.add(transfer.role())) throw new IllegalArgumentException("Duplicate/empty source slot or role");
            var previous = slots.putIfAbsent(transfer.slot(), transfer);
            if (previous != null && !java.util.Objects.equals(previous.sourceFunction(), transfer.sourceFunction()))
                throw new IllegalArgumentException("Conflicting source declarations");
        }
        if (base.getResultItem(RegistryAccess.EMPTY).hasTag()) throw new IllegalArgumentException("Output metadata requires an explicit transfer adapter");
    }
    public ShapedRecipe base() { return base; }
    public String function() { return function; }
    public List<Transfer> transfers() { return transfers; }
    public java.util.Set<String> qualifications() { return qualifications; }
    public List<TransferPlan.QualificationMove> inheritedQualifications() { return provenance.qualificationMoves(); }
    @Override public RecipeSerializer<?> getSerializer() { return ManufacturingRecipes.SHAPED.get(); }
    @Override public boolean matches(CraftingContainer inventory, Level level) { return !assemble(inventory, level.registryAccess()).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess registries) {
        Match match = findMatch(inventory);
        if (match == null) return ItemStack.EMPTY;
        ItemStack result = base.assemble(inventory, registries);
        if (result.isEmpty()) return ItemStack.EMPTY;
        var sources = new LinkedHashMap<Integer, TransferPlan.Source>();
        try {
            var physicalSlots = new java.util.HashSet<Integer>();
            for (Transfer transfer : transfers) {
                int px = transfer.slot() % getWidth(), py = transfer.slot() / getWidth();
                int x = match.x() + (match.mirrored() ? getWidth() - px - 1 : px);
                int physicalSlot = x + (match.y() + py) * inventory.getWidth();
                physicalSlots.add(physicalSlot);
                if (sources.containsKey(transfer.slot())) continue;
                ItemStack source = inventory.getItem(physicalSlot);
                if (transfer.sourceFunction() == null) sources.put(transfer.slot(), TransferPlan.Source.part(NativeParts.read(source)));
                else {
                    // Only the declared build is transferable. Unknown holder contents/capabilities
                    // must stay in the source slot, not disappear into the new chassis.
                    if (!source.hasTag() || !source.getTag().getAllKeys().equals(java.util.Set.of(BuildNbt.KEY))) return ItemStack.EMPTY;
                    var serialized = source.serializeNBT();
                    if (serialized.contains("ForgeCaps") && (!serialized.contains("ForgeCaps", net.minecraft.nbt.Tag.TAG_COMPOUND)
                            || !serialized.getCompound("ForgeCaps").isEmpty())) return ItemStack.EMPTY;
                    var sourceId = ForgeRegistries.ITEMS.getKey(source.getItem());
                    if (sourceId == null) return ItemStack.EMPTY;
                    var build = BuildNbt.read(source.getTag(), sourceId.toString(), transfer.sourceFunction());
                    if (!build.valid() || !build.build().hasProvenance()) return ItemStack.EMPTY;
                    sources.put(transfer.slot(), TransferPlan.Source.build(build.build()));
                }
            }
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var source = inventory.getItem(slot);
                if (!physicalSlots.contains(slot) && source.hasTag() && source.getTag().contains(BuildNbt.KEY)) return ItemStack.EMPTY;
            }
            var id = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (id == null) return ItemStack.EMPTY;
            BuildNbt.write(result.getOrCreateTag(), provenance.apply(id.toString(), function, sources, result.getCount()));
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
