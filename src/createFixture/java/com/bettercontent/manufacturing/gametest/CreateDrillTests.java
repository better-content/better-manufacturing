package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.*;
import com.bettercontent.manufacturing.integration.create.*;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.recipe.*;
import com.simibubi.create.AllBlocks;
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
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static ItemStack head(String material) {
        return ((ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:pick_head"))).withMaterial(new MaterialId(material));
    }
    private static ItemStack craft(GameTestHelper helper, String material) {
        var recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("create:crafting/kinetics/mechanical_drill")).orElseThrow();
        helper.assertTrue(recipe instanceof MaterialShapedRecipe, "Authored conditional recipe did not select the manufacturing serializer");
        var grid = grid();
        grid.setItem(1, com.simibubi.create.AllItems.ANDESITE_ALLOY.asStack());
        grid.setItem(3, com.simibubi.create.AllItems.ANDESITE_ALLOY.asStack());
        grid.setItem(5, com.simibubi.create.AllItems.ANDESITE_ALLOY.asStack());
        grid.setItem(7, AllBlocks.ANDESITE_CASING.asStack()); grid.setItem(4, head(material));
        var shaped = (MaterialShapedRecipe) recipe;
        helper.assertTrue(shaped.matches(grid, helper.getLevel()), "Reachable native components did not match");
        var result = shaped.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!result.isEmpty(), "Assembly returned no drill"); return result;
    }
    private static DrillBlockEntity placed(GameTestHelper helper, BlockPos relative, ItemStack source) {
        var result = ((BlockItem) source.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), helper.absolutePos(relative), Direction.WEST, source.copy(), Direction.UP));
        helper.assertTrue(result.consumesAction(), "Native BlockItem placement failed");
        var blockEntity = (DrillBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(CreateManufacturing.behaviour(blockEntity) != null, "Native Create behaviour was not attached");
        return blockEntity;
    }
    @GameTest(template = "drill_workshop")
    public static void drillCraftPlaceSaveDropAndSchematic(GameTestHelper helper) {
        var crafted = craft(helper, "tconstruct:iron");
        var placed = placed(helper, new BlockPos(2, 1, 2), crafted);
        var behaviour = CreateManufacturing.behaviour(placed);
        helper.assertTrue(behaviour.state().allowed() && behaviour.state().rate() == 1, "Native placement lost the iron reference build");
        CompoundTag saved = placed.saveWithFullMetadata();
        var loaded = new DrillBlockEntity(placed.getType(), placed.getBlockPos(), placed.getBlockState());
        loaded.setLevel(helper.getLevel()); loaded.load(saved);
        helper.assertTrue(CreateManufacturing.behaviour(loaded).record().equals(behaviour.record()), "Native BE save/load lost provenance");
        CompoundTag packet = placed.writeClient(new CompoundTag());
        helper.assertTrue(packet.get(BuildNbt.KEY).equals(crafted.getTag().get(BuildNbt.KEY)), "Client update packet omitted the build");
        // Native readClient and goggles rendering require a real client; do not fake that acceptance here.
        var drops = Block.getDrops(placed.getBlockState(), helper.getLevel(), placed.getBlockPos(), placed);
        helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(crafted, drops.get(0)), "Native loot did not return the attributable machine");
        var requirements = placed.getRequiredItems(placed.getBlockState());
        helper.assertTrue(!requirements.isInvalid() && requirements.getRequiredItems().size() == 1, "Schematic item accounting changed");
        helper.assertTrue(requirements.getRequiredItems().get(0).matches(crafted)
            && !requirements.getRequiredItems().get(0).matches(AllBlocks.MECHANICAL_DRILL.asStack()), "Schematic can fabricate upgraded heads from stock machines");
        var occupied = crafted.copy(); occupied.getOrCreateTag().putString("BlockEntityTag", "occupied sentinel");
        helper.assertTrue(!requirements.getRequiredItems().get(0).matches(occupied), "Schematic would consume unreviewed stored contents");
        CompoundTag safe = new CompoundTag(); placed.writeSafe(safe);
        helper.assertTrue(safe.get(BuildNbt.KEY).equals(crafted.getTag().get(BuildNbt.KEY)), "Schematic-safe persistence lost provenance");
        var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>(); CreateManufacturing.goggles(placed, lines);
        helper.assertTrue(lines.stream().anyMatch(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
            && text.getKey().equals("better_manufacturing.drill.head") && java.util.Arrays.asList(text.getArgs()).contains("tconstruct:iron")), "Installed head missing from goggles explanation");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "drill_wrench"));
        player.setPos(placed.getBlockPos().getX(), placed.getBlockPos().getY(), placed.getBlockPos().getZ());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
        var context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
            player.getMainHandItem(), new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(placed.getBlockPos()), Direction.UP, placed.getBlockPos(), false));
        ((com.simibubi.create.content.equipment.wrench.IWrenchable) placed.getBlockState().getBlock()).onSneakWrenched(placed.getBlockState(), context);
        helper.assertTrue(helper.getLevel().getBlockState(placed.getBlockPos()).isAir(), "Native wrench did not remove drill");
        helper.assertTrue(player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameTags(stack, crafted)).mapToInt(ItemStack::getCount).sum() == 1,
            "Native wrench lost or duplicated the material-bearing machine");
        helper.succeed();
    }
    @GameTest(template = "drill_workshop", timeoutTicks = 180)
    public static void drillNativeOperationAndAccounting(GameTestHelper helper) {
        long[] finished = {-1, -1};
        var entities = new DrillBlockEntity[3];
        for (int i = 0; i < 3; i++) {
            int z = 1 + 3 * i;
            for (int x = 1; x < 7; x++) helper.setBlock(x, 0, z, Blocks.STONE);
            var item = craft(helper, i == 0 ? "tconstruct:wood" : "tconstruct:iron");
            if (i == 2) item.getTag().getCompound(BuildNbt.KEY).getCompound("components").getCompound("head").putString("material", "better_manufacturing:unknown");
            helper.setBlock(3, 1, z, AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING, Direction.EAST));
            entities[i] = (DrillBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, z)));
            CreateManufacturing.install(helper.getLevel(), entities[i].getBlockPos(), item);
            helper.setBlock(4, 1, z, Blocks.STONE);
            helper.setBlock(2, 1, z, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
            ((CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, z)))).generatedSpeed.setValue(64);
        }
        helper.assertTrue(Math.abs(entities[0].calculateStressApplied() / entities[1].calculateStressApplied() - .75) < .001, "Cutting change did not carry matching native stress cost");
        helper.onEachTick(() -> {
            for (int i = 0; i < 2; i++) if (finished[i] < 0 && helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(4, 1, 1 + 3 * i))).isAir()) finished[i] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(finished[0] > 0 && finished[1] > 0, "Drills have not completed their native work");
            helper.assertTrue(finished[0] > finished[1], "Distinct head builds did not produce observable different work times");
            helper.assertBlockPresent(Blocks.STONE, 4, 1, 7);
            helper.assertItemEntityCountIs(Items.COBBLESTONE, new BlockPos(4, 1, 1), 2, 1);
            helper.assertItemEntityCountIs(Items.COBBLESTONE, new BlockPos(4, 1, 4), 2, 1);
        });
    }
    @GameTest(template = "drill_workshop")
    public static void drillRetoolRecoveryAndOccupiedSafety(GameTestHelper helper) {
        var machine = craft(helper, "tconstruct:iron");
        var data = machine.getTag().getCompound(BuildNbt.KEY).getCompound("components").getCompound("head").getCompound("retained");
        data.putString("better_manufacturing:source_part_tag", "{BetterContentJackhammerWear:7}");
        var recipe = (DrillRetoolingRecipe) helper.getLevel().getRecipeManager().byKey(new ResourceLocation("better_manufacturing:retool_drill_head")).orElseThrow();
        var grid = grid(); grid.setItem(0, machine); grid.setItem(8, head("tconstruct:cobalt"));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Valid retooling rejected");
        var output = recipe.assemble(grid, helper.getLevel().registryAccess()); var remains = recipe.getRemainingItems(grid);
        helper.assertTrue(DrillEngineering.evaluate(output.getTag()).rate() > 1, "Replacement head did not change performance");
        helper.assertTrue(remains.stream().filter(stack -> !stack.isEmpty()).count() == 1 && remains.get(8).getCount() == 1
            && remains.get(8).getTag().getInt("BetterContentJackhammerWear") == 7, "Old head refund duplicated or lost original wear");
        helper.assertTrue(ItemStack.isSameItemSameTags(machine, grid.getItem(0)), "Retooling preview mutated input");
        machine.getOrCreateTag().put("BlockEntityTag", new CompoundTag());
        machine.getTag().getCompound("BlockEntityTag").putString("Items", "occupied sentinel");
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()) && recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty(), "Unreviewed stored contents were consumed");
        helper.assertTrue(machine.getTag().getCompound("BlockEntityTag").contains("Items"), "Rejected retooling deleted contents");
        machine.getTag().remove("BlockEntityTag");
        // Execute vanilla ResultSlot consumption and remainder routing, including inventory overflow.
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "drill_retool"));
        var tablePos = helper.absolutePos(new BlockPos(3, 1, 5)); helper.getLevel().setBlockAndUpdate(tablePos, Blocks.CRAFTING_TABLE.defaultBlockState());
        player.setPos(tablePos.getX() + .5, tablePos.getY() + 1, tablePos.getZ() + .5);
        for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), tablePos));
        menu.getSlot(1).set(machine.copy()); var batch = head("tconstruct:cobalt"); batch.setCount(64); menu.getSlot(9).set(batch);
        var taken = menu.getSlot(0).remove(1); helper.assertTrue(!taken.isEmpty(), "Native crafting menu did not expose the retool result");
        menu.getSlot(0).onTake(player, taken);
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(9).getItem().getCount() == 63, "Native crafting consumed the wrong quantities");
        var refunded = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(tablePos).inflate(3), entity -> entity.getItem().is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:pick_head"))));
        helper.assertTrue(refunded.size() == 1 && refunded.get(0).getItem().getCount() == 1 && refunded.get(0).getItem().getTag().getInt("BetterContentJackhammerWear") == 7,
            "Full native inventory lost, duplicated or repaired the recovered head");
        helper.succeed();
    }
    @GameTest(template = "drill_workshop")
    public static void drillMovingActorTierAndMalformedPreservation(GameTestHelper helper) {
        var item = craft(helper, "tconstruct:wood");
        var state = AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING, Direction.EAST);
        var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state, item.getTag().copy());
        var context = new MovementContext(helper.getLevel(), info, new MountedContraption());
        var actor = new DrillMovementBehaviour(); actor.startMoving(context);
        var target = helper.absolutePos(new BlockPos(5, 1, 3)); helper.getLevel().setBlockAndUpdate(target, Blocks.OBSIDIAN.defaultBlockState());
        actor.visitNewPosition(context, target);
        helper.assertTrue(!context.data.contains("BreakingPos"), "Moving wood-head drill bypassed native harvest tier");
        helper.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()); actor.visitNewPosition(context, target);
        helper.assertTrue(context.data.contains("BreakingPos"), "Moving head could not start an eligible native job");
        var epoch = DrillEngineering.epoch();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new slimeknights.tconstruct.library.events.MaterialsLoadedEvent());
        helper.assertTrue(DrillEngineering.epoch() > epoch, "Tinkers data reload event did not invalidate cached engineering values");
        var placed = placed(helper, new BlockPos(2, 1, 7), item);
        var malformed = new CompoundTag(); malformed.putString(BuildNbt.KEY, "future opaque data");
        CreateManufacturing.behaviour(placed).install(malformed);
        var saved = placed.saveWithFullMetadata();
        helper.assertTrue(saved.getString(BuildNbt.KEY).equals("future opaque data") && !CreateManufacturing.behaviour(placed).state().allowed(), "Malformed build was erased or became a stock-grade bypass");
        helper.succeed();
    }
}
