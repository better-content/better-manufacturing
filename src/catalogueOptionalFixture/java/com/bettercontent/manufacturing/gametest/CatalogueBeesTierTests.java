package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
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

/** Crafting-table tier upgrades keep native costs and predecessor NBT; only the new parts set the rate. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class CatalogueBeesTierTests {
    private CatalogueBeesTierTests() {}

    private static ItemStack item(String id) {
        var value = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (value == null) throw new AssertionError("Native Bees item missing: " + id);
        return new ItemStack(value);
    }

    private static ItemStack part(String id, String material) {
        var value = item(id).getItem();
        if (!(value instanceof ToolPartItem tool)) throw new AssertionError("Missing Bees physical part " + id);
        return tool.withMaterial(MaterialVariantId.parse(material));
    }

    private static ItemStack paidPanel(GameTestHelper helper, String stem) {
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("better_manufacturing:prepared_bees_cost_" + stem)).orElseThrow();
        helper.assertTrue(found instanceof ShapedRecipe, "Native-cost Bees panel preparation missing: " + stem);
        var prep = (ShapedRecipe) found;
        var grid = CreateDrillTests.grid();
        for (int slot = 0; slot < prep.getIngredients().size(); slot++) {
            var ingredient = prep.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            var samples = ingredient.getItems();
            helper.assertTrue(samples.length > 0, "Bees native ingredient cannot be resolved: " + stem + " / " + slot);
            grid.setItem(slot % prep.getWidth() + slot / prep.getWidth() * 3, samples[0].copy());
        }
        helper.assertTrue(prep.matches(grid, helper.getLevel()), "Bees full native input bill did not match");
        var marked = prep.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(marked.is(item("complicated_bees:gyrofuge_panel").getItem()) && marked.hasTag(),
            "Cost preparation erased the original panel or its route marker");
        return marked;
    }

    private static PartVariantRecipe tier(GameTestHelper helper, String stem) {
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("complicated_bees:" + stem)).orElseThrow();
        helper.assertTrue(found instanceof PartVariantRecipe, "Native Bees tier recipe not configured: " + stem);
        return (PartVariantRecipe) found;
    }

    private static ItemStack craft(GameTestHelper helper, String stem, ItemStack predecessor, String handleMaterial) {
        var recipe = tier(helper, stem);
        var grid = CreateDrillTests.grid();
        grid.setItem(0, part("tconstruct:tough_handle", handleMaterial));
        grid.setItem(1, paidPanel(helper, stem));
        grid.setItem(2, part("tconstruct:large_plate", "tconstruct:iron"));
        grid.setItem(4, predecessor);
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Paid Bees predecessor/working-part craft rejected: " + stem);
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(item("complicated_bees:" + stem).getItem())
            && SettingsNbt.read(result.getTag(), "complicated_bees:" + stem) != null,
            "Bees tier craft lost physical part rating: " + stem);
        grid.setItem(2, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Part-free Bees tier craft: " + stem);
        return result;
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void gyrofugeUpgradeRetainsNativeMetadataAndRejectsBadPredecessor(GameTestHelper helper) {
        var base = item("complicated_bees:gyrofuge_base");
        base.getOrCreateTag().putString("bees_native_configuration", "retain");
        var basic = craft(helper, "gyrofuge_basic_speed_unit", base, "tconstruct:iron");
        helper.assertTrue("retain".equals(basic.getTag().getString("bees_native_configuration")),
            "Native gyrofuge configuration lost on basic upgrade");
        var next = craft(helper, "gyrofuge_speed_unit", basic, "tconstruct:slimewood");
        helper.assertTrue("retain".equals(next.getTag().getString("bees_native_configuration"))
            && SettingsNbt.read(next.getTag(), "complicated_bees:gyrofuge_speed_unit") != null,
            "Tier upgrade lost native predecessor NBT or new material rating");
        var broken = basic.copy();
        broken.getOrCreateTag().getCompound(SettingsNbt.KEY).putString("output", "minecraft:stone");
        var grid = CreateDrillTests.grid();
        grid.setItem(0, part("tconstruct:tough_handle", "tconstruct:iron"));
        grid.setItem(1, paidPanel(helper, "gyrofuge_speed_unit"));
        grid.setItem(2, part("tconstruct:large_plate", "tconstruct:iron"));
        grid.setItem(4, broken);
        helper.assertTrue(!tier(helper, "gyrofuge_speed_unit").matches(grid, helper.getLevel()),
            "Malformed predecessor was laundered into a new rated unit");
        helper.succeed();
    }
}
