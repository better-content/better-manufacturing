package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Preserve PowerGrid's native saw ID, one-slab 50-tick work and two-circuit yield with a paid plate. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PowergridEmptyCircuitTests {
    private Wave2PowergridEmptyCircuitTests() {}

    private static Item item(String id) { return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)); }

    private static ItemStack prepare(GameTestHelper helper, String slabId) {
        var id = new ResourceLocation("kubejs:powergrid_cutting/prepare/" + slabId.replace(':', '_').replace('/', '_'));
        var found = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
        helper.assertTrue(found instanceof CraftingRecipe, "Loaded same-species slab preparation absent: " + id);
        var craft = (CraftingRecipe) found;
        var plate = item("tconstruct:large_plate");
        helper.assertTrue(plate instanceof ToolPartItem && craft.getIngredients().size() == 2,
            "Preparation must consume one real TCon plate and one native slab");
        var grid = CreateDrillTests.grid();
        grid.setItem(0, new ItemStack(item(slabId)));
        grid.setItem(1, ((ToolPartItem) plate).withMaterial(MaterialVariantId.parse("tconstruct:rock#stone")));
        helper.assertTrue(craft.matches(grid, helper.getLevel()), "Real plate and native slab rejected: " + slabId);
        var marked = craft.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(marked.is(item(slabId)) && marked.getCount() == 1 && marked.hasTag()
            && marked.getTag().getCompound("better_manufacturing").getInt("empty_circuit_slab") == 1,
            "Plate preparation changed wooden species/count or omitted saw marker: " + slabId);
        grid.setItem(1, ItemStack.EMPTY);
        helper.assertTrue(!craft.matches(grid, helper.getLevel()), "Unpaid slab bypassed physical plate cost");
        return marked;
    }

    private static SawBlockEntity saw(GameTestHelper helper, int x, Item output) {
        helper.setBlock(x, 1, 2, AllBlocks.MECHANICAL_SAW.getDefaultState()
            .setValue(SawBlock.FACING, Direction.UP).setValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(x - 1, 1, 2, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST));
        var motor = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x - 1, 1, 2)));
        helper.assertTrue(motor instanceof CreativeMotorBlockEntity, "Native Create motor absent");
        ((CreativeMotorBlockEntity) motor).generatedSpeed.setValue(64);
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 2)));
        helper.assertTrue(be instanceof SawBlockEntity, "Native Create saw absent");
        var machine = (SawBlockEntity) be;
        helper.assertTrue(machine.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(output)),
            "Native saw output filter rejected the circuit");
        return machine;
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 180)
    public static void nativeSawConsumesOnlyPlatePreparedWoodenSlabs(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("powergrid")
            && ModList.get().isLoaded("tconstruct"), "Pinned Create, PowerGrid and TCon required");
        var manager = helper.getLevel().getRecipeManager();
        var found = manager.byKey(new ResourceLocation("powergrid:cutting/empty_circuit_slabs")).orElseThrow();
        helper.assertTrue(found instanceof CuttingRecipe, "PowerGrid native recipe ID no longer uses Create cutting");
        var cutting = (CuttingRecipe) found;
        var circuit = item("powergrid:empty_circuit");
        helper.assertTrue(cutting.getIngredients().size() == 1 && cutting.getProcessingDuration() == 50
            && cutting.getRollableResults().size() == 1
            && cutting.getRollableResults().get(0).getStack().is(circuit)
            && cutting.getRollableResults().get(0).getStack().getCount() == 2
            && cutting.getRollableResults().get(0).getChance() == 1.0f,
            "PowerGrid one-slab, 50-tick, guaranteed two-circuit native work changed");
        var oak = prepare(helper, "minecraft:oak_slab");
        var spruce = prepare(helper, "minecraft:spruce_slab");
        helper.assertTrue(cutting.getIngredients().get(0).test(oak) && cutting.getIngredients().get(0).test(spruce)
            && !cutting.getIngredients().get(0).test(new ItemStack(item("minecraft:oak_slab"))),
            "Native saw input narrowed wooden species or admits an unpaid slab");
        var paidSaw = saw(helper, 3, circuit);
        var rawSaw = saw(helper, 7, circuit);
        int[] produced = {0};
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(paidSaw.getSpeed() != 0 && rawSaw.getSpeed() != 0, "Real native saws are not motor-powered");
            helper.assertTrue(paidSaw.inventory.insertItem(0, oak.copy(), false).isEmpty()
                && rawSaw.inventory.insertItem(0, new ItemStack(item("minecraft:oak_slab")), false).isEmpty(),
                "Native saw did not accept the paid/raw one-slab inputs");
            helper.assertTrue(paidSaw.inventory.recipeDuration == 50,
                "Paid native saw lost 50-tick recipe work: " + paidSaw.inventory.recipeDuration);
        });
        helper.onEachTick(() -> {
            for (int slot = 0; slot < paidSaw.inventory.getSlots(); slot++) {
                var stack = paidSaw.inventory.getStackInSlot(slot);
                if (stack.is(circuit)) produced[0] = Math.max(produced[0], stack.getCount());
            }
            for (int slot = 0; slot < rawSaw.inventory.getSlots(); slot++)
                helper.assertTrue(!rawSaw.inventory.getStackInSlot(slot).is(circuit), "Raw slab produced a circuit without a plate");
        });
        helper.runAfterDelay(85, () -> {
            helper.assertTrue(produced[0] == 2 && !paidSaw.inventory.getStackInSlot(0).is(item("minecraft:oak_slab")),
                "Real Create saw did not consume one prepared slab to produce two native circuits");
            helper.succeed();
        });
    }
}
