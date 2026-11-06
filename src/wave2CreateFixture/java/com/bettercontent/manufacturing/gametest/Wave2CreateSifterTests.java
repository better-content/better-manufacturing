package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.bettercontent.manufacturing.integration.sifter.ScreenAccess;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Two native frame variants and real sifting work, not merely registered recipe serializers. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateSifterTests {
    private Wave2CreateSifterTests() {}

    private static ItemStack handle(String material) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tool_handle"));
        if (!(item instanceof ToolPartItem part)) throw new AssertionError("Real TConstruct handle missing");
        return part.withMaterial(MaterialVariantId.parse(material));
    }

    private static ItemStack craft(GameTestHelper helper, String id, String material, ItemStack predecessor) {
        // Production20 removes the native basic Sifter ID and reauthors its
        // part-consuming progression recipe; the native brass upgrade keeps its ID.
        var recipeId = id.equals("sifter") ? "kubejs:powered_works/refinement/sifter" : "createsifter:" + id;
        var found = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(recipeId))
            .orElseThrow(() -> new AssertionError("Missing production Sifter frame recipe " + recipeId));
        helper.assertTrue(found instanceof PartVariantRecipe, "Sifter frame has no craft-time native part calculation");
        var recipe = (PartVariantRecipe) found;
        var grid = CreateDrillTests.grid();
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            var options = ingredient.getItems();
            helper.assertTrue(options.length > 0, "Missing real Sifter progression ingredient " + i);
            grid.setItem(i % recipe.getWidth() + i / recipe.getWidth() * 3, options[0].copy());
        }
        for (var binding : recipe.parts()) {
            int slot = binding.slot() % recipe.getWidth() + binding.slot() / recipe.getWidth() * 3;
            grid.setItem(slot, binding.role().equals("core") ? predecessor.copy() : handle(material));
        }
        var output = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!output.isEmpty() && SettingsNbt.read(output.getTag(), "createsifter:" + id) != null,
            "Native Sifter frame craft lost its fixed setting");
        return output;
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 220)
    public static void framePlacementWorkCostSaveAndNativeOutput(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("createsifter") && ModList.get().isLoaded("tconstruct"),
            "Real Create, CreateSifter and TConstruct required for positive frame test");
        SifterBlockEntity[] machines = new SifterBlockEntity[2];
        for (int choice = 0; choice < 2; choice++) {
            // Native slimewood handle mining-speed modifier -0.15 contrasts with
            // iron's 0; pack wood and iron both have 0 and cannot produce .75.
            var item = craft(helper, "sifter", choice == 0 ? "tconstruct:slimewood" : "tconstruct:iron", ItemStack.EMPTY);
            var pos = helper.absolutePos(new BlockPos(2 + choice * 4, 2, 2));
            var placed = ((BlockItem) item.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos, Direction.WEST, item.copy(), Direction.UP));
            helper.assertTrue(placed.consumesAction(), "Native Sifter frame placement failed");
            var block = (SifterBlockEntity) helper.getLevel().getBlockEntity(pos);
            machines[choice] = block;
            var installed = KineticVariantBehaviour.get(block);
            helper.assertTrue(installed != null && installed.settings() != null
                && Math.abs(installed.rate() - (choice == 0 ? .85 : 1)) < 1e-6,
                "Crafted Sifter frame did not transfer onto native machine: choice=" + choice
                    + " built=" + SettingsNbt.read(item.getTag(), "createsifter:sifter")
                    + " installed=" + (installed == null ? "no behaviour" : installed.settings()));
            var restored = block.getType().create(pos, block.getBlockState()); restored.setLevel(helper.getLevel());
            restored.load(block.saveWithFullMetadata());
            helper.assertTrue(KineticVariantBehaviour.get(restored).rate() == installed.rate(), "Native Sifter save/load lost frame");
            var drops = Block.getDrops(block.getBlockState(), helper.getLevel(), pos, block);
            helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(item, drops.get(0)), "Native Sifter drop lost frame settings");
            var mesh = ForgeRegistries.ITEMS.getValue(new ResourceLocation("createsifter:string_mesh"));
            helper.assertTrue(mesh != null && mesh != Items.AIR, "Missing ordinary native mesh");
            block.meshInv.insertItem(0, new ItemStack(mesh), false);
            helper.assertTrue(((ScreenAccess) block).manufacturing$screenState().rate() == installed.rate(),
                "Native unconfigured mesh incorrectly diluted configured frame");
            var motorPos = pos.below(); helper.getLevel().setBlockAndUpdate(motorPos,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
            ((CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(motorPos)).generatedSpeed.setValue(32);
        }
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(Math.abs(machines[0].getSpeed()) == 32 && Math.abs(machines[1].getSpeed()) == 32,
                "Native frame machines received no kinetic supply");
            helper.assertTrue(Math.abs(machines[0].calculateStressApplied() / machines[1].calculateStressApplied() - .85) < .001,
                "Native frame work changed without paying proportional stress");
            for (var block : machines) block.inputInv.setStackInSlot(0, new ItemStack(Items.CACTUS));
        });
        helper.runAtTickTime(100, () -> helper.assertTrue(machines[1].inputInv.getStackInSlot(0).isEmpty()
            && !machines[0].inputInv.getStackInSlot(0).isEmpty(), "Native Sifter completion ignored frame rating"));
        helper.runAtTickTime(140, () -> {
            for (var block : machines) {
                int coal = 0, flint = 0;
                for (int slot = 0; slot < block.outputInv.getSlots(); slot++) {
                    var stack = block.outputInv.getStackInSlot(slot);
                    if (stack.is(Items.COAL)) coal += stack.getCount();
                    if (stack.is(Items.FLINT)) flint += stack.getCount();
                }
                helper.assertTrue(coal == 2 && flint == 2, "Frame altered native Sifter yield or recipe");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brassUpgradeInheritsCoreWithoutStackedBonus(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("createsifter") && ModList.get().isLoaded("tconstruct"),
            "Real Sifter/TConstruct dependencies are required");
        var core = craft(helper, "sifter", "tconstruct:slimewood", ItemStack.EMPTY);
        var brass = craft(helper, "brass_sifter", "tconstruct:iron", core);
        var inherited = SettingsNbt.read(brass.getTag(), "createsifter:brass_sifter");
        helper.assertTrue(inherited != null && Math.abs(inherited.rate() - ((.85 + 1 + 1) / 3)) < .001,
            "Brass native upgrade dropped predecessor rating or stacked free throughput");
        var baseline = craft(helper, "brass_sifter", "tconstruct:iron",
            new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("createsifter:sifter"))));
        helper.assertTrue(SettingsNbt.read(baseline.getTag(), "createsifter:brass_sifter").rate() == 1,
            "Unconfigured native Sifter predecessor did not use baseline");
        helper.succeed();
    }
}
