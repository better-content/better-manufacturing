package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** The actual effective mixer root must consume and inherit its separately crafted native whisk. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateWhiskTests {
    private Wave2CreateWhiskTests() {}

    private static PartVariantRecipe required(GameTestHelper helper, String id) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(id))
            .orElseThrow(() -> new AssertionError("Missing effective production recipe " + id));
        helper.assertTrue(recipe instanceof PartVariantRecipe, id + " lost its configured native component binding");
        return (PartVariantRecipe) recipe;
    }

    private static CraftingContainer grid(PartVariantRecipe recipe) {
        CraftingContainer grid = new TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            var candidates = ingredient.getItems();
            if (candidates.length == 0) throw new AssertionError("Missing production ingredient for " + recipe.getId() + " at " + i);
            grid.setItem(i % recipe.getWidth() + i / recipe.getWidth() * 3, candidates[0].copy());
        }
        return grid;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void effectiveMixerInheritsConsumedWhiskWithoutExtraHandle(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create and TConstruct dependencies are required");
        var whiskRecipe = required(helper, "create:crafting/kinetics/whisk");
        var mixerRecipe = required(helper, "kubejs:powered_works/mechanical_mixer");
        helper.assertTrue(whiskRecipe.parts().size() == 1 && whiskRecipe.parts().get(0).slot() == 1,
            "Whisk's native handle role changed");
        helper.assertTrue(mixerRecipe.parts().size() == 1 && mixerRecipe.parts().get(0).slot() == 2,
            "Mixer must bind the consumed whisk, not a new handle");
        var handle = (ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tool_handle"));
        helper.assertTrue(handle != null, "Missing real TConstruct handle");
        double[] rates = new double[2];
        for (int choice = 0; choice < 2; choice++) {
            var whiskGrid = grid(whiskRecipe);
            whiskGrid.setItem(1, handle.withMaterial(MaterialVariantId.parse(choice == 0 ? "tconstruct:wood#oak" : "tconstruct:iron")));
            var whisk = whiskRecipe.assemble(whiskGrid, helper.getLevel().registryAccess());
            var whiskSettings = SettingsNbt.read(whisk.getTag(), "create:whisk");
            helper.assertTrue(whiskSettings != null, "Configured native whisk did not craft");
            var mixerGrid = grid(mixerRecipe);
            helper.assertTrue(mixerGrid.getItem(6).is(whisk.getItem()), "Effective mixer lost its whisk ingredient");
            mixerGrid.setItem(6, whisk.copy()); // 1x3 recipe, bound local slot 2 is grid slot 6.
            var mixer = mixerRecipe.assemble(mixerGrid, helper.getLevel().registryAccess());
            var mixerSettings = SettingsNbt.read(mixer.getTag(), "create:mechanical_mixer");
            helper.assertTrue(mixerSettings != null && mixerSettings.rate() == whiskSettings.rate(),
                "Mixer failed to inherit its consumed whisk's fixed rating");
            rates[choice] = mixerSettings.rate();
            helper.assertTrue(mixerSettings.materials().containsValue(choice == 0 ? "tconstruct:wood#oak" : "tconstruct:iron"),
                "Mixer lost the configured native component label");
            // A native unconfigured predecessor stays usable as the ordinary baseline.
            mixerGrid.setItem(6, new ItemStack(whisk.getItem()));
            var plain = SettingsNbt.read(mixerRecipe.assemble(mixerGrid, helper.getLevel().registryAccess()).getTag(), "create:mechanical_mixer");
            helper.assertTrue(plain != null && plain.rate() == 1, "Native baseline whisk unexpectedly requires Manufacturing NBT");
        }
        helper.assertTrue(rates[0] < rates[1] && rates[1] == 1, "Cheap real pack material had no distinct inherited rating");
        helper.succeed();
    }
}
