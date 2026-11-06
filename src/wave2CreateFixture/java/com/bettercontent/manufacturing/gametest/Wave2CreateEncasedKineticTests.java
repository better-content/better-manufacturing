package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Encasing only changes the native shell; part consumption belongs to the source shaft/cog craft. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateEncasedKineticTests {
    private Wave2CreateEncasedKineticTests() {}

    private static ItemStack source(GameTestHelper helper, String id) {
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("create:crafting/kinetics/" + id)).orElseThrow();
        helper.assertTrue(found instanceof CraftingRecipe, "Native source recipe missing: " + id);
        var recipe = (CraftingRecipe) found;
        var grid = CreateDrillTests.grid();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        boolean consumedPart = false;
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            var choices = ingredient.getItems();
            helper.assertTrue(choices.length > 0, "Missing physical native source ingredient: " + id + "/" + i);
            var stack = choices[0].copy();
            if (stack.getItem() instanceof ToolPartItem part) {
                consumedPart = true;
                stack = part.withMaterial(MaterialVariantId.parse("tconstruct:iron"));
            }
            grid.setItem(i % width + i / width * 3, stack);
        }
        helper.assertTrue(consumedPart && recipe.matches(grid, helper.getLevel()),
            "Source shaft/cog recipe lost its physical TCon part: " + id);
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!result.isEmpty() && !result.hasTag(),
            "Construction-only shaft/cog minted numerical settings or missing output: " + id);
        return result;
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 90)
    public static void sixNativeCasingsReusePartBuiltShaftsAndCogsWithoutInventingRatings(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Native Create and TCon required for casing construction");
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var pos = new BlockPos(2, 2, 2);
        var absolute = helper.absolutePos(pos);
        for (String source : new String[]{"shaft", "cogwheel", "large_cogwheel"}) {
            var constructed = source(helper, source);
            for (String tier : new String[]{"andesite", "brass"}) {
                helper.setBlock(pos, Blocks.AIR);
                var item = constructed.copy(); item.setCount(1);
                helper.assertTrue(((BlockItem) item.getItem()).place(new DirectionalPlaceContext(
                    helper.getLevel(), absolute, Direction.WEST, item, Direction.UP)).consumesAction(),
                    "Part-built base could not be natively placed: " + source);
                var base = helper.getLevel().getBlockState(absolute);
                var casing = tier.equals("andesite") ? AllBlocks.ANDESITE_CASING.asStack() : AllBlocks.BRASS_CASING.asStack();
                player.setItemInHand(InteractionHand.MAIN_HAND, casing.copy());
                var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
                helper.assertTrue(base.getBlock().use(base, helper.getLevel(), absolute, player,
                    InteractionHand.MAIN_HAND, hit).consumesAction(), "Native casing use failed: " + tier + "/" + source);
                String output = "create:" + tier + "_encased_" + source;
                var encased = helper.getLevel().getBlockState(absolute);
                helper.assertTrue(encased.is(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(output))),
                    "Native casing did not produce requested selected block: " + output);
                var drops = Block.getDrops(encased, helper.getLevel(), absolute, helper.getLevel().getBlockEntity(absolute));
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(constructed.getItem())
                    && !drops.get(0).hasTag() && SettingsNbt.read(drops.get(0).getTag(), "create:" + source) == null,
                    "Native cased block lost its part-built base identity or invented a numerical rating: " + output);
                helper.setBlock(pos, Blocks.AIR);
                helper.assertTrue(((BlockItem) drops.get(0).getItem()).place(new DirectionalPlaceContext(
                    helper.getLevel(), absolute, Direction.WEST, drops.get(0).copy(), Direction.UP)).consumesAction()
                    && helper.getLevel().getBlockState(absolute).is(((BlockItem) constructed.getItem()).getBlock()),
                    "Native base drop could not be re-placed after casing: " + output);
            }
        }
        helper.succeed();
    }
}
