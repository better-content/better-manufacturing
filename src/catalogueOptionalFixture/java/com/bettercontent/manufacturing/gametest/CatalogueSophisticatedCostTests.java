package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.sophisticatedstorage.SophisticatedCostBundleIngredient;
import com.bettercontent.manufacturing.integration.sophisticatedstorage.SophisticatedCostBundleRecipe;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Route-specific bundle and native three-link conversion must pay costs at the original crafting station. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class CatalogueSophisticatedCostTests {
    private CatalogueSophisticatedCostTests() {}

    private static ItemStack item(String id) {
        var value = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (value == null) throw new AssertionError("Native Sophisticated item absent: " + id);
        return new ItemStack(value);
    }

    private static ItemStack part(String id) {
        if (!(item(id).getItem() instanceof ToolPartItem tool)) throw new AssertionError("Part unavailable: " + id);
        return tool.withMaterial(MaterialVariantId.parse("tconstruct:iron"));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void earlyUpgradeBaseAndControllerLinkKeepOriginalYieldAndCosts(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var found = manager.byKey(new ResourceLocation("better_manufacturing:sophisticated_cost_bundle/upgrade_base_sophisticatedstorage"))
            .orElseThrow();
        helper.assertTrue(found instanceof SophisticatedCostBundleRecipe, "Storage upgrade paid bundle serializer absent");
        var prep = (SophisticatedCostBundleRecipe) found;
        var input = CreateDrillTests.grid();
        var plank = new ItemStack(Items.OAK_PLANKS);
        plank.getOrCreateTag().putString("owner_native_field", "preserved");
        input.setItem(0, plank); input.setItem(1, new ItemStack(Items.OAK_PLANKS));
        input.setItem(2, part("tconstruct:large_plate")); input.setItem(3, part("tconstruct:tool_handle"));
        helper.assertTrue(prep.matches(input, helper.getLevel()), "Displaced native plank or physical parts rejected");
        var paid = prep.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(paid.is(Items.OAK_PLANKS) && paid.getCount() == 1 && paid.hasTag()
            && "preserved".equals(paid.getTag().getString("owner_native_field"))
            && "sophisticatedstorage:upgrade_base".equals(paid.getTag().getString(SophisticatedCostBundleIngredient.KEY)),
            "Same-identity native plank lost owner NBT or exact route marker");
        input.setItem(3, ItemStack.EMPTY);
        helper.assertTrue(!prep.matches(input, helper.getLevel()), "Bundle accepted fewer than two physical parts");
        var root = manager.byKey(new ResourceLocation("sophisticatedstorage:upgrade_base")).orElseThrow();
        helper.assertTrue(root instanceof ShapedRecipe, "Native crafting-table upgrade-base serializer lost");
        var recipe = (ShapedRecipe) root;
        var craft = CreateDrillTests.grid();
        craft.setItem(0, paid.copy());
        for (int slot = 1; slot < recipe.getIngredients().size(); slot++) {
            var ingredient = recipe.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            var samples = ingredient.getItems();
            helper.assertTrue(samples.length != 0, "Native upgrade-base bill missing at " + slot);
            craft.setItem(slot, samples[0].copy());
        }
        helper.assertTrue(recipe.matches(craft, helper.getLevel())
            && recipe.assemble(craft, helper.getLevel().registryAccess()).is(item("sophisticatedstorage:upgrade_base").getItem()),
            "Paid native upgrade-base bill failed original hand crafting");
        craft.setItem(0, new ItemStack(Items.OAK_PLANKS));
        helper.assertTrue(!recipe.matches(craft, helper.getLevel()), "Raw plank bypassed paid bundle");
        var alternate = manager.byKey(new ResourceLocation("sophisticatedstorage:storage_link_from_controller")).orElseThrow();
        helper.assertTrue(alternate instanceof ShapelessRecipe, "Native controller-to-three-links route disappeared");
        var links = (ShapelessRecipe) alternate;
        var linkGrid = CreateDrillTests.grid();
        linkGrid.setItem(0, item("sophisticatedstorage:controller"));
        linkGrid.setItem(1, new ItemStack(Items.ENDER_PEARL));
        linkGrid.setItem(2, part("tconstruct:large_plate"));
        linkGrid.setItem(3, part("tconstruct:tool_handle"));
        helper.assertTrue(links.matches(linkGrid, helper.getLevel())
            && links.assemble(linkGrid, helper.getLevel().registryAccess()).getCount() == 3,
            "Native controller/ender-pearl route lost its three-link yield");
        linkGrid.setItem(3, ItemStack.EMPTY);
        helper.assertTrue(!links.matches(linkGrid, helper.getLevel()), "Part-free native alternate still yields three links");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void advancedHopperRetainsPredecessorFiltersAndReplacesOnlyTheRating(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("sophisticatedstorage:advanced_hopper_upgrade")).orElseThrow();
        helper.assertTrue(found instanceof PartVariantRecipe, "Native advanced hopper replacement absent");
        var recipe = (PartVariantRecipe) found;
        var grid = CreateDrillTests.grid();
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            var ingredient = recipe.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            if (slot == 0) grid.setItem(slot, part("tconstruct:tool_handle"));
            else if (slot == 4) {
                var hopper = item("sophisticatedstorage:hopper_upgrade");
                hopper.getOrCreateTag().putString("owner_filter_payload", "keep");
                grid.setItem(slot, hopper);
            } else {
                var alternatives = ingredient.getItems();
                helper.assertTrue(alternatives.length != 0, "Native hopper ingredient missing at " + slot);
                grid.setItem(slot, alternatives[0].copy());
            }
        }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native hopper plus paid handle rejected");
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue("keep".equals(result.getTag().getString("owner_filter_payload"))
            && SettingsNbt.read(result.getTag(), "sophisticatedstorage:advanced_hopper_upgrade") != null,
            "Predecessor filter metadata or new physical-part setting lost");
        grid.setItem(0, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Advanced hopper upgraded without paid handle");
        helper.succeed();
    }
}
