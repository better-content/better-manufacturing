package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour;
import com.bettercontent.manufacturing.integration.createfluids.FluidNativeRates;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.bettercontent.betterindustrialheat.content.heat.HeatPipeBlockEntity;
import com.bettercontent.betterindustrialheat.HeatSyncConfig;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.world.level.block.Blocks;
import com.bettercontent.betterairtightmachines.chemistry.AirtightUpgradeHolder;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2FluidsHeatPipeTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cheapPlateConsumesIntoNativePipeAndAttenuatesPressure(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct")
            && ModList.get().isLoaded("better_industrial_heat"), "Fluids profile must load real Create, TCon and Industrial Heat");
        ItemStack crafted = WorkshopVariantTests.craft(helper, "fluid_pipe", "tconstruct:rock#stone");
        helper.assertTrue(crafted.getCount() == 4, "Native pipe recipe must retain four-output yield");
        BlockPos ratedPos = new BlockPos(1, 1, 1);
        var placed = ((BlockItem) crafted.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), helper.absolutePos(ratedPos), Direction.WEST, crafted.copy(), Direction.UP));
        helper.assertTrue(placed.consumesAction(), "Rated pipe could not be placed");
        helper.setBlock(ratedPos.east(), AllBlocks.FLUID_PIPE.getDefaultState());
        var rated = (FluidPipeBlockEntity) helper.getBlockEntity(ratedPos);
        var plain = (FluidPipeBlockEntity) helper.getBlockEntity(ratedPos.east());
        var variant = FluidVariantBehaviour.get(rated);
        helper.assertTrue(variant != null && variant.settings() != null && variant.rate("fluid_rate") == .75,
            "Cheap rock plate did not install bounded pipe pressure rate");
        var loaded = rated.getType().create(rated.getBlockPos(), rated.getBlockState());
        loaded.setLevel(helper.getLevel());
        loaded.load(rated.saveWithFullMetadata());
        helper.assertTrue(FluidVariantBehaviour.get(loaded).rate("fluid_rate") == .75,
            "Pipe settings did not survive native save/load");
        var drops = Block.getDrops(rated.getBlockState(), helper.getLevel(), rated.getBlockPos(), rated);
        helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(crafted, drops.get(0)),
            "Pipe loot lost final settings or changed count/identity");
        var ratedFlow = BlockEntityBehaviour.get(rated, FluidTransportBehaviour.TYPE);
        var plainFlow = BlockEntityBehaviour.get(plain, FluidTransportBehaviour.TYPE);
        helper.assertTrue(ratedFlow != null && plainFlow != null, "Native pipe transport behaviour missing");
        ratedFlow.addPressure(Direction.EAST, true, 100f);
        plainFlow.addPressure(Direction.WEST, true, 100f);
        helper.assertTrue(ratedFlow.getConnection(Direction.EAST) != null && plainFlow.getConnection(Direction.WEST) != null,
            "Native pipe pressure connection unavailable");
        var ratedPressure = ratedFlow.getConnection(Direction.EAST).getPressure();
        var plainPressure = plainFlow.getConnection(Direction.WEST).getPressure();
        float ratedMagnitude = Math.abs(ratedPressure.getFirst()) + Math.abs(ratedPressure.getSecond());
        float plainMagnitude = Math.abs(plainPressure.getFirst()) + Math.abs(plainPressure.getSecond());
        helper.assertTrue(plainMagnitude > 0 && Math.abs(ratedMagnitude / plainMagnitude - .75f) < .001f,
            "Pipe did not attenuate native pressure: " + ratedMagnitude + "/" + plainMagnitude);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var hit = new BlockHitResult(Vec3.atCenterOf(rated.getBlockPos()), Direction.SOUTH, rated.getBlockPos(), false);
        var wrench = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(((FluidPipeBlock) rated.getBlockState().getBlock()).onWrenched(rated.getBlockState(), wrench).consumesAction(),
            "Native wrench did not convert the rated pipe to glass");
        var glassState = helper.getLevel().getBlockState(rated.getBlockPos());
        var glassBE = helper.getLevel().getBlockEntity(rated.getBlockPos());
        helper.assertTrue(glassState.getBlock() instanceof GlassFluidPipeBlock &&
            FluidVariantBehaviour.get(glassBE).rate("fluid_rate") == .75,
            "Glass conversion lost the rated pipe or replaced its material settings");
        var requirement = ((GlassFluidPipeBlock) glassState.getBlock()).getRequiredItems(glassState, glassBE);
        helper.assertTrue(!requirement.isInvalid() && requirement.getRequiredItems().get(0).matches(crafted) &&
            !requirement.getRequiredItems().get(0).matches(AllBlocks.FLUID_PIPE.asStack()),
            "Native glass-pipe schematic did not require the configured base pipe");
        helper.assertTrue(((GlassFluidPipeBlock) glassState.getBlock()).onWrenched(glassState, wrench).consumesAction(),
            "Native wrench could not revert the glass pipe");
        helper.assertTrue(FluidVariantBehaviour.get(helper.getLevel().getBlockEntity(rated.getBlockPos()))
            .rate("fluid_rate") == .75, "Reverse wrench conversion lost material settings");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void malformedAndOversizedPipeSettingsFallBackWithoutCopyingUnboundedTag(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("better_manufacturing"),
            "Real native fluid variant behaviour is required");
        ItemStack crafted = WorkshopVariantTests.craft(helper, "fluid_pipe", "tconstruct:rock#stone");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, AllBlocks.FLUID_PIPE.getDefaultState());
        // helper.setBlock does not run Create's first BE tick; attachment is a native
        // lifecycle event, not a fixture call to initialize()/addBehaviours().
        helper.runAtTickTime(3, () -> {
            var pipe = (FluidPipeBlockEntity) helper.getBlockEntity(pos);
            var variant = FluidVariantBehaviour.get(pipe);
            helper.assertTrue(variant != null, "Fluid behaviour was not attached after native BE ticks");
            String key = "better_manufacturing:settings";
            var wrongOutput = crafted.getTag().copy();
            wrongOutput.getCompound(key).putString("output", "create:spout");
            variant.install(wrongOutput);
            helper.assertTrue(variant.settings() == null && variant.rate("fluid_rate") == 1,
                "Wrong-output settings changed native pipe pressure");
            var saved = new CompoundTag();
            saved.putString("UnrelatedFluidMetadata", "preserved");
            variant.writeSafe(saved);
            helper.assertTrue(saved.getString("UnrelatedFluidMetadata").equals("preserved") &&
                saved.getCompound(key).getString("output").equals("create:spout"),
                "Bounded invalid record or unrelated NBT was destroyed");
            var malformed = new CompoundTag();
            malformed.putString(key, "old_version");
            variant.install(malformed);
            var bounded = new CompoundTag();
            variant.writeSafe(bounded);
            helper.assertTrue(variant.settings() == null && bounded.getString(key).equals("old_version"),
                "Bounded malformed settings must survive a native drop with baseline behavior");
            var oversized = new CompoundTag();
            oversized.putString(key, "x".repeat(4096));
            variant.install(oversized);
            var discarded = new CompoundTag();
            variant.writeSafe(discarded);
            helper.assertTrue(variant.settings() == null && variant.rate("fluid_rate") == 1 && !discarded.contains(key),
                "Oversized settings were copied into a native save or affected work");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ratedTankItemKeepsContentsThroughNativePlacement(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Fluid tank placement needs real Create and TCon");
        ItemStack crafted = WorkshopVariantTests.craft(helper, "fluid_tank", "tconstruct:copper");
        BlockPos pos = new BlockPos(1, 1, 1);
        var firstPlace = ((BlockItem) crafted.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), helper.absolutePos(pos), Direction.WEST, crafted.copy(), Direction.UP));
        helper.assertTrue(firstPlace.consumesAction(), "Rated native tank placement failed");
        var tank = (FluidTankBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(FluidVariantBehaviour.get(tank).rate("fluid_capacity") == 1.15,
            "Copper tank capacity was not installed");
        helper.assertTrue(tank.getTankInventory().fill(new FluidStack(Fluids.WATER, 1100),
            IFluidHandler.FluidAction.EXECUTE) == 1100, "Rated tank could not hold 1100 mB");
        ItemStack carried = crafted.copy();
        carried.getOrCreateTag().put("BlockEntityTag", tank.saveWithoutMetadata());
        helper.setBlock(pos, Blocks.AIR);
        var secondPlace = ((BlockItem) carried.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), helper.absolutePos(pos), Direction.WEST, carried, Direction.UP));
        helper.assertTrue(secondPlace.consumesAction(), "Filled native tank item could not be placed");
        var restored = (FluidTankBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(FluidVariantBehaviour.get(restored).rate("fluid_capacity") == 1.15,
            "Filled tank placement lost the crafted capacity rating");
        helper.assertTrue(restored.getTankInventory().getFluidAmount() == 1100,
            "Create fluid tank item placement discarded 100 mB above native capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void airtightBasinKeepsFluidAndBothIndependentSettings(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("better_airtight_machines") && ModList.get().isLoaded("create"),
            "Real Airtight and Create are mandatory for the combined basin lifecycle");
        ItemStack crafted = WorkshopVariantTests.craft(helper, "basin", "tconstruct:copper");
        BlockPos pos = new BlockPos(1, 1, 1);
        var placed = ((BlockItem) crafted.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), helper.absolutePos(pos), Direction.WEST, crafted.copy(), Direction.UP));
        helper.assertTrue(placed.consumesAction(), "Rated basin placement failed");
        var basin = (BasinBlockEntity) helper.getBlockEntity(pos);
        var rated = FluidVariantBehaviour.get(basin);
        helper.assertTrue(rated != null && rated.rate("fluid_capacity") == 1.15,
            "Copper liner did not set the native basin tank capacity");
        var input = basin.getTanks().get(false).getPrimaryHandler();
        helper.assertTrue(input.getCapacity() == 1150, "Basin input tank was not actually resized");
        helper.assertTrue(input.fill(new FluidStack(Fluids.WATER, 1100), IFluidHandler.FluidAction.EXECUTE) == 1100,
            "Rated basin refused fluid above its native 1000 mB baseline");
        var airtight = (AirtightUpgradeHolder) basin;
        airtight.betterContentFixes$setAirtight(true);
        var loaded = basin.getType().create(basin.getBlockPos(), basin.getBlockState());
        loaded.setLevel(helper.getLevel());
        loaded.load(basin.saveWithFullMetadata());
        var restored = (BasinBlockEntity) loaded;
        helper.assertTrue(((AirtightUpgradeHolder) restored).isAirtight(), "Native Airtight seal was lost on basin reload");
        helper.assertTrue(FluidVariantBehaviour.get(restored).rate("fluid_capacity") == 1.15,
            "Basin material setting was lost on Airtight reload");
        helper.assertTrue(restored.getTanks().get(false).getPrimaryHandler().getFluidAmount() == 1100,
            "Airtight/material coexisting reload deleted contained fluid");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void portableInterfaceSharesTransferBudgetAcrossHandlers(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Portable fluid interface needs real Create and TCon");
        ItemStack crafted = WorkshopVariantTests.craft(helper, "portable_fluid_interface", "tconstruct:rock#stone");
        BlockPos pos = new BlockPos(1, 1, 1);
        var placed = ((BlockItem) crafted.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), helper.absolutePos(pos), Direction.WEST, crafted.copy(), Direction.UP));
        helper.assertTrue(placed.consumesAction(), "Portable fluid interface placement failed");
        var portable = (PortableFluidInterfaceBlockEntity) helper.getBlockEntity(pos);
        var variant = FluidVariantBehaviour.get(portable);
        helper.assertTrue(variant != null && variant.rate("fluid_rate") == .75,
            "Portable fluid interface did not receive the cheap fitting rating");
        var first = portable.new InterfaceFluidHandler(new FluidTank(10000));
        var second = portable.new InterfaceFluidHandler(new FluidTank(10000));
        helper.assertTrue(FluidNativeRates.interfaceLimit(first) == 750, "Rated native handler budget is not 750 mB");
        FluidNativeRates.transferred(first, 400);
        helper.assertTrue(FluidNativeRates.interfaceLimit(first) == 350 && FluidNativeRates.interfaceLimit(second) == 350,
            "Different native handler instances bypassed the same block transfer budget");
        helper.assertTrue(FluidNativeRates.interfaceLimit(first) == 350,
            "Simulation-style budget inspection must not consume fluid allowance");
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(FluidNativeRates.interfaceLimit(second) == 750,
                "Native budget did not replenish on the next server tick");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void heatPipeConsumesRealPlateAndRetainsNativeHeatLimit(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("better_industrial_heat") && ModList.get().isLoaded("tconstruct"),
            "Industrial Heat and TCon are mandatory in the fluids profile");
        var found = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("better_industrial_heat:heat_pipe")).orElseThrow();
        helper.assertTrue(found instanceof PartVariantRecipe,
            "Effective heat-pipe recipe must be the real pack PartVariantRecipe, not the IH native ShapedRecipe: "
                + found.getClass().getName());
        var recipe = (PartVariantRecipe) found;
        var grid = CreateDrillTests.grid();
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            var options = recipe.getIngredients().get(slot).getItems();
            helper.assertTrue(options.length > 0, "Missing native pack ingredient at slot " + slot);
            grid.setItem(slot, options[0].copy());
        }
        for (var binding : recipe.parts()) {
            helper.assertTrue(binding.role().equals("thermal_surface"), "Unexpected heat pipe part role");
            int slot = binding.slot();
            helper.assertTrue(grid.getItem(slot).getItem() instanceof ToolPartItem, "Thermal surface must consume large plate");
            var plate = (ToolPartItem) grid.getItem(slot).getItem();
            grid.setItem(slot, plate.withMaterial(MaterialVariantId.parse("tconstruct:rock#stone")));
        }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()),
            "Active heat-pipe PartVariantRecipe rejected real plate and native ingredients");
        var selected = helper.getLevel().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        helper.assertTrue(selected.getId().equals(recipe.getId()) && selected instanceof PartVariantRecipe,
            "Ordinary crafting selected a different heat-pipe recipe: " + selected.getId());
        ItemStack crafted = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(!crafted.isEmpty() && crafted.getCount() == 4 &&
            ForgeRegistries.ITEMS.getKey(crafted.getItem()).toString().equals("better_industrial_heat:heat_pipe"),
            "Effective heat pipe recipe must consume a real cheap plate and yield four pipes");
        // Use Forge/vanilla's actual result-slot take path, which decrements each
        // ingredient after the active recipe supplies its container remainders.
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var result = new ResultContainer();
        result.setRecipeUsed(recipe);
        new ResultSlot(player, grid, result, 0, 0, 0).onTake(player, crafted.copy());
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            if (!recipe.getIngredients().get(slot).isEmpty())
                helper.assertTrue(grid.getItem(slot).isEmpty(), "Ordinary craft returned consumed heat-pipe input " + slot);
        }
        BlockPos pos = new BlockPos(1, 1, 1);
        var placed = ((BlockItem) crafted.getItem()).place(new DirectionalPlaceContext(
            helper.getLevel(), helper.absolutePos(pos), Direction.WEST, crafted.copy(), Direction.UP));
        helper.assertTrue(placed.consumesAction(), "Rated heat pipe failed native placement");
        var pipe = (HeatPipeBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(pipe.transferRatio() == .75f && pipe.envelopeRatio() == .75f,
            "Industrial Heat listener did not apply baked rock thermal settings");
        helper.assertTrue(pipe.maxHeat() == .75f * (float) HeatSyncConfig.INSTANCE.pipeMaxHeat(),
            "Rated pipe buffer did not preserve native capacity ceiling");
        pipe.setHeat(250f);
        var restored = pipe.getType().create(pipe.getBlockPos(), pipe.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.load(pipe.saveWithFullMetadata());
        helper.assertTrue(((HeatPipeBlockEntity) restored).getHeat() == 250f
            && ((HeatPipeBlockEntity) restored).transferRatio() == .75f,
            "Native heat and bounded surface did not survive save/load");
        var drops = Block.getDrops(pipe.getBlockState(), helper.getLevel(), pipe.getBlockPos(), pipe);
        helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(crafted, drops.get(0)),
            "Heat pipe loot did not preserve final settings");
        helper.succeed();
    }
}
