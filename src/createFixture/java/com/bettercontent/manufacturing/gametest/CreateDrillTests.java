package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.create.*;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.drill.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class CreateDrillTests {
    static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    static ItemStack head(String material) {
        return ((ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:pick_head"))).withMaterial(new MaterialId(material));
    }
    static ItemStack craft(GameTestHelper helper, String material) {
        var recipe = (PartVariantRecipe) helper.getLevel().getRecipeManager().byKey(new ResourceLocation("create:crafting/kinetics/mechanical_drill")).orElseThrow();
        var grid = grid();
        for (int slot : new int[]{1,3,5}) grid.setItem(slot, AllItems.ANDESITE_ALLOY.asStack());
        grid.setItem(7, AllBlocks.ANDESITE_CASING.asStack()); grid.setItem(4, head(material));
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!result.isEmpty(), "Part-based drill craft failed"); return result;
    }
    static DrillBlockEntity placed(GameTestHelper helper, BlockPos relative, ItemStack source) {
        var result = ((BlockItem) source.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), helper.absolutePos(relative), Direction.WEST, source.copy(), Direction.UP));
        helper.assertTrue(result.consumesAction(), "Native placement failed");
        return (DrillBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }
    @GameTest(template = "drill_workshop")
    public static void drillCraftPlaceSaveDropAndSchematic(GameTestHelper helper) {
        var crafted = craft(helper, "tconstruct:iron"); var block = placed(helper, new BlockPos(2,1,2), crafted);
        var behaviour = CreateManufacturing.behaviour(block);
        var loaded = new DrillBlockEntity(block.getType(), block.getBlockPos(), block.getBlockState());
        loaded.setLevel(helper.getLevel()); loaded.load(block.saveWithFullMetadata());
        helper.assertTrue(CreateManufacturing.behaviour(loaded).record().equals(behaviour.record()), "Save/load lost settings");
        helper.assertTrue(block.writeClient(new CompoundTag()).get(SettingsNbt.KEY).equals(crafted.getTag().get(SettingsNbt.KEY)), "Packet lost settings");
        var drops = Block.getDrops(block.getBlockState(), helper.getLevel(), block.getBlockPos(), block);
        helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(crafted, drops.get(0)), "Drop lost settings");
        var requirement = block.getRequiredItems(block.getBlockState()).getRequiredItems().get(0);
        helper.assertTrue(requirement.matches(crafted) && !requirement.matches(AllBlocks.MECHANICAL_DRILL.asStack()), "Schematic changed the variant");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "variant_wrench"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        var context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
            player.getMainHandItem(), new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(block.getBlockPos()), Direction.UP, block.getBlockPos(), false));
        ((com.simibubi.create.content.equipment.wrench.IWrenchable) block.getBlockState().getBlock()).onSneakWrenched(block.getBlockState(), context);
        helper.assertTrue(player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameTags(stack, crafted)).mapToInt(ItemStack::getCount).sum() == 1, "Wrench lost variant");
        helper.succeed();
    }
    @GameTest(template = "drill_workshop", timeoutTicks = 180)
    public static void drillNativeOperationAndAccounting(GameTestHelper helper) {
        long[] finished = {-1,-1,-1}; var blocks = new DrillBlockEntity[3];
        for (int i = 0; i < 3; i++) {
            // Pack wood has no head; pinned slimewood head speed 4 vs iron 6
            // supplies a real 0.75-clamped cutting contrast with native iron-tier drops.
            int z = 1 + 3*i; var item = craft(helper, i == 0 ? "tconstruct:slimewood" : "tconstruct:iron");
            if (i == 2) item.getTag().getCompound(SettingsNbt.KEY).putInt("version", 999);
            helper.setBlock(3,1,z, AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING, Direction.EAST));
            blocks[i] = (DrillBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3,1,z)));
            CreateManufacturing.install(helper.getLevel(), blocks[i].getBlockPos(), item);
            helper.setBlock(4,1,z, Blocks.STONE);
            helper.setBlock(2,1,z, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
            ((CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2,1,z)))).generatedSpeed.setValue(64);
        }
        helper.assertTrue(Math.abs(blocks[0].calculateStressApplied()/blocks[1].calculateStressApplied()-.75)<.001, "Stress did not match work rate");
        helper.onEachTick(() -> { for (int i=0;i<3;i++) if (finished[i]<0 && helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(4,1,1+3*i))).isAir()) finished[i]=helper.getTick(); });
        helper.succeedWhen(() -> {
            helper.assertTrue(finished[0]>finished[1] && finished[1]>0 && finished[2]>0, "Variant rates/default fallback did not operate");
            for (int i=0;i<3;i++) helper.assertItemEntityCountIs(Items.COBBLESTONE,new BlockPos(4,1,1+3*i),2,1);
        });
    }
    @GameTest(template = "drill_workshop")
    public static void normalCraftingConsumesPartsWithoutRetainingThem(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "variant_craft"));
        var pos = helper.absolutePos(new BlockPos(3,1,3)); helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(),pos));
        for(int slot:new int[]{1,3,5}) menu.getSlot(slot+1).set(AllItems.ANDESITE_ALLOY.asStack());
        menu.getSlot(8).set(AllBlocks.ANDESITE_CASING.asStack());
        var part = head("tconstruct:iron"); part.setCount(2); part.getOrCreateTag().putInt("BetterContentJackhammerWear",7);
        menu.getSlot(5).set(part);
        var preview = menu.getSlot(0).getItem().copy(); var result = menu.getSlot(0).remove(1);
        helper.assertTrue(!result.isEmpty() && ItemStack.isSameItemSameTags(preview,result), "Preview differs from craft");
        menu.getSlot(0).onTake(player,result);
        helper.assertTrue(menu.getSlot(5).getItem().getCount()==1 && menu.getSlot(8).getItem().isEmpty(), "Craft consumed wrong quantities");
        helper.assertTrue(result.getTag().getAllKeys().equals(java.util.Set.of(SettingsNbt.KEY)) && !result.getTag().toString().contains("Wear"), "Craft retained original part state");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation("better_manufacturing:retool_drill_head")).isEmpty(), "Retooling recipe remains active");
        helper.succeed();
    }
    @GameTest(template = "drill_workshop")
    public static void movingDrillUsesBakedTier(GameTestHelper helper) {
        var item = craft(helper,"tconstruct:slimewood"); var state = AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING,Direction.EAST);
        var context = new MovementContext(helper.getLevel(),new StructureTemplate.StructureBlockInfo(BlockPos.ZERO,state,item.getTag().copy()),new MountedContraption());
        var actor = new DrillMovementBehaviour(); actor.startMoving(context);
        var target = helper.absolutePos(new BlockPos(5,1,3)); helper.getLevel().setBlockAndUpdate(target,Blocks.OBSIDIAN.defaultBlockState()); actor.visitNewPosition(context,target);
        helper.assertTrue(!context.data.contains("BreakingPos"),"Moving cutter ignored baked tier");
        helper.getLevel().setBlockAndUpdate(target,Blocks.STONE.defaultBlockState()); actor.visitNewPosition(context,target);
        helper.assertTrue(context.data.contains("BreakingPos"),"Moving cutter could not work"); helper.succeed();
    }
}
