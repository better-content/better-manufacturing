package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Effective KubeJS workshop route, rather than a dormant JSON fallback. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class CatalogueCreateWorkshopTests {
    private CatalogueCreateWorkshopTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bearingPaysHandleWithoutDroppingNativeSlabCasingOrShaft(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("kubejs:powered_works/workshop/mechanical_bearing")).orElseThrow();
        helper.assertTrue(found instanceof ShapedRecipe, "Effective mechanical bearing workshop root absent");
        var recipe = (ShapedRecipe) found;
        helper.assertTrue(recipe.getWidth() == 2 && recipe.getHeight() == 3,
            "Bearing lost early hand-crafting station or full native bill");
        var handle = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tool_handle"));
        helper.assertTrue(handle instanceof ToolPartItem, "Physical TConstruct handle absent");
        var grid = CreateDrillTests.grid();
        int paidSlot = -1, occupied = 0;
        for (int local = 0; local < recipe.getIngredients().size(); local++) {
            var ingredient = recipe.getIngredients().get(local);
            if (ingredient.isEmpty()) continue;
            occupied++;
            int slot = local % recipe.getWidth() + local / recipe.getWidth() * 3;
            if (ingredient.test(((ToolPartItem) handle).withMaterial(MaterialVariantId.parse("tconstruct:iron")))) {
                helper.assertTrue(paidSlot == -1, "Duplicate handle in native workshop bill");
                paidSlot = slot;
                grid.setItem(slot, ((ToolPartItem) handle).withMaterial(MaterialVariantId.parse("tconstruct:iron")));
            } else {
                var alternatives = ingredient.getItems();
                helper.assertTrue(alternatives.length != 0, "Empty native workshop ingredient at " + local);
                grid.setItem(slot, alternatives[0].copy());
            }
        }
        helper.assertTrue(paidSlot != -1 && occupied == 4, "Slab, casing, shaft or physical handle displaced");
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Loaded native bill plus paid handle rejected");
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:mechanical_bearing")))
            && result.getCount() == 1 && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
            "Bearing changed yield or returned one of its consumed native costs");
        grid.setItem(paidSlot, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Unpaid bearing root remains available");
        helper.succeed();
    }
}
