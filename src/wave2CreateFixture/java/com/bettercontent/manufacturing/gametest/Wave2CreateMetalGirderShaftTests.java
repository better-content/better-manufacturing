package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

/** Metal-girder casing is a distinct paid interaction, not a craftable standalone encased shaft. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateMetalGirderShaftTests {
    private Wave2CreateMetalGirderShaftTests() {}

    @GameTest(template = "drill_workshop", timeoutTicks = 90)
    public static void nativeGirderConsumesOneAndReturnsPartBuiltBaseOnBreak(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Native Create and TCon required for girded shaft");
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("create:crafting/kinetics/shaft")).orElseThrow();
        helper.assertTrue(found instanceof ShapedRecipe, "Effective part-bearing source shaft recipe missing");
        var recipe = (ShapedRecipe) found;
        var grid = CreateDrillTests.grid();
        boolean consumedPart = false;
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            var choices = ingredient.getItems();
            helper.assertTrue(choices.length > 0, "Unobtainable physical shaft ingredient");
            var stack = choices[0].copy();
            if (stack.getItem() instanceof ToolPartItem part) {
                consumedPart = true;
                stack = part.withMaterial(MaterialVariantId.parse("tconstruct:iron"));
            }
            grid.setItem(i % recipe.getWidth() + i / recipe.getWidth() * 3, stack);
        }
        helper.assertTrue(consumedPart && recipe.matches(grid, helper.getLevel()),
            "Production shaft no longer physically consumes a TCon part");
        var shafts = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(shafts.is(AllBlocks.SHAFT.asItem()) && shafts.getCount() == 8 && !shafts.hasTag(),
            "Part-built source shaft changed native yield or minted fake settings");
        var pos = new BlockPos(2, 2, 2);
        var absolute = helper.absolutePos(pos);
        var shaft = shafts.copy(); shaft.setCount(1);
        helper.assertTrue(((BlockItem) shaft.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), absolute, Direction.WEST, shaft, Direction.UP)).consumesAction(),
            "Native part-built shaft could not be placed");
        var original = helper.getLevel().getBlockState(absolute);
        helper.assertTrue(original.is(AllBlocks.SHAFT.get()), "Native part-built shaft placement changed its block");
        // The fixture's placement context picks a vertical shaft; turn that SAME placed shaft
        // horizontal before exercising Create's native girder-only-horizontal interaction.
        if (original.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
            helper.setBlock(pos, original.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            original = helper.getLevel().getBlockState(absolute);
        }
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "girder_fixture"));
        helper.assertTrue(!player.isCreative(), "Paid girder check requires a survival player");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.METAL_GIRDER.asItem(), 2));
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        helper.assertTrue(((ShaftBlock) original.getBlock()).use(original, helper.getLevel(), absolute,
            player, InteractionHand.MAIN_HAND, hit).consumesAction(), "Native girder-on-shaft use failed");
        var encased = helper.getLevel().getBlockState(absolute);
        helper.assertTrue(encased.is(AllBlocks.METAL_GIRDER_ENCASED_SHAFT.get())
            && player.getItemInHand(InteractionHand.MAIN_HAND).is(AllBlocks.METAL_GIRDER.asItem())
            && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
            "Girded shaft changed native block identity or failed to spend exactly one girder");
        var drops = Block.getDrops(encased, helper.getLevel(), absolute, helper.getLevel().getBlockEntity(absolute));
        helper.assertTrue(drops.size() == 2 && drops.stream().anyMatch(drop -> drop.is(AllBlocks.METAL_GIRDER.asItem()))
            && drops.stream().anyMatch(drop -> drop.is(AllBlocks.SHAFT.asItem()))
            && drops.stream().allMatch(drop -> drop.getCount() == 1 && !drop.hasTag()
                && SettingsNbt.read(drop.getTag(), "create:metal_girder_encased_shaft") == null),
            "Native girder break lost its physical shaft/girder costs or invented a numeric rating");
        helper.setBlock(pos, Blocks.AIR);
        var recovered = drops.stream().filter(drop -> drop.is(AllBlocks.SHAFT.asItem())).findFirst().orElseThrow();
        helper.assertTrue(((BlockItem) recovered.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), absolute, Direction.WEST, recovered.copy(), Direction.UP)).consumesAction()
            && helper.getLevel().getBlockState(absolute).is(AllBlocks.SHAFT.get()),
            "Recovered native part-built shaft could not be re-placed");
        helper.succeed();
    }
}
