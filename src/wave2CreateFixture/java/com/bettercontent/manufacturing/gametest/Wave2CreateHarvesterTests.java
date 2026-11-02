package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.simibubi.create.AllBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Required only in the separate real-Bumblezone profile: never an absent-mod success. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateHarvesterTests {
    private Wave2CreateHarvesterTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void harvesterCraftIsOrdinaryNativeConstruction(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct")
                && ModList.get().isLoaded("the_bumblezone"),
            "Real Create/TConstruct/Bumblezone dependencies are required");
        var recipe = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("kubejs:fonts/bumblezone/mechanical_harvester"))
            .orElseThrow(() -> new AssertionError("Missing actual effective harvester acquisition root"));
        helper.assertTrue(recipe instanceof ShapedRecipe && !(recipe instanceof PartVariantRecipe),
            "Construction-only harvester still advertises inert configured work");
        var shaped = (ShapedRecipe) recipe;
        var grid = CreateDrillTests.grid();
        for (int i = 0; i < shaped.getIngredients().size(); i++) {
            if (shaped.getIngredients().get(i).isEmpty()) continue;
            var candidates = shaped.getIngredients().get(i).getItems();
            helper.assertTrue(candidates.length > 0, "Harvester progression prerequisite absent at ingredient " + i);
            grid.setItem(i % shaped.getWidth() + i / shaped.getWidth() * 3, candidates[0].copy());
        }
        var blade = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:small_blade"));
        helper.assertTrue(blade instanceof ToolPartItem && grid.getItem(1).is(blade),
            "Harvester lost its consumed native small blade");
        grid.setItem(1, ((ToolPartItem) blade).withMaterial(MaterialVariantId.parse("tconstruct:wood#oak")));
        var result = shaped.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(AllBlocks.MECHANICAL_HARVESTER.asItem())
                && SettingsNbt.read(result.getTag(), "create:mechanical_harvester") == null,
            "Ordinary harvester craft still produces inert settings");
        helper.assertTrue(shaped.matches(grid, helper.getLevel()), "Real cheap-material acquisition failed");
        helper.succeed();
    }
}
