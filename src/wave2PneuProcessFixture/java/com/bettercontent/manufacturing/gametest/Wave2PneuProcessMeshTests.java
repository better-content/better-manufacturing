package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.List;

/** Real CC recipe and Create filling sequence; never replaces the native water/loop budget. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuProcessMeshTests {
    private static final ResourceLocation EMPTY = new ResourceLocation("compressedcreativity:crafting/mesh_empty");
    private static final ResourceLocation WOVEN = new ResourceLocation("compressedcreativity:crafting/mesh_woven");
    private static final ResourceLocation SPLASHING = new ResourceLocation("compressedcreativity:sequenced_assembly/mesh_splashing");

    private static void requiredMods(GameTestHelper helper) {
        for (String mod : List.of("pneumaticcraft", "compressedcreativity", "create", "tconstruct"))
            helper.assertTrue(ModList.get().isLoaded(mod), "Required real mod absent: " + mod);
    }

    private static ItemStack part(String shape, String material) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:" + shape));
        if (!(item instanceof ToolPartItem tool)) throw new AssertionError("Native TCon part missing: " + shape);
        return tool.withMaterial(new MaterialId(material));
    }

    private static TransientCraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }

    private static PartVariantRecipe recipe(GameTestHelper helper, ResourceLocation id) {
        var found = helper.getLevel().getRecipeManager().byKey(id).orElseThrow(() -> new AssertionError("Production CC recipe absent: " + id));
        helper.assertTrue(found instanceof PartVariantRecipe, "Production CC recipe did not use configured parts: " + id);
        return (PartVariantRecipe) found;
    }

    private static ItemStack frame(GameTestHelper helper, String material) {
        var recipe = recipe(helper, EMPTY);
        var grid = grid();
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 8}) {
            var available = recipe.getIngredients().get(i).getItems();
            helper.assertTrue(available.length > 0, "Required native CC ingredient unavailable in effective pack: " + i);
            grid.setItem(i, available[0].copy());
        }
        grid.setItem(4, part("tool_handle", material));
        grid.setItem(7, part("broad_blade", material));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native mesh-frame shape/material rejected: " + material);
        var output = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!output.isEmpty() && output.getCount() == 1, "Native mesh-frame yield changed");
        return output;
    }

    private static ItemStack woven(GameTestHelper helper, String material) {
        var recipe = recipe(helper, WOVEN);
        var grid = grid();
        grid.setItem(1, frame(helper, material));
        grid.setItem(7, frame(helper, material));
        for (int i : new int[]{3, 4, 5}) grid.setItem(i, new ItemStack(Items.WHITE_WOOL));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native woven mesh inputs rejected");
        var output = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!output.isEmpty() && output.getCount() == 1, "Native woven mesh yield changed");
        return output;
    }

    private static double airflow(GameTestHelper helper, ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        var settings = id == null ? null : SettingsNbt.read(stack.getTag(), id.toString());
        helper.assertTrue(settings != null && settings.properties().containsKey("airflow"), "Mesh lost baked airflow");
        return settings.properties().get("airflow");
    }

    private static ItemStack nativeWaterStage(GameTestHelper helper, ItemStack input, int step) {
        var recipe = SequencedAssemblyRecipe.getRecipe(helper.getLevel(), input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class)
            .orElseThrow(() -> new AssertionError("Native water-filling stage missing: " + step));
        helper.assertTrue(recipe.getRequiredFluid().getRequiredAmount() == 1000
            && recipe.getRequiredFluid().test(new FluidStack(Fluids.WATER, 1000)), "Native 1000 mB water budget changed");
        var outputs = recipe.rollResults();
        helper.assertTrue(outputs.size() == 1 && outputs.get(0).getCount() == 1, "Native filling yield changed");
        return outputs.get(0);
    }

    @GameTest(template = "empty")
    public static void nativeEightStageMeshKeepsBakedRatingAndNativeWaterCost(GameTestHelper helper) {
        requiredMods(helper);
        var nativeRecipe = helper.getLevel().getRecipeManager().byKey(SPLASHING)
            .orElseThrow(() -> new AssertionError("Native CC sequenced assembly absent"));
        helper.assertTrue(nativeRecipe instanceof SequencedAssemblyRecipe
            && ((SequencedAssemblyRecipe) nativeRecipe).getLoops() == 8, "Native eight-loop recipe changed");
        for (String material : List.of("tconstruct:wood", "tconstruct:iron")) {
            var input = woven(helper, material);
            double expected = material.equals("tconstruct:wood") ? .75 : 1;
            helper.assertTrue(Math.abs(airflow(helper, input) - expected) < .001, "Cheap pack material failed to affect mesh");
            for (int stage = 1; stage <= 8; stage++) {
                input = nativeWaterStage(helper, input, stage);
                helper.assertTrue(Math.abs(airflow(helper, input) - expected) < .001, "Baked setting lost at native stage " + stage);
                if (stage < 8) {
                    helper.assertTrue(input.getTag().getCompound("SequencedAssembly").getInt("Step") == stage,
                        "Native sequenced progress was overwritten at stage " + stage);
                } else helper.assertTrue(!input.getTag().contains("SequencedAssembly")
                    && ForgeRegistries.ITEMS.getKey(input.getItem()).toString().equals("compressedcreativity:mesh_splashing"),
                    "Final native mesh identity/progress changed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unconfiguredMeshRemainsNativeAndWrongOutputSettingsDoNotPropagate(GameTestHelper helper) {
        requiredMods(helper);
        var ordinary = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("compressedcreativity:mesh_woven")));
        var first = nativeWaterStage(helper, ordinary, 1);
        helper.assertTrue(!first.getTag().contains(SettingsNbt.KEY) && first.getTag().getCompound("SequencedAssembly").getInt("Step") == 1,
            "Native unconfigured input acquired fake settings");
        var wrong = woven(helper, "tconstruct:wood");
        var wrongTag = wrong.getTag().copy();
        var wanted = SettingsNbt.read(wrongTag, "compressedcreativity:mesh_woven");
        SettingsNbt.write(wrong.getOrCreateTag(), new com.bettercontent.manufacturing.api.MachineSettings(
            "compressedcreativity:mesh_dense", wanted.properties(), wanted.materials(), null));
        helper.assertTrue(!nativeWaterStage(helper, wrong, 1).getTag().contains(SettingsNbt.KEY),
            "Wrong-output settings were transferred across native sequence");
        helper.succeed();
    }
}
