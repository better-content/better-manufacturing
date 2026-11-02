package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.AssemblyRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import me.desht.pneumaticcraft.common.block.entity.AssemblyDrillBlockEntity;
import me.desht.pneumaticcraft.common.block.entity.AssemblyLaserBlockEntity;
import me.desht.pneumaticcraft.common.block.entity.ThermopneumaticProcessingPlantBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.ItemStackHandler;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsValveTests {
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    private static ItemStack item(String id) {
        var value = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (value == null || value == net.minecraft.world.item.Items.AIR) throw new AssertionError("Required native item absent: " + id);
        return new ItemStack(value);
    }
    private static ItemStack plate(String material) {
        return ((ToolPartItem) item("tconstruct:large_plate").getItem()).withMaterial(MaterialVariantId.parse(material));
    }
    private static PartVariantRecipe blankRecipe(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("better_manufacturing:pneupressure_valve_blank")).orElseThrow();
        helper.assertTrue(recipe instanceof PartVariantRecipe, "Production valve-blank recipe was not loaded as a part variant");
        return (PartVariantRecipe) recipe;
    }
    private static ItemStack blank(GameTestHelper helper, String material, boolean mirror) {
        var recipe = blankRecipe(helper); var input = grid();
        input.setItem(4, mirror ? plate(material) : item("pneumaticcraft:compressed_iron_block"));
        input.setItem(5, mirror ? item("pneumaticcraft:compressed_iron_block") : plate(material));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Shifted/mirrored valve blank failed native grid match");
        var preview = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.isSameItemSameTags(preview, recipe.assemble(input, helper.getLevel().registryAccess())), "Valve-blank preview changed final properties");
        helper.assertTrue(preview.is(item("better_manufacturing:compressed_iron_valve_blank").getItem()) && preview.getCount() == 1,
            "Blank lost physical compressed-iron block and plate cost");
        helper.assertTrue(recipe.getIngredients().size() == 2 && recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
            "Blank returned a plate or compressed iron block");
        return preview;
    }
    @GameTest(template = "pneu_workshop")
    public static void nativeAssemblyDrillsTwentyValvesWithBoundedBody(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Required real PNCR/TCon profile absent");
        var recipe = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("pneumaticcraft:assembly/pressure_chamber_valve")).orElseThrow();
        helper.assertTrue(recipe instanceof AssemblyRecipe, "Native PNCR assembly-drill serializer absent");
        var assembly = (AssemblyRecipe) recipe;
        helper.assertTrue(assembly.getProgramType() == AssemblyRecipe.AssemblyProgramType.DRILL
            && assembly.getOutput().is(item("pneumaticcraft:pressure_chamber_valve").getItem())
            && assembly.getOutput().getCount() == 20 && assembly.getInputAmount() == 1,
            "Native valve drill program or 20-valve gate changed");
        var cheap = blank(helper, "tconstruct:wood#oak", false);
        var baseline = blank(helper, "tconstruct:iron", true);
        helper.assertTrue(assembly.matches(cheap) && assembly.matches(baseline)
            && !assembly.matches(item("pneumaticcraft:compressed_iron_block")), "Original block bypasses material-dependent assembly");
        var cheapSettings = SettingsNbt.read(cheap.getTag(), "better_manufacturing:compressed_iron_valve_blank");
        var normalSettings = SettingsNbt.read(baseline.getTag(), "better_manufacturing:compressed_iron_valve_blank");
        helper.assertTrue(cheapSettings != null && normalSettings != null
            && cheapSettings.properties().get("pressure_envelope") == .75
            && normalSettings.properties().get("pressure_envelope") == 1,
            "Pack wood/iron plates did not bake distinct pressure envelopes");
        var pos = new BlockPos(2, 2, 2);
        var drillBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:assembly_drill"));
        helper.assertTrue(drillBlock != null, "Native assembly drill block missing");
        helper.setBlock(pos, drillBlock.defaultBlockState());
        var drill = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(drill instanceof AssemblyDrillBlockEntity, "Native assembly drill BE missing");
        // Invoke the genuine PNCR assembly lookup (and its applied mixin), not a hand-written result simulator.
        var lookup = AssemblyDrillBlockEntity.class.getDeclaredMethod("getDrilledOutputForItem", ItemStack.class);
        lookup.setAccessible(true);
        var result = (ItemStack) lookup.invoke(drill, cheap);
        var settings = SettingsNbt.read(result.getTag(), "pneumaticcraft:pressure_chamber_valve");
        helper.assertTrue(result.getCount() == 20 && settings != null && settings.properties().get("pressure_envelope") == .75,
            "Native assembly result lost material body setting or its twenty outputs");
        var ordinary = (ItemStack) lookup.invoke(drill, item("pneumaticcraft:compressed_iron_block"));
        helper.assertTrue(ordinary.isEmpty(), "Unprepared native block still produces unconfigured valves");
        var malformed = cheap.copy(); malformed.getOrCreateTag().getCompound(SettingsNbt.KEY).putString("output", "pneumaticcraft:pressure_tube");
        var unconfigured = (ItemStack) lookup.invoke(drill, malformed);
        helper.assertTrue(unconfigured.getCount() == 20 && SettingsNbt.read(unconfigured.getTag(), "pneumaticcraft:pressure_chamber_valve") == null,
            "Malformed saved blank settings were promoted into a rated valve");
        var outOfRange = cheap.copy();
        outOfRange.getOrCreateTag().getCompound(SettingsNbt.KEY).getCompound("properties").putDouble("pressure_envelope", 1.5);
        var downgraded = (ItemStack) lookup.invoke(drill, outOfRange);
        helper.assertTrue(downgraded.getCount() == 20 && SettingsNbt.read(downgraded.getTag(), "pneumaticcraft:pressure_chamber_valve") == null,
            "Out-of-range pressure on blank was promoted to valve");
        var wrongProperty = cheap.copy();
        wrongProperty.getOrCreateTag().getCompound(SettingsNbt.KEY).getCompound("properties").putDouble("rotation_rate", 1.5);
        var wrong = (ItemStack) lookup.invoke(drill, wrongProperty);
        helper.assertTrue(wrong.getCount() == 20 && SettingsNbt.read(wrong.getTag(), "pneumaticcraft:pressure_chamber_valve") == null,
            "Extra property on blank was laundered into rated valve");
        helper.succeed();
    }

    @GameTest(template = "pneu_workshop")
    public static void ratedValveContinuesThroughNativeTwentyToEightTubeLaser(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Actual PNCR/TCon required");
        var laserRecipe = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("pneumaticcraft:assembly/advanced_pressure_tube")).orElseThrow();
        helper.assertTrue(laserRecipe instanceof AssemblyRecipe, "Native valve-to-tube laser recipe missing");
        var laserAssembly = (AssemblyRecipe) laserRecipe;
        helper.assertTrue(laserAssembly.getProgramType() == AssemblyRecipe.AssemblyProgramType.LASER
            && laserAssembly.getInputAmount() == 20 && laserAssembly.getOutput().getCount() == 8,
            "Native 20-valve to 8-tube gate or yield changed");
        var drillBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:assembly_drill"));
        var laserBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:assembly_laser"));
        helper.assertTrue(drillBlock != null && laserBlock != null, "Native drill/laser missing");
        var drillPos = new BlockPos(2, 2, 2); var laserPos = new BlockPos(5, 2, 2);
        helper.setBlock(drillPos, drillBlock.defaultBlockState());
        helper.setBlock(laserPos, laserBlock.defaultBlockState());
        var drill = helper.getLevel().getBlockEntity(helper.absolutePos(drillPos));
        var laser = helper.getLevel().getBlockEntity(helper.absolutePos(laserPos));
        helper.assertTrue(drill instanceof AssemblyDrillBlockEntity && laser instanceof AssemblyLaserBlockEntity,
            "Native drill/laser block entities missing");
        var drillLookup = AssemblyDrillBlockEntity.class.getDeclaredMethod("getDrilledOutputForItem", ItemStack.class);
        var laserLookup = AssemblyLaserBlockEntity.class.getDeclaredMethod("getLaseredOutputForItem", ItemStack.class);
        drillLookup.setAccessible(true); laserLookup.setAccessible(true);
        var valves = (ItemStack) drillLookup.invoke(drill, blank(helper, "tconstruct:wood#oak", false));
        helper.assertTrue(valves.getCount() == 20 && laserAssembly.matches(valves), "Rated valves lost native laser eligibility");
        var tubes = (ItemStack) laserLookup.invoke(laser, valves);
        var settings = SettingsNbt.read(tubes.getTag(), "pneumaticcraft:advanced_pressure_tube");
        helper.assertTrue(tubes.getCount() == 8 && settings != null && settings.properties().get("pressure_envelope") == .75,
            "Native laser did not carry consumed valve construction into eight tubes");
        var invalid = valves.copy();
        invalid.getOrCreateTag().getCompound(SettingsNbt.KEY).getCompound("properties").putDouble("pressure_envelope", 1.5);
        var fallback = (ItemStack) laserLookup.invoke(laser, invalid);
        helper.assertTrue(fallback.getCount() == 8 && SettingsNbt.read(fallback.getTag(), "pneumaticcraft:advanced_pressure_tube") == null,
            "Invalid valve pressure laundered into advanced tube");
        helper.succeed();
    }

    @GameTest(template = "pneu_workshop")
    public static void thermoTubeLastInputAndIncompatibleOutputKeepNativeCosts(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft"), "Real PNCR required");
        var recipe = helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("pneumaticcraft:thermo_plant/reinforced_pressure_tube")).orElseThrow();
        helper.assertTrue(recipe instanceof ThermoPlantRecipe thermo && thermo.getInputFluid().getAmount() == 50,
            "Loaded native 50 mB plastic reinforced-tube recipe absent");
        var nativeBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:thermopneumatic_processing_plant"));
        var plastic = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("pneumaticcraft:plastic"));
        helper.assertTrue(nativeBlock != null && plastic != null, "Native thermo plant or plastic absent");
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, nativeBlock.defaultBlockState());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(be instanceof ThermopneumaticProcessingPlantBlockEntity, "Native thermo plant BE absent");
        var plant = (ThermopneumaticProcessingPlantBlockEntity) be;
        var type = ThermopneumaticProcessingPlantBlockEntity.class;
        var recipeField = type.getDeclaredField("currentRecipe"); recipeField.setAccessible(true);
        var progressField = type.getDeclaredField("craftingProgress"); progressField.setAccessible(true);
        var inputField = type.getDeclaredField("inputItemHandler"); inputField.setAccessible(true);
        var inputs = (ItemStackHandler) inputField.get(plant);
        var output = plant.getOutputInventory();
        var cycle = type.getDeclaredMethod("runOneCycle"); cycle.setAccessible(true);
        recipeField.set(plant, recipe);
        helper.assertTrue(output.getStackInSlot(0).isEmpty() && inputs.getStackInSlot(0).isEmpty(),
            "Thermo plant started with unexpected inventory");
        var wood = item("pneumaticcraft:pressure_tube");
        SettingsNbt.write(wood.getOrCreateTag(), new MachineSettings("pneumaticcraft:pressure_tube",
            Map.of("pressure_envelope", .75), Map.of("body", "tconstruct:wood#oak"), null));
        inputs.setStackInSlot(0, wood); // Exactly the final one-item input.
        helper.assertTrue(plant.getInputTank().fill(new FluidStack(plastic, 50), FluidAction.EXECUTE) == 50,
            "Real native plastic tank rejected recipe cost");
        progressField.setInt(plant, 6000);
        // Execute PNCR's real completion path, not a simulated output helper. The private
        // call bypasses tickServer heat/pressure gating, which remains separately unproven.
        cycle.invoke(plant);
        var result = output.getStackInSlot(0);
        var rated = SettingsNbt.read(result.getTag(), "pneumaticcraft:reinforced_pressure_tube");
        helper.assertTrue(result.is(item("pneumaticcraft:reinforced_pressure_tube").getItem()) && result.getCount() == 1
            && rated != null && rated.properties().equals(Map.of("pressure_envelope", .75))
            && rated.materials().equals(Map.of("body", "tconstruct:wood#oak"))
            && inputs.getStackInSlot(0).isEmpty() && plant.getInputTank().getFluidAmount() == 0,
            "Last input lost metadata or native 50 mB/item consumption was bypassed");
        var iron = item("pneumaticcraft:pressure_tube");
        SettingsNbt.write(iron.getOrCreateTag(), new MachineSettings("pneumaticcraft:pressure_tube",
            Map.of("pressure_envelope", 1.0), Map.of("body", "tconstruct:iron"), null));
        inputs.setStackInSlot(0, iron);
        helper.assertTrue(plant.getInputTank().fill(new FluidStack(plastic, 50), FluidAction.EXECUTE) == 50,
            "Native plastic tank rejected blocked-output cost setup");
        progressField.setInt(plant, 6000);
        cycle.invoke(plant);
        helper.assertTrue(ItemStack.isSameItemSameTags(output.getStackInSlot(0), result)
            && output.getStackInSlot(0).getCount() == 1 && ItemStack.isSameItemSameTags(inputs.getStackInSlot(0), iron)
            && plant.getInputTank().getFluidAmount() == 50 && progressField.getInt(plant) == 6000,
            "Incompatible prefilled output consumed last item/plastic or replaced its rated identity");
        helper.succeed();
    }
}
