package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** The pinned native brass gate remains; the added plate is a construction cost, not a made-up flow rating. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateSmartFluidPipeTests {
    private Wave2CreateSmartFluidPipeTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void smartPipeKeepsBrassAndNativeInputsWhileConsumingPlate(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create and TCon required for smart-pipe construction");
        var recipe = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("create:crafting/kinetics/smart_fluid_pipe"))
            .orElseThrow(() -> new AssertionError("Missing effective native-ID smart-pipe recipe"));
        helper.assertTrue(recipe instanceof ShapedRecipe && !(recipe instanceof PartVariantRecipe),
            "Smart pipe must remain a construction-only native shaped recipe");
        var shaped = (ShapedRecipe) recipe;
        helper.assertTrue(shaped.getWidth() == 2 && shaped.getHeight() == 3,
            "Added plate did not retain the brass/pipe/electron native progression shape");
        var grid = CreateDrillTests.grid();
        for (int i = 0; i < shaped.getIngredients().size(); i++) {
            if (shaped.getIngredients().get(i).isEmpty()) continue;
            var candidates = shaped.getIngredients().get(i).getItems();
            helper.assertTrue(candidates.length > 0, "Unobtainable native smart-pipe input at " + i);
            grid.setItem(i % shaped.getWidth() + i / shaped.getWidth() * 3, candidates[0].copy());
        }
        var largePlate = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:large_plate"));
        helper.assertTrue(largePlate instanceof ToolPartItem && grid.getItem(4).is(largePlate),
            "The added physical TCon plate is missing from the loaded recipe");
        grid.setItem(4, ((ToolPartItem) largePlate).withMaterial(MaterialVariantId.parse("tconstruct:rock#stone")));
        var brass = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:brass_sheet"));
        var fluidPipe = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:fluid_pipe"));
        var electronTube = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:electron_tube"));
        helper.assertTrue(grid.getItem(0).is(brass) && grid.getItem(3).is(fluidPipe) && grid.getItem(6).is(electronTube),
            "Native brass, pipe or electron-tube gate was removed");
        helper.assertTrue(shaped.matches(grid, helper.getLevel()), "Real part-bearing smart-pipe craft rejected");
        var result = shaped.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:smart_fluid_pipe")))
            && result.getCount() == 1 && SettingsNbt.read(result.getTag(), "create:smart_fluid_pipe") == null,
            "Construction-only pipe changed native output or fabricated performance settings");
        grid.setItem(4, ItemStack.EMPTY);
        helper.assertTrue(!shaped.matches(grid, helper.getLevel()), "Part-free smart-pipe route remained under Manufacturing");
        helper.succeed();
    }
}
