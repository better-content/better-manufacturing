package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import me.desht.pneumaticcraft.common.recipes.special.CompressorUpgradeCrafting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** A paid native liquid compressor remains paid when upgraded through PNCR's own recipe. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsLiquidUpgradeTests {
    private Wave2PneuMechanicsLiquidUpgradeTests() {}

    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }

    private static ItemStack plateOrHandle(String id) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (!(item instanceof ToolPartItem part)) throw new AssertionError("Native TCon compressor part missing: " + id);
        return part.withMaterial(MaterialVariantId.parse("tconstruct:iron"));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void nativeLiquidUpgradeKeepsPaidPartsAndNativeOwnerTag(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"),
            "Native PneumaticCraft and TCon are required");
        var recipes = helper.getLevel().getRecipeManager();
        var root = recipes.byKey(new ResourceLocation("pneumaticcraft:liquid_compressor")).orElseThrow();
        helper.assertTrue(root instanceof PartVariantRecipe, "Loaded liquid compressor lost the BM part recipe");
        var base = (PartVariantRecipe) root;
        helper.assertTrue(base.getWidth() == 3 && base.getHeight() == 2, "Native liquid compressor footprint changed");
        var input = grid();
        int paid = 0;
        for (int i = 0; i < base.getIngredients().size(); i++) {
            var ingredient = base.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            ItemStack stack;
            if (ingredient.test(plateOrHandle("tconstruct:large_plate"))) {
                stack = plateOrHandle("tconstruct:large_plate"); paid++;
            } else if (ingredient.test(plateOrHandle("tconstruct:tough_handle"))) {
                stack = plateOrHandle("tconstruct:tough_handle"); paid++;
            } else {
                var candidates = ingredient.getItems();
                helper.assertTrue(candidates.length > 0, "Missing native liquid compressor input " + i);
                stack = candidates[0].copy();
            }
            input.setItem(i, stack);
        }
        helper.assertTrue(paid == 2 && base.matches(input, helper.getLevel()),
            "Native root did not require a real plate and handle");
        var liquid = base.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue("pneumaticcraft:liquid_compressor".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(liquid.getItem())))
                && liquid.getCount() == 1, "Native liquid compressor identity/count changed");
        var source = SettingsNbt.read(liquid.getTag(), "pneumaticcraft:liquid_compressor");
        helper.assertTrue(source != null && source.properties().keySet().equals(java.util.Set.of("compression_rate", "pressure_envelope")),
            "Physical part stats not captured in the liquid compressor");

        var owner = new CompoundTag(); owner.putString("PneumaticOwnerMarker", "preserve");
        liquid.getOrCreateTag().put("BlockEntityTag", owner);
        var upgrade = recipes.byKey(new ResourceLocation("pneumaticcraft:advanced_liquid_compressor")).orElseThrow();
        helper.assertTrue(upgrade instanceof CompressorUpgradeCrafting,
            "Native PNCR compressor-upgrade serializer changed");
        var nativeUpgrade = (CompressorUpgradeCrafting) upgrade;
        helper.assertTrue(nativeUpgrade.getWidth() == 3 && nativeUpgrade.getHeight() == 3,
            "Native PNCR compressor-upgrade footprint changed");
        var upgradeGrid = grid();
        int predecessorCount = 0;
        for (int i = 0; i < nativeUpgrade.getIngredients().size(); i++) {
            var ingredient = nativeUpgrade.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            if (ingredient.test(liquid)) { upgradeGrid.setItem(i, liquid.copy()); predecessorCount++; }
            else {
                var candidates = ingredient.getItems();
                helper.assertTrue(candidates.length > 0, "Missing native advanced liquid input " + i);
                upgradeGrid.setItem(i, candidates[0].copy());
            }
        }
        helper.assertTrue(predecessorCount == 1 && nativeUpgrade.matches(upgradeGrid, helper.getLevel()),
            "Native upgrade rejected the paid liquid compressor");
        var advanced = nativeUpgrade.assemble(upgradeGrid, helper.getLevel().registryAccess());
        helper.assertTrue("pneumaticcraft:advanced_liquid_compressor".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(advanced.getItem())))
                && advanced.getCount() == 1, "Advanced liquid output identity/count changed");
        var inherited = SettingsNbt.read(advanced.getTag(), "pneumaticcraft:advanced_liquid_compressor");
        helper.assertTrue(inherited != null && inherited.properties().equals(source.properties())
                && inherited.materials().equals(source.materials()), "Native upgrade dropped the source's paid settings");
        helper.assertTrue(advanced.getTag() != null && advanced.getTag().getCompound("BlockEntityTag")
                .getString("PneumaticOwnerMarker").equals("preserve"), "Native upgrade erased unrelated owner NBT");
        helper.succeed();
    }
}
