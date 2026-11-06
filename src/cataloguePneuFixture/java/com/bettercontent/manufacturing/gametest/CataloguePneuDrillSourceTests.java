package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.pneumatictools.PneuDrillBitSource;
import com.bettercontent.manufacturing.integration.pneumatictools.PneuDrillBitSourceRecipe;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.api.crafting.recipe.AssemblyRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Check the loaded exact-ID PNCR thermo recipe, not just the optional finishing craft. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class CataloguePneuDrillSourceTests {
    private CataloguePneuDrillSourceTests() {}

    private static ItemStack item(String id) {
        var registered = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (registered == null || registered == net.minecraft.world.item.Items.AIR)
            throw new AssertionError("Missing native input " + id);
        return new ItemStack(registered);
    }

    @GameTest(template = "pneu_workshop", timeoutTicks = 140)
    public static void everyThermoBitPaysHeadAndKeepsNativeLubricantPressureAndYield(GameTestHelper helper) {
        var headItem = item("tconstruct:pick_head").getItem();
        helper.assertTrue(headItem instanceof ToolPartItem, "Real TCon pick head unavailable");
        var head = ((ToolPartItem) headItem).withMaterial(MaterialVariantId.parse("tconstruct:iron"));
        String[][] grades = {
            {"iron", "minecraft:iron_ingot", "iron_drill_bit", "2000", "3"},
            {"compressed_iron", "pneumaticcraft:ingot_iron_compressed", "compressed_iron_drill_bit", "4000", "4"},
            {"diamond", "minecraft:diamond_block", "diamond_drill_bit", "8000", "7.5"}
        };
        var manager = helper.getLevel().getRecipeManager();
        ItemStack diamond = ItemStack.EMPTY;
        for (String[] grade : grades) {
            String output = "pneumaticcraft:drill_bit_" + grade[0];
            var found = manager.byKey(new ResourceLocation("better_manufacturing:pneu_prepare_" + grade[0] + "_drill_bit")).orElseThrow();
            helper.assertTrue(found instanceof PneuDrillBitSourceRecipe, "Paid material preparation absent: " + output);
            var prep = (PneuDrillBitSourceRecipe) found;
            var grid = CreateDrillTests.grid();
            var raw = item(grade[1]);
            grid.setItem(0, raw.copy()); grid.setItem(1, head.copy());
            helper.assertTrue(prep.matches(grid, helper.getLevel()), "Native material plus real head rejected: " + output);
            var paid = prep.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(paid.is(raw.getItem()) && paid.getCount() == 1
                && SettingsNbt.read(paid.getTag(), output) != null
                && PneuDrillBitSource.thermoInput(paid, output),
                "Preparation lost native material identity or the pick-head rating: " + output);
            grid.setItem(1, ItemStack.EMPTY);
            helper.assertTrue(!prep.matches(grid, helper.getLevel()), "Raw material bypassed the pick-head bill: " + output);
            var nativeFound = manager.byKey(new ResourceLocation("pneumaticcraft:thermo_plant/" + grade[2])).orElseThrow();
            helper.assertTrue(nativeFound instanceof ThermoPlantRecipe, "Native thermo serializer lost: " + output);
            var thermo = (ThermoPlantRecipe) nativeFound;
            helper.assertTrue(thermo.getInputItem().test(paid) && !thermo.getInputItem().test(raw)
                && thermo.getInputFluid().getAmount() == Integer.parseInt(grade[3])
                && thermo.getRequiredPressure() == Double.parseDouble(grade[4])
                && thermo.getOutputItem().is(item(output).getItem()) && thermo.getOutputItem().getCount() == 1,
                "Paid input bypassed or displaced native lubricant/pressure/output: " + output);
            var result = PneuDrillBitSource.thermoOutput(paid, thermo.getOutputItem());
            if (grade[0].equals("diamond")) diamond = result.copy();
            helper.assertTrue(result.is(item(output).getItem()) && SettingsNbt.read(result.getTag(), output) != null
                && !result.getTag().contains(PneuDrillBitSource.MARKER)
                && PneuDrillBitSource.thermoOutput(raw, thermo.getOutputItem()).isEmpty(),
                "Native output transfer dropped rating or allowed unprepared stock: " + output);
            var malformed = paid.copy();
            malformed.getOrCreateTag().putString(PneuDrillBitSource.MARKER, "pneumaticcraft:drill_bit_netherite");
            helper.assertTrue(!thermo.getInputItem().test(malformed)
                && PneuDrillBitSource.thermoOutput(malformed, thermo.getOutputItem()).isEmpty(),
                "Wrong-output marker laundered into a valid thermo bit: " + output);
        }
        var next = manager.byKey(new ResourceLocation("pneumaticcraft:unassembled_netherite_drill_bit")).orElseThrow();
        helper.assertTrue(next instanceof PneuDrillBitSourceRecipe, "Diamond-to-netherite precursor lost its native bill");
        var assemble = (PneuDrillBitSourceRecipe) next;
        var grid = CreateDrillTests.grid();
        grid.setItem(0, diamond); grid.setItem(1, item("minecraft:netherite_ingot"));
        helper.assertTrue(assemble.matches(grid, helper.getLevel()), "Paid diamond bit plus netherite ingot rejected");
        var marked = assemble.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(marked.is(item("pneumaticcraft:unassembled_netherite_drill_bit").getItem())
            && PneuDrillBitSource.assemblyInput(marked), "Unassembled bit lost diamond-head rating or native item identity");
        grid.setItem(0, item("pneumaticcraft:drill_bit_diamond"));
        helper.assertTrue(!assemble.matches(grid, helper.getLevel()), "Unpaid diamond bit bypassed netherite preparation");
        var nativeAssembly = manager.byKey(new ResourceLocation("pneumaticcraft:assembly/netherite_drill_bit")).orElseThrow();
        helper.assertTrue(nativeAssembly instanceof AssemblyRecipe
            && ((AssemblyRecipe) nativeAssembly).matches(marked)
            && !((AssemblyRecipe) nativeAssembly).matches(item("pneumaticcraft:unassembled_netherite_drill_bit")),
            "Native PNCR assembly-drill route accepted an unprepared netherite bit");
        var finalBit = PneuDrillBitSource.assemblyOutput(marked, item("pneumaticcraft:drill_bit_netherite"));
        helper.assertTrue(finalBit.is(item("pneumaticcraft:drill_bit_netherite").getItem())
            && SettingsNbt.read(finalBit.getTag(), "pneumaticcraft:drill_bit_netherite") != null,
            "Native drill upgrade lost the material-dependent paid diamond predecessor");
        helper.succeed();
    }
}
