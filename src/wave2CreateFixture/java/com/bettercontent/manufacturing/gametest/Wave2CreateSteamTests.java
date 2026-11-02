package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.createbulk.BulkPartSettings;
import com.bettercontent.manufacturing.integration.createbulk.SteamVariantBehaviour;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;

/** Native shaft respects rated engine capacity only after its native boiler supplies efficiency. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateSteamTests {
    private Wave2CreateSteamTests() {}

    @GameTest(template = "drill_workshop", timeoutTicks = 100)
    public static void nativeShaftCapacityRequiresSupplyAndPersistsRatedEngine(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create/TConstruct dependencies required");
        var handle = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tough_handle"));
        helper.assertTrue(handle instanceof ToolPartItem, "Missing actual native piston-rod part");
        // Create's facing points from the tank towards the piston. A perpendicular
        // plain shaft TWO blocks away is converted to a powered shaft by engine onPlace.
        var enginePos = new BlockPos(6, 3, 5);
        var state = AllBlocks.STEAM_ENGINE.getDefaultState();
        var facing = SteamEngineBlock.getFacing(state);
        var shaftPos = enginePos.relative(facing, 2);
        var shaftAxis = facing.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        helper.setBlock(enginePos.relative(facing.getOpposite()), AllBlocks.FLUID_TANK.getDefaultState());
        helper.setBlock(shaftPos, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, shaftAxis));
        helper.setBlock(enginePos, state);
        helper.runAtTickTime(5, () -> {
            var engineBlock = helper.getLevel().getBlockEntity(helper.absolutePos(enginePos));
            var shaftBlock = helper.getLevel().getBlockEntity(helper.absolutePos(shaftPos));
            helper.assertTrue(engineBlock instanceof SteamEngineBlockEntity, "Native steam engine BE missing after ticks");
            helper.assertTrue(shaftBlock instanceof PoweredShaftBlockEntity,
                "Native engine did not convert perpendicular shaft at two-block offset");
            var engine = (SteamEngineBlockEntity) engineBlock;
            var shaft = (PoweredShaftBlockEntity) shaftBlock;
            var variant = SteamVariantBehaviour.get(engine);
            helper.assertTrue(variant != null, "Native steam engine tick did not attach variant behaviour");
            helper.assertTrue(engine.getTank() != null && engine.getShaft() == shaft,
                "Native steam engine did not connect to its tank and powered shaft");
            helper.assertTrue(shaft.calculateAddedStressCapacity() == 0, "Unpowered steam shaft generated free capacity");
            double[] capacity = new double[2];
            for (int i = 0; i < 2; i++) {
                var material = i == 0 ? "tconstruct:wood#oak" : "tconstruct:iron";
                var part = ((ToolPartItem) handle).withMaterial(MaterialVariantId.parse(material));
                var settings = BulkPartSettings.bake("create:steam_engine", BulkPartSettings.STEAM, Map.of("piston_rod", part));
                var stack = AllBlocks.STEAM_ENGINE.asStack(); SettingsNbt.write(stack.getOrCreateTag(), settings);
                variant.install(stack.getTag());
                helper.assertTrue(settings.equals(variant.settings()), "Engine did not install crafted fixed settings");
                // Supply the existing native shaft input, not a new generator; zero remains zero.
                shaft.update(engine.getBlockPos(), 0, .5f);
                capacity[i] = shaft.calculateAddedStressCapacity();
                helper.assertTrue(capacity[i] > 0, "Native supplied shaft did not produce capacity");
                var saved = engine.saveWithFullMetadata();
                variant.install(new CompoundTag()); // Simulate unloaded settings before native NBT load.
                engine.load(saved);
                helper.assertTrue(settings.equals(variant.settings()), "Native steam engine save/load lost setting");
                var drops = Block.getDrops(engine.getBlockState(), helper.getLevel(), engine.getBlockPos(), engine);
                helper.assertTrue(drops.size() == 1 && net.minecraft.world.item.ItemStack.isSameItemSameTags(stack, drops.get(0)),
                    "Native steam engine drop lost its fixed setting");
            }
            helper.assertTrue(capacity[0] < capacity[1] && Math.abs(capacity[0] / capacity[1] - .75) < .01,
                "Rated native steam shaft ignored material or created capacity for free");
            shaft.update(engine.getBlockPos(), 0, 0);
            helper.assertTrue(shaft.calculateAddedStressCapacity() == 0, "Engine still generated stress with native supply removed");
            helper.succeed();
        });
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 170)
    public static void fueledAndWaterFedBoilerActuallyPowersRatedEngineThenDriesAndRecovers(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create/TConstruct dependencies required for active boiler");
        var handle = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tough_handle"));
        helper.assertTrue(handle instanceof ToolPartItem, "Missing native rated piston-rod part");
        var enginePos = new BlockPos(6, 3, 5);
        var state = AllBlocks.STEAM_ENGINE.getDefaultState();
        var facing = SteamEngineBlock.getFacing(state);
        helper.assertTrue(facing == Direction.UP, "Boiler fixture requires native floor-mounted steam engine");
        var tankPos = enginePos.below();
        var burnerPos = tankPos.below();
        var shaftPos = enginePos.relative(facing, 2);
        // The real 2x2 multiblock has size four: enough for one actively heated engine.
        // SMOULDERING is an unfuelled captured blaze, not a fabricated heat level.
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
            helper.setBlock(tankPos.offset(dx, 0, dz), AllBlocks.FLUID_TANK.getDefaultState());
        helper.setBlock(burnerPos, AllBlocks.BLAZE_BURNER.getDefaultState()
            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        helper.setBlock(shaftPos, AllBlocks.SHAFT.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(enginePos, state);
        var engine = new SteamEngineBlockEntity[1];
        var shaft = new PoweredShaftBlockEntity[1];
        var tank = new FluidTankBlockEntity[1];
        var capacity = new float[2];
        helper.runAtTickTime(25, () -> {
            var be = helper.getLevel().getBlockEntity(helper.absolutePos(enginePos));
            var shaftBe = helper.getLevel().getBlockEntity(helper.absolutePos(shaftPos));
            helper.assertTrue(be instanceof SteamEngineBlockEntity && shaftBe instanceof PoweredShaftBlockEntity,
                "Native boiler engine/converted perpendicular two-offset shaft did not initialize");
            engine[0] = (SteamEngineBlockEntity) be;
            shaft[0] = (PoweredShaftBlockEntity) shaftBe;
            helper.assertTrue(engine[0].getShaft() == shaft[0] && engine[0].getTank() != null,
                "Floor engine failed native tank/shaft geometry");
            tank[0] = engine[0].getTank().getControllerBE();
            helper.assertTrue(tank[0] != null && tank[0].getWidth() == 2 && tank[0].getHeight() == 1
                && tank[0].getTotalTankSize() == 4 && tank[0].boiler.attachedEngines == 1
                && tank[0].boiler.isActive(), "Native four-tank boiler did not attach one steam engine");
            helper.assertTrue(shaft[0].calculateAddedStressCapacity() == 0,
                "Unheated, dry boiler produced free native shaft capacity");
            var burnerBe = helper.getLevel().getBlockEntity(helper.absolutePos(burnerPos));
            helper.assertTrue(burnerBe instanceof BlazeBurnerBlockEntity, "Real blaze-burner heater missing");
            var coal = new ItemStack(Items.COAL);
            var fuel = BlazeBurnerBlock.tryInsert(helper.getLevel().getBlockState(helper.absolutePos(burnerPos)),
                helper.getLevel(), helper.absolutePos(burnerPos), coal, false, false, false);
            helper.assertTrue(fuel.getResult().consumesAction() && coal.isEmpty()
                && ((BlazeBurnerBlockEntity) burnerBe).getRemainingBurnTime() > 0,
                "Real coal was not consumed by native burner fuel insertion");
            var burning = helper.getLevel().getBlockState(helper.absolutePos(burnerPos));
            helper.assertTrue(burning.getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.KINDLED
                && BoilerHeater.findHeat(helper.getLevel(), helper.absolutePos(burnerPos), burning) >= 1,
                "Native burner did not emit actual boiler heat after consuming coal");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(tank[0].boiler.waterSupply == 0 && shaft[0].calculateAddedStressCapacity() == 0,
                "Heated but unfed boiler generated free shaft capacity");
            // The boiler's own active fluid capability consumes supplied water into
            // gatheredSupply; filling a plain tank would not prove boiler throughput.
            var handler = tank[0].getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
            helper.assertTrue(handler instanceof com.simibubi.create.content.fluids.tank.BoilerData.BoilerFluidHandler,
                "Active boiler did not expose its native water-input capability");
            helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "Native operating boiler rejected 1000 mB of real water");
        });
        helper.runAtTickTime(45, () -> {
            var boiler = tank[0].boiler;
            helper.assertTrue(boiler.waterSupply > 0 && boiler.activeHeat > 0
                && boiler.getMaxHeatLevelForWaterSupply() >= 1
                && boiler.getEngineEfficiency(tank[0].getTotalTankSize()) > 0,
                "Native boiler did not combine sampled water, burnt fuel and size into engine efficiency: supply="
                    + boiler.waterSupply + " heat=" + boiler.activeHeat);
            helper.assertTrue(shaft[0].engineEfficiency > 0 && shaft[0].calculateAddedStressCapacity() > 0
                && Math.abs(shaft[0].getGeneratedSpeed()) > 0,
                "Actual operating boiler failed to drive native shaft and stress capacity");
            var part = ((ToolPartItem) handle).withMaterial(MaterialVariantId.parse("tconstruct:wood#oak"));
            var wood = BulkPartSettings.bake("create:steam_engine", BulkPartSettings.STEAM, Map.of("piston_rod", part));
            var tag = new CompoundTag(); SettingsNbt.write(tag, wood);
            SteamVariantBehaviour.get(engine[0]).install(tag);
            helper.assertTrue(wood.equals(SteamVariantBehaviour.get(engine[0]).settings()),
                "Wood piston-rod setting did not install on operating engine");
        });
        helper.runAtTickTime(48, () -> {
            capacity[0] = shaft[0].calculateAddedStressCapacity();
            helper.assertTrue(capacity[0] > 0 && shaft[0].engineEfficiency > 0,
                "Wood engine did not produce native heat-and-water-funded stress capacity");
            var part = ((ToolPartItem) handle).withMaterial(MaterialVariantId.parse("tconstruct:iron"));
            var iron = BulkPartSettings.bake("create:steam_engine", BulkPartSettings.STEAM, Map.of("piston_rod", part));
            var tag = new CompoundTag(); SettingsNbt.write(tag, iron);
            SteamVariantBehaviour.get(engine[0]).install(tag);
        });
        helper.runAtTickTime(52, () -> {
            capacity[1] = shaft[0].calculateAddedStressCapacity();
            helper.assertTrue(capacity[1] > capacity[0] && Math.abs(capacity[0] / capacity[1] - .75) < .01,
                "Operating boiler lost real wood/iron 0.75 engine budget: wood="
                    + capacity[0] + " iron=" + capacity[1]);
            var saved = engine[0].saveWithFullMetadata();
            engine[0].load(saved);
            helper.assertTrue(SteamVariantBehaviour.get(engine[0]).settings() != null,
                "Native operating engine save/load lost its rated setting");
        });
        helper.runAtTickTime(57, () -> helper.assertTrue(shaft[0].calculateAddedStressCapacity() > 0
            && Math.abs(shaft[0].calculateAddedStressCapacity() / capacity[1] - 1) < .01,
            "Native engine did not retain supplied iron budget after BE NBT round-trip"));
        helper.runAtTickTime(115, () -> {
            helper.assertTrue(tank[0].boiler.activeHeat > 0 && tank[0].boiler.waterSupply == 0
                && tank[0].boiler.getMaxHeatLevelForWaterSupply() == 0,
                "Coal was exhausted or native boiler still claims water long after feed stopped");
            helper.assertTrue(shaft[0].engineEfficiency == 0 && shaft[0].calculateAddedStressCapacity() == 0,
                "Native engine failed to shut off after water supply ceased despite surviving heat");
            var handler = tank[0].getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
            helper.assertTrue(handler != null && handler.fill(new FluidStack(Fluids.WATER, 1000),
                IFluidHandler.FluidAction.EXECUTE) == 1000, "Native dry boiler refused renewed water input");
        });
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(tank[0].boiler.waterSupply > 0 && tank[0].boiler.activeHeat > 0
                && shaft[0].engineEfficiency > 0 && shaft[0].calculateAddedStressCapacity() > 0
                && Math.abs(shaft[0].calculateAddedStressCapacity() / capacity[1] - 1) < .01,
                "Real fuelled boiler did not recover rated shaft budget after water returned");
            helper.succeed();
        });
    }
}
