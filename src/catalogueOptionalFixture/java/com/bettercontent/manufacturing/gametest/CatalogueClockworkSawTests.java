package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.clockwork.ClockworkBladeCuttingPrep;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Real Create saw: paid pairs, native 100-tick recipe, three rated one-length blades, no raw-plank yield. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class CatalogueClockworkSawTests {
    private CatalogueClockworkSawTests() {}

    private static ItemStack part(String id) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (!(item instanceof ToolPartItem tool)) throw new AssertionError("Missing materialized part " + id);
        return tool.withMaterial(MaterialVariantId.parse("tconstruct:iron"));
    }

    private static SawBlockEntity saw(GameTestHelper helper, int x) {
        helper.setBlock(x, 1, 2, AllBlocks.MECHANICAL_SAW.getDefaultState()
            .setValue(SawBlock.FACING, Direction.UP).setValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(x - 1, 1, 2, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST));
        var motor = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x - 1, 1, 2)));
        helper.assertTrue(motor instanceof CreativeMotorBlockEntity, "Native Create motor absent");
        ((CreativeMotorBlockEntity) motor).generatedSpeed.setValue(64);
        var block = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 2)));
        helper.assertTrue(block instanceof SawBlockEntity, "Native Create saw absent");
        var machine = (SawBlockEntity) block;
        var blade = ForgeRegistries.ITEMS.getValue(new ResourceLocation("vs_clockwork:propeller_blade"));
        helper.assertTrue(machine.getBehaviour(FilteringBehaviour.TYPE).setFilter(new ItemStack(blade)),
            "Output blade filter rejected by native saw");
        return machine;
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 220)
    public static void paidPlankProducesThreeRatedNativeBladesButRawPlankCannot(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var source = manager.byKey(new ResourceLocation("better_manufacturing:clockwork_propeller_blade_cutting_prep")).orElseThrow();
        helper.assertTrue(source instanceof ClockworkBladeCuttingPrep, "Production three-pair source serializer absent");
        var prep = (ClockworkBladeCuttingPrep) source;
        var grid = CreateDrillTests.grid();
        grid.setItem(0, new ItemStack(Items.OAK_PLANKS));
        for (int i = 0; i < 3; i++) {
            grid.setItem(1 + i, part("tconstruct:broad_blade"));
            grid.setItem(4 + i, part("tconstruct:tough_handle"));
        }
        helper.assertTrue(prep.matches(grid, helper.getLevel()), "One native plank and three real part pairs rejected");
        var paid = prep.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(paid.is(Items.OAK_PLANKS) && paid.getCount() == 1
            && ClockworkBladeCuttingPrep.paidSettings(paid, "vs_clockwork:propeller_blade").size() == 3,
            "Native plank identity or individual part ratings lost");
        grid.setItem(6, ItemStack.EMPTY);
        helper.assertTrue(!prep.matches(grid, helper.getLevel()), "Missing one of three handles still paid three blades");
        var nativeRecipe = manager.byKey(new ResourceLocation("vs_clockwork:cutting/propeller_blade")).orElseThrow();
        helper.assertTrue(nativeRecipe instanceof CuttingRecipe, "Clockwork native cutting ID no longer uses Create saw");
        var cutting = (CuttingRecipe) nativeRecipe;
        helper.assertTrue(cutting.getProcessingDuration() == 100 && cutting.getIngredients().size() == 1
            && cutting.getIngredients().get(0).test(paid)
            && !cutting.getIngredients().get(0).test(new ItemStack(Items.OAK_PLANKS))
            && cutting.getRollableResults().size() == 1
            && cutting.getRollableResults().get(0).getStack().getCount() == 3,
            "Native 100-tick, three-blade saw recipe admitted an unpaid plank");
        var paidSaw = saw(helper, 3);
        var rawSaw = saw(helper, 7);
        int[] maxRated = {0};
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(paidSaw.inventory.insertItem(0, paid.copy(), false).isEmpty()
                && rawSaw.inventory.insertItem(0, new ItemStack(Items.OAK_PLANKS), false).isEmpty(),
                "Real saw rejected the native paid/raw plank inputs");
        });
        helper.onEachTick(() -> {
            int count = 0;
            for (int slot = 0; slot < paidSaw.inventory.getSlots(); slot++) {
                var blade = paidSaw.inventory.getStackInSlot(slot);
                if (!blade.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("vs_clockwork:propeller_blade")))) continue;
                helper.assertTrue(SettingsNbt.read(blade.getTag(), "vs_clockwork:propeller_blade") != null
                    && blade.getTag().getDouble("BladeLength") == 1,
                    "Create saw emitted an unconfigured or wrong-length blade");
                count += blade.getCount();
            }
            maxRated[0] = Math.max(maxRated[0], count);
            for (int slot = 0; slot < rawSaw.inventory.getSlots(); slot++)
                helper.assertTrue(!rawSaw.inventory.getStackInSlot(slot).is(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("vs_clockwork:propeller_blade"))), "Unpaid plank yielded a Clockwork blade");
        });
        helper.runAfterDelay(165, () -> {
            helper.assertTrue(maxRated[0] == 3, "Real saw did not yield all three individually rated native blades");
            helper.succeed();
        });
    }
}
