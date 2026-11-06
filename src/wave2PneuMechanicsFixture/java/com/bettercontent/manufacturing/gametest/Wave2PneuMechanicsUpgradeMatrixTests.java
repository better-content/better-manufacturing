package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** One paid plate per native four-matrix thermo cycle, including PNCR's item-only cache transition. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsUpgradeMatrixTests {
    private Wave2PneuMechanicsUpgradeMatrixTests() {}

    @GameTest(template = "pneu_workshop", timeoutTicks = 160)
    public static void preparedLapisPaysPlateWaterAirAndNativeThermoWork(GameTestHelper helper) throws Exception {
        var recipes = helper.getLevel().getRecipeManager();
        var prep = recipes.byKey(new ResourceLocation("better_manufacturing:prepared_upgrade_matrix_lapis")).orElseThrow();
        helper.assertTrue(prep instanceof CraftingRecipe, "Loaded one-lapis plate preparation is missing");
        var craft = (CraftingRecipe) prep;
        var plate = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:large_plate"));
        helper.assertTrue(plate instanceof ToolPartItem && craft.getIngredients().size() == 2,
            "Matrix prep does not use a real TCon plate");
        var grid = CreateDrillTests.grid();
        grid.setItem(0, new ItemStack(Items.LAPIS_LAZULI));
        grid.setItem(1, ((ToolPartItem) plate).withMaterial(MaterialVariantId.parse("tconstruct:iron")));
        helper.assertTrue(craft.matches(grid, helper.getLevel()), "Native lapis plus eligible materialized plate rejected");
        var paid = craft.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(paid.is(Items.LAPIS_LAZULI) && paid.getCount() == 1 && paid.hasTag()
            && paid.getTag().getCompound("better_manufacturing").getInt("upgrade_matrix_lapis") == 1,
            "Preparation lost native lapis identity/count or plate-paid marker");
        grid.setItem(1, ItemStack.EMPTY);
        helper.assertTrue(!craft.matches(grid, helper.getLevel()), "Unpaid lapis bypassed plate preparation");

        var loaded = recipes.byKey(new ResourceLocation("pneumaticcraft:thermo_plant/upgrade_matrix")).orElseThrow();
        helper.assertTrue(loaded instanceof ThermoPlantRecipe, "Native thermo recipe ID/serializer changed");
        var thermo = (ThermoPlantRecipe) loaded;
        helper.assertTrue(thermo.getRequiredPressure() == 2 && thermo.getOperatingTemperature().getMin() == 273
            && thermo.getInputFluid().getAmount() == 1000
            && thermo.getOutputItem().is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft:upgrade_matrix")))
            && thermo.getOutputItem().getCount() == 4 && thermo.getInputItem().test(paid)
            && !thermo.getInputItem().test(new ItemStack(Items.LAPIS_LAZULI)),
            "Plate gate changed the native water/pressure/temperature/four-output cost or admitted raw lapis");
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:thermopneumatic_processing_plant"));
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, block.defaultBlockState());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(be instanceof ThermopneumaticProcessingPlantBlockEntity, "Real PNCR thermo plant absent");
        var plant = (ThermopneumaticProcessingPlantBlockEntity) be;
        var field = ThermopneumaticProcessingPlantBlockEntity.class.getDeclaredField("inputItemHandler");
        field.setAccessible(true);
        var inputs = (ItemStackHandler) field.get(plant);
        var raw = new ItemStack(Items.LAPIS_LAZULI);
        helper.assertTrue(!inputs.isItemValid(0, raw), "Ordinary lapis entered PNCR before cache priming");
        inputs.setStackInSlot(0, raw);
        helper.assertTrue(plant.getInputTank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
            "PNCR input tank rejected native water cost");
        for (int i = 0; i < 3; i++) plant.tickServer();
        helper.assertTrue(plant.getOutputInventory().getStackInSlot(0).isEmpty() && plant.getInputTank().getFluidAmount() == 1000
            && inputs.getStackInSlot(0).is(Items.LAPIS_LAZULI), "Ordinary lapis produced a free upgrade matrix");
        inputs.setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(inputs.isItemValid(0, paid), "PNCR item-only acceptability cache rejected a prepared lapis stack");
        inputs.setStackInSlot(0, paid);
        for (int i = 0; i < 100 && plant.getPressure() < 2.5; i++) plant.addAir(250);
        helper.assertTrue(plant.getPressure() >= 2.5, "Native plant cannot reach the recipe's 2-bar requirement");
        var progress = ThermopneumaticProcessingPlantBlockEntity.class.getDeclaredField("craftingProgress");
        progress.setAccessible(true);
        progress.setInt(plant, 5900); // Let PNCR itself finish and charge the final work/air, never synthesize an output.
        float pressureBefore = plant.getPressure();
        for (int i = 0; i < 30 && plant.getOutputInventory().getStackInSlot(0).isEmpty(); i++) plant.tickServer();
        var result = plant.getOutputInventory().getStackInSlot(0);
        helper.assertTrue(result.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft:upgrade_matrix")))
            && result.getCount() == 4 && inputs.getStackInSlot(0).isEmpty()
            && plant.getInputTank().getFluidAmount() == 0 && plant.getPressure() < pressureBefore,
            "Real native cycle failed to consume paid lapis, 1000 mB water and air for four matrices");
        helper.succeed();
    }
}
