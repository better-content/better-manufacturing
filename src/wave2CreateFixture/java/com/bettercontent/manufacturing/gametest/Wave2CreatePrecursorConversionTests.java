package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Native one-input conversions depend on part-built bases, without inventing a saved rating. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreatePrecursorConversionTests {
    private Wave2CreatePrecursorConversionTests() {}

    private static ItemStack base(GameTestHelper helper, String id, int count) {
        var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("create:crafting/"
            + (id.equals("packager") ? "logistics/" : "kinetics/") + id)).orElseThrow();
        helper.assertTrue(found instanceof ShapedRecipe, "Effective part-bearing source recipe missing: " + id);
        var recipe = (ShapedRecipe) found;
        var grid = CreateDrillTests.grid();
        boolean consumedPart = false;
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            var choices = ingredient.getItems();
            helper.assertTrue(choices.length > 0, "Native base has an uncraftable ingredient: " + id);
            var stack = choices[0].copy();
            if (stack.getItem() instanceof ToolPartItem part) {
                consumedPart = true;
                stack = part.withMaterial(MaterialVariantId.parse("tconstruct:iron"));
            }
            grid.setItem(i % recipe.getWidth() + i / recipe.getWidth() * 3, stack);
        }
        helper.assertTrue(consumedPart && recipe.matches(grid, helper.getLevel()),
            "Effective native base recipe no longer consumes its physical TCon part: " + id);
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:" + id)))
            && result.getCount() == count && !result.hasTag(),
            "Native precursor count/identity or construction-only tag changed: " + id);
        return result;
    }

    private static ItemStack convert(GameTestHelper helper, String recipeId, ItemStack ingredient,
                                     String expectedOutput) {
        var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(recipeId)).orElseThrow();
        helper.assertTrue(found instanceof ShapelessRecipe, "Native one-item conversion recipe missing: " + recipeId);
        var recipe = (CraftingRecipe) found;
        var grid = CreateDrillTests.grid();
        var input = ingredient.copy(); input.setCount(1);
        var incidental = new CompoundTag(); incidental.putString("Unrelated", "keep source only");
        input.setTag(incidental);
        grid.setItem(0, input);
        helper.assertTrue(recipe.getIngredients().size() == 1 && recipe.matches(grid, helper.getLevel()),
            "Native conversion changed its one-precursor cost: " + recipeId);
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation(expectedOutput)))
            && result.getCount() == 1 && !result.hasTag()
            && "keep source only".equals(grid.getItem(0).getTag().getString("Unrelated"))
            && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
            "Native conversion output, residual cost or vanilla unrelated-tag behavior changed: " + recipeId);
        return result;
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 90)
    public static void nativeRepackagerAndSecondaryChassisOnlyConvertPartBuiltBases(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Native Create and TCon required");
        var packager = base(helper, "packager", 1);
        var chassis = base(helper, "linear_chassis", 3);
        var repackager = convert(helper, "create:crafting/logistics/repackager_from_conversion",
            packager, "create:repackager");
        convert(helper, "create:crafting/logistics/packager_from_conversion", repackager, "create:packager");
        var secondary = convert(helper, "create:crafting/kinetics/secondary_linear_chassis_from_conversion",
            chassis, "create:secondary_linear_chassis");
        convert(helper, "create:crafting/kinetics/linear_chassis_from_conversion", secondary,
            "create:linear_chassis");
        helper.succeed();
    }
}
