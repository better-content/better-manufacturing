package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import me.desht.pneumaticcraft.common.block.entity.HeatPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.List;

/** PNCR owns heat transfer, temperature and inventory; construction changes only native conductance. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuProcessHeatTests {
    private static final ResourceLocation PIPE = new ResourceLocation("pneumaticcraft:heat_pipe");
    private static TransientCraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static ItemStack part(String shape, String material) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:" + shape));
        if (!(item instanceof ToolPartItem tool)) throw new AssertionError("Native TCon part missing: " + shape);
        return tool.withMaterial(new MaterialId(material));
    }
    private static ItemStack pipe(GameTestHelper helper, String material) {
        var found = helper.getLevel().getRecipeManager().byKey(PIPE)
            .orElseThrow(() -> new AssertionError("Production PNCR heat pipe recipe absent"));
        helper.assertTrue(found instanceof PartVariantRecipe, "Configured heat pipe recipe not effective");
        var recipe = (PartVariantRecipe) found;
        var grid = grid();
        for (int slot : new int[]{0, 1, 3, 5, 6, 7, 8}) {
            var options = recipe.getIngredients().get(slot).getItems();
            helper.assertTrue(options.length > 0, "Missing real compressed iron/lagging ingredient at " + slot);
            grid.setItem(slot, options[0].copy());
        }
        grid.setItem(2, part("tough_handle", material));
        grid.setItem(4, part("large_plate", material));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native heat pipe shape/role rejected");
        var output = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(output.getCount() == 6, "Native six-pipe recipe yield lost");
        var settings = SettingsNbt.read(output.getTag(), PIPE.toString());
        helper.assertTrue(settings != null && settings.properties().containsKey("thermal_transfer"), "Heat transfer missing from crafted pipe");
        return output;
    }
    private static HeatPipeBlockEntity placed(GameTestHelper helper, int x, ItemStack stack) {
        var at = helper.absolutePos(new BlockPos(x, 2, 2));
        var result = ((BlockItem) stack.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), at, Direction.WEST, stack.copy(), Direction.UP));
        helper.assertTrue(result.consumesAction(), "Native heat pipe placement failed");
        var block = helper.getLevel().getBlockEntity(at);
        helper.assertTrue(block instanceof HeatPipeBlockEntity, "Native PNCR pipe entity missing");
        return (HeatPipeBlockEntity) block;
    }
    @GameTest(template = "wave2_pneu_process_workshop")
    public static void cheapMaterialChangesNativeConductanceWithoutChangingHeatOrDrops(GameTestHelper helper) {
        for (String mod : List.of("pneumaticcraft", "tconstruct"))
            helper.assertTrue(ModList.get().isLoaded(mod), "Required real mod absent: " + mod);
        var wood = pipe(helper, "tconstruct:wood");
        var iron = pipe(helper, "tconstruct:iron");
        var woodSettings = SettingsNbt.read(wood.getTag(), PIPE.toString());
        helper.assertTrue(Math.abs(woodSettings.properties().get("thermal_transfer") - .75) < .001, "Cheap wood pipe not rated");
        var woodPipe = placed(helper, 2, wood);
        var ironPipe = placed(helper, 5, iron);
        double woodResistance = woodPipe.getHeatExchanger(Direction.UP).getThermalResistance();
        double ironResistance = ironPipe.getHeatExchanger(Direction.UP).getThermalResistance();
        helper.assertTrue(woodResistance > 0 && ironResistance > 0
            && Math.abs(woodResistance / ironResistance - 1d / .75) < .02,
            "Baked rate did not alter native thermal resistance");
        helper.assertTrue(Math.abs(PneuProcessNative.rate(woodPipe, "thermal_transfer") - .75) < .001,
            "Crafted heat-pipe setting lost at placement");
        var copy = new HeatPipeBlockEntity(woodPipe.getBlockPos(), woodPipe.getBlockState());
        copy.setLevel(helper.getLevel());
        copy.load(woodPipe.saveWithFullMetadata());
        copy.onLoad();
        helper.assertTrue(Math.abs(copy.getHeatExchanger(Direction.UP).getThermalResistance() - woodResistance) < .001,
            "Saving/loading multiplied native resistance again");
        var drops = Block.getDrops(woodPipe.getBlockState(), helper.getLevel(), woodPipe.getBlockPos(), woodPipe);
        helper.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1
            && SettingsNbt.read(drops.get(0).getTag(), PIPE.toString()) != null,
            "Native PNCR drop lost baked settings");
        helper.succeed();
    }
    @GameTest(template = "wave2_pneu_process_workshop")
    public static void malformedOrWrongOutputInstalledPipeUsesNativeBaseline(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft"), "Required real PNCR absent");
        var rated = pipe(helper, "tconstruct:wood");
        rated.getTag().getCompound(SettingsNbt.KEY).putInt("version", 999);
        var malformed = placed(helper, 2, rated);
        var baseline = placed(helper, 5, new ItemStack(ForgeRegistries.ITEMS.getValue(PIPE)));
        var wrongStack = pipe(helper, "tconstruct:wood");
        SettingsNbt.write(wrongStack.getOrCreateTag(), new com.bettercontent.manufacturing.api.MachineSettings(
            "pneumaticcraft:heat_sink", java.util.Map.of("thermal_transfer", .75), java.util.Map.of(), null));
        var wrongOutput = placed(helper, 8, wrongStack);
        double nativeResistance = baseline.getHeatExchanger(Direction.UP).getThermalResistance();
        for (var block : List.of(malformed, wrongOutput)) {
            helper.assertTrue(PneuProcessNative.rate(block, "thermal_transfer") == 1
                && Math.abs(block.getHeatExchanger(Direction.UP).getThermalResistance() - nativeResistance) < .001,
                "Invalid/wrong-output settings changed native conductance");
            helper.assertTrue(!block.saveWithFullMetadata().contains(SettingsNbt.KEY),
                "Invalid settings were persisted as valid");
        }
        helper.succeed();
    }
}
