package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsBitTests {
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    @GameTest(template = "pneu_workshop")
    public static void nativeBitsFinishOnceAndCannotRequalify(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Native PNCR/TCon missing");
        for (String grade : new String[]{"iron", "compressed_iron", "diamond", "netherite"}) {
            String id = "pneumaticcraft:drill_bit_" + grade;
            var actual = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("better_manufacturing:pneumatictools_drill_bit_" + grade)).orElseThrow();
            helper.assertTrue(actual instanceof PartVariantRecipe, "Real native bit finishing recipe absent: " + grade);
            var recipe = (PartVariantRecipe) actual;
            var bitItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            var headItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:pick_head"));
            helper.assertTrue(bitItem != null && headItem instanceof ToolPartItem, "Native bit/head absent");
            var input = grid(); input.setItem(3, new ItemStack(bitItem));
            input.setItem(0, ((ToolPartItem) headItem).withMaterial(MaterialVariantId.parse("tconstruct:iron")));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Native gated unconfigured bit rejected on first finishing: " + grade);
            var first = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(first.is(bitItem) && SettingsNbt.read(first.getTag(), id) != null
                && ItemStack.isSameItemSameTags(first, recipe.assemble(input, helper.getLevel().registryAccess()))
                && recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                "Finishing lost native bit grade, preview identity or consumed pick head: " + grade);
            input.setItem(3, first);
            input.setItem(0, ((ToolPartItem) headItem).withMaterial(MaterialVariantId.parse("tconstruct:wood#oak")));
            helper.assertTrue(!recipe.matches(input, helper.getLevel()) && recipe.assemble(input, helper.getLevel().registryAccess()).isEmpty(),
                "Previously configured bit retooled through finishing recipe: " + grade);
            var invalid = first.copy(); invalid.getOrCreateTag().getCompound(SettingsNbt.KEY).putString("output", "minecraft:stone");
            input.setItem(3, invalid);
            helper.assertTrue(recipe.assemble(input, helper.getLevel().registryAccess()).isEmpty(),
                "Malformed settings-bearing bit laundered into a new material: " + grade);
        }
        helper.succeed();
    }
}
