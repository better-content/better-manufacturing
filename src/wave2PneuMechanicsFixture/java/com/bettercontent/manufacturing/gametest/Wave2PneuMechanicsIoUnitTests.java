package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import me.desht.pneumaticcraft.api.crafting.ShapedRecipeNoMirror;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Native I/O orientations require a real part at initial acquisition, not at every paid reorientation. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsIoUnitTests {
    private Wave2PneuMechanicsIoUnitTests() {}

    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bothNativeAssemblyIoOrientationsConsumePlateAndKeepConversions(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"),
            "PneumaticCraft and TCon are required for assembly I/O unit acquisition");
        var manager = helper.getLevel().getRecipeManager();
        var plateItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:large_plate"));
        helper.assertTrue(plateItem instanceof ToolPartItem, "Native TCon plate missing");
        var plate = ((ToolPartItem) plateItem).withMaterial(MaterialVariantId.parse("tconstruct:rock#stone"));
        var importId = "pneumaticcraft:assembly_io_unit_import";
        var exportId = "pneumaticcraft:assembly_io_unit_export";
        for (var target : new String[]{importId, exportId}) {
            var found = manager.byKey(new ResourceLocation(target)).orElseThrow();
            helper.assertTrue(found instanceof ShapedRecipeNoMirror, "Native no-mirror assembly recipe type changed: " + target);
            var recipe = (ShapedRecipeNoMirror) found;
            helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3, "Native assembly grid changed");
            var input = grid();
            int plateSlots = 0;
            for (int i = 0; i < recipe.getIngredients().size(); i++) {
                var ingredient = recipe.getIngredients().get(i);
                if (ingredient.isEmpty()) continue;
                var candidates = ingredient.getItems();
                helper.assertTrue(candidates.length > 0, "Unobtainable native assembly I/O ingredient: " + target + " slot " + i);
                if (ingredient.test(plate)) {
                    plateSlots++;
                    input.setItem(i, plate.copy());
                } else input.setItem(i, candidates[0].copy());
            }
            helper.assertTrue(plateSlots == 1, "Exact BM part cost missing from native I/O recipe: " + target);
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Native I/O craft rejected real part: " + target);
            var output = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(target.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(output.getItem())))
                && output.getCount() == 1, "Native I/O result or quantity changed: " + target);
            for (int i = 0; i < input.getContainerSize(); i++) if (input.getItem(i).is(plateItem)) {
                input.setItem(i, ItemStack.EMPTY); break;
            }
            helper.assertTrue(!recipe.matches(input, helper.getLevel()), "Part-free native I/O root still matches: " + target);
        }
        for (var target : new String[]{importId, exportId}) {
            String opposite = target.equals(importId) ? exportId : importId;
            var suffix = target.equals(importId) ? "import_from_export" : "export_from_import";
            var found = manager.byKey(new ResourceLocation("pneumaticcraft:assembly_io_unit_" + suffix)).orElseThrow();
            helper.assertTrue(found instanceof ShapelessRecipe, "Native reversible I/O orientation was removed");
            var recipe = (ShapelessRecipe) found;
            var input = grid();
            input.setItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(opposite))));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Native orientation conversion rejected paid precursor");
            var output = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(target.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(output.getItem())))
                && output.getCount() == 1, "Reorientation changed output identity or quantity");
        }
        helper.succeed();
    }
}
