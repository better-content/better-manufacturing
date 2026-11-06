package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.tconstruct.NativePartCapture;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.bettercontent.manufacturing.integration.createbulk.BulkNative;
import com.bettercontent.manufacturing.integration.createbulk.BulkWheelRecipe;
import com.bettercontent.manufacturing.integration.createbulk.BulkPartSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInventory;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;

/** Native mechanical recipe (raw 7x7 trimmed to 5x7) and paired-wheel controller. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateBulkTests {
    // Pinned rock head durability 130 / iron 250 clamps to the native .75
    // lower bound; the iron hammer's head mining speed supplies 1x load.
    private static final double NATIVE_ROCK_WHEEL_WORK = .75;
    private Wave2CreateBulkTests() {}

    /** Synthetic persisted boundaries, separate from the real material-craft assertions. */
    private static MachineSettings wheelBoundary(double work, double load) {
        var numeric = new NumericInputs(BulkPartSettings.WHEEL,
            Map.of("hammer", Map.of(Measure.HEAD_MINING_SPEED, new NumericInputs.Stat(work, 1)),
                "plate", Map.of(Measure.HEAD_DURABILITY, new NumericInputs.Stat(load, 1))), Map.of());
        String output = "create:crushing_wheel";
        return new MachineSettings(output, NumericEvaluator.evaluate(output, numeric, null), Map.of(), null, numeric);
    }
    private static MachineSettings launcherBoundary(double recharge, double envelope) {
        var numeric = new NumericInputs(BulkPartSettings.LAUNCHER,
            Map.of("spring", Map.of(Measure.LIMB_DRAW_SPEED, new NumericInputs.Stat(recharge - 1, 0),
                Measure.LIMB_VELOCITY, new NumericInputs.Stat(envelope - 1, 0))), Map.of());
        String output = "create:weighted_ejector";
        return new MachineSettings(output, NumericEvaluator.evaluate(output, numeric, null), Map.of(), null, numeric);
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 100)
    public static void shiftedMechanicalRecipePreservesPairAndNativeWorkBudget(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create/TConstruct dependencies required for wheel native fixture");
        // Production20 removes Create's native ID and reauthors the paid pair
        // under this KubeJS ID using the native mechanical-crafting recipe type.
        var recipeId = new ResourceLocation("kubejs:powered_works/refinement/crushing_wheels");
        var manager = helper.getLevel().getRecipeManager();
        var found = manager.byKey(recipeId).orElseThrow(() -> {
            boolean resource = helper.getLevel().getServer().getResourceManager()
                .getResource(new ResourceLocation("create:recipes/mechanical_crafting/crushing_wheel.json")).isPresent();
            var wheelIds = manager.getAllRecipesFor(AllRecipeTypes.MECHANICAL_CRAFTING.getType()).stream()
                .map(recipe -> recipe.getId().toString()).filter(id -> id.contains("wheel")).toList();
            return new AssertionError("Missing real mechanical wheel recipe: resource=" + resource + " mechanicalIds=" + wheelIds);
        });
        helper.assertTrue(found instanceof BulkWheelRecipe, "Wheel lost native mechanical-crafting serializer");
        var recipe = (BulkWheelRecipe) found;
        // Native ShapedRecipe shrink removes the raw 7x7 pattern's two empty outer columns.
        helper.assertTrue(recipe.getWidth() == 5 && recipe.getHeight() == 7 && recipe.parts().size() == 2
            && recipe.parts().stream().map(BulkWheelRecipe.Part::slot).toList().equals(java.util.List.of(2, 32))
            && recipe.parts().stream().map(BulkWheelRecipe.Part::role).toList().equals(java.util.List.of("hammer", "plate")),
            "Raw wheel part slots were not rebased onto Create's 5x7 mechanical grid");
        CraftingContainer grid = new TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 9, 9);
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            var candidates = ingredient.getItems();
            helper.assertTrue(candidates.length > 0, "Native mechanical wheel ingredient missing at " + i);
            grid.setItem(2 + i % recipe.getWidth() + (1 + i / recipe.getWidth()) * 9, candidates[0].copy());
        }
        for (var binding : recipe.parts()) {
            boolean hammer = binding.role().equals("hammer");
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(hammer
                ? "tconstruct:hammer_head" : "tconstruct:large_plate"));
            helper.assertTrue(item instanceof ToolPartItem, "Wheel material part unavailable");
            grid.setItem(2 + binding.slot() % recipe.getWidth() + (1 + binding.slot() / recipe.getWidth()) * 9,
                ((ToolPartItem) item).withMaterial(MaterialVariantId.parse(hammer ? "tconstruct:iron" : "tconstruct:rock#stone")));
        }
        // Create's native matcher only accepts its own mechanical inventory. Rehydrate a
        // complete shifted crafter grid through Create's real GroupedItems NBT format;
        // vertical coordinates invert when Create builds its crafting container.
        var encoded = new CompoundTag(); var entries = new ListTag();
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
            var entry = new CompoundTag(); entry.putInt("x", x); entry.putInt("y", 8 - y);
            entry.put("item", grid.getItem(x + 9 * y).serializeNBT()); entries.add(entry);
        }
        encoded.put("Grid", entries);
        var grouped = RecipeGridHandler.GroupedItems.read(encoded); grouped.calcStats();
        var nativeGrid = new MechanicalCraftingInventory(grouped);
        helper.assertTrue(nativeGrid.getWidth() == 9 && nativeGrid.getHeight() == 9
            && recipe.matches(nativeGrid, helper.getLevel()), "Shifted native mechanical-crafter matcher rejected supported iron hammer/rock plate");
        int hammerSlot = 2 + recipe.parts().get(0).slot() % recipe.getWidth()
            + (1 + recipe.parts().get(0).slot() / recipe.getWidth()) * 9;
        var hammer = (ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:hammer_head"));
        var supportedHammer = nativeGrid.getItem(hammerSlot).copy();
        // The loaded pack disables the wood head stat entirely. Rock has a real
        // native head and must remain valid; wood must not bypass eligibility.
        nativeGrid.setItem(hammerSlot, hammer.withMaterial(MaterialVariantId.parse("tconstruct:wood#oak")));
        helper.assertTrue(!recipe.matches(nativeGrid, helper.getLevel())
            && recipe.assemble(nativeGrid, helper.getLevel().registryAccess()).isEmpty(),
            "Unsupported pack wood hammer head bypassed native part policy");
        nativeGrid.setItem(hammerSlot, supportedHammer);
        var pair = recipe.assemble(nativeGrid, helper.getLevel().registryAccess());
        var settings = SettingsNbt.read(pair.getTag(), "create:crushing_wheel");
        helper.assertTrue(pair.getCount() == 2 && pair.is(AllBlocks.CRUSHING_WHEEL.asItem())
            && settings != null && Math.abs(settings.rate() - NATIVE_ROCK_WHEEL_WORK) < .001
            && Math.abs(settings.properties().getOrDefault("load", 0.0) - 1) < .001,
            "Native mechanical craft lost its TWO rated wheels or distinct paid load");
        var controllerPos = new BlockPos(4, 2, 4);
        // Native Create controller creation requires two counter-rotating wheels whose
        // shaft axes are perpendicular to the direction between their blocks.
        for (var direction : new Direction[]{Direction.WEST, Direction.EAST})
            helper.setBlock(controllerPos.relative(direction), AllBlocks.CRUSHING_WHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        helper.runAtTickTime(5, () -> {
            var west = helper.getLevel().getBlockEntity(helper.absolutePos(controllerPos.west()));
            var east = helper.getLevel().getBlockEntity(helper.absolutePos(controllerPos.east()));
            helper.assertTrue(west instanceof CrushingWheelBlockEntity && east instanceof CrushingWheelBlockEntity,
                "Native crushing-wheel block entities did not survive ordinary ticks");
            var first = (CrushingWheelBlockEntity) west;
            var second = (CrushingWheelBlockEntity) east;
            var left = KineticVariantBehaviour.get(first);
            var right = KineticVariantBehaviour.get(second);
            helper.assertTrue(left != null && right != null, "Native wheel tick did not attach kinetic behaviours");
            left.install(new CompoundTag()); right.install(new CompoundTag());
            float nativeLeftStress = first.calculateStressApplied(), nativeRightStress = second.calculateStressApplied();
            helper.assertTrue(nativeLeftStress > 0 && nativeRightStress > 0,
                "Native crushing-wheel stress unavailable: west=" + nativeLeftStress + " east=" + nativeRightStress);
            var nativeLeftState = first.getBlockState(); var nativeRightState = second.getBlockState();
            float nativeLeftSpeed = first.getSpeed(), nativeRightSpeed = second.getSpeed();
            // These label-free persisted boundaries test the stress hook, NOT additional
            // craftable materials or recipes. The real iron/rock wheel is restored below.
            for (double[] probe : new double[][]{{.75, .75}, {1, 1.5}}) {
                double workRate = probe[0], probeLoad = probe[1];
                var tag = new CompoundTag();
                SettingsNbt.write(tag, wheelBoundary(workRate, probeLoad));
                left.install(tag); right.install(tag);
                helper.assertTrue(left.settings() != null && right.settings() != null
                    && Math.abs(left.rate() - workRate) < .001 && Math.abs(right.rate() - workRate) < .001,
                    "Valid label-free wheel work/load boundary was rejected");
                helper.assertTrue(first.getBlockState().equals(nativeLeftState) && second.getBlockState().equals(nativeRightState)
                    && first.getSpeed() == nativeLeftSpeed && second.getSpeed() == nativeRightSpeed,
                    "Wheel boundary probe changed native axis/speed instead of only settings");
                float probeLeft = first.calculateStressApplied(), probeRight = second.calculateStressApplied();
                helper.assertTrue(Math.abs(probeLeft / nativeLeftStress - probeLoad) < .001,
                    "West wheel native stress hook missed bounded load: baseline=" + nativeLeftStress
                        + " actual=" + probeLeft + " workRate=" + workRate + " expectedLoad=" + probeLoad);
                helper.assertTrue(Math.abs(probeRight / nativeRightStress - probeLoad) < .001,
                    "East wheel native stress hook missed bounded load: baseline=" + nativeRightStress
                        + " actual=" + probeRight + " workRate=" + workRate + " expectedLoad=" + probeLoad);
            }
            left.install(pair.getTag()); right.install(pair.getTag());
            helper.assertTrue(Math.abs(left.rate() - NATIVE_ROCK_WHEEL_WORK) < .001
                && Math.abs(right.rate() - NATIVE_ROCK_WHEEL_WORK) < .001,
                "Wheel pair lost normally installed settings");
            double load = settings.properties().get("load");
            float ratedLeftStress = first.calculateStressApplied(), ratedRightStress = second.calculateStressApplied();
            helper.assertTrue(Math.abs(ratedLeftStress / nativeLeftStress - load) < .001,
                "West wheel cost did not equal its baked load: native=" + nativeLeftStress
                    + " rated=" + ratedLeftStress + " expectedLoad=" + load + " workRate=" + left.rate());
            helper.assertTrue(Math.abs(ratedRightStress / nativeRightStress - load) < .001,
                "East wheel cost did not equal its baked load: native=" + nativeRightStress
                    + " rated=" + ratedRightStress + " expectedLoad=" + load + " workRate=" + right.rate());
            first.setSpeed(64); second.setSpeed(-64);
            first.fixControllers(); // Create's own pair-validation and controller-creation path.
            var formed = helper.getLevel().getBlockEntity(helper.absolutePos(controllerPos));
            helper.assertTrue(formed instanceof CrushingWheelControllerBlockEntity
                && formed.getBlockState().getValue(CrushingWheelControllerBlock.VALID),
                "Opposite native wheels failed to form valid controller");
            var controller = (CrushingWheelControllerBlockEntity) formed;
            helper.assertTrue(Math.abs(BulkNative.pairWork(controller, 4f) - 4f * NATIVE_ROCK_WHEEL_WORK) < .001,
                "Native paired controller ignored bounded wheel work or stacked two bonuses");
            // Invalid v2 native-stat evidence must fall back as a whole,
            // while the original record survives native block-entity save.
            var invalid = pair.getTag().copy();
            invalid.getCompound(SettingsNbt.KEY).getCompound("parts").getCompound("plate")
                .getCompound("HEAD_DURABILITY").putDouble("reference", 0);
            for (var wheel : new CrushingWheelBlockEntity[]{first, second}) {
                var variant = KineticVariantBehaviour.get(wheel); variant.install(invalid);
                helper.assertTrue(variant.settings() == null
                    && wheel.saveWithFullMetadata().getCompound(SettingsNbt.KEY).getCompound("parts")
                        .getCompound("plate").getCompound("HEAD_DURABILITY").getDouble("reference") == 0,
                    "Invalid wheel stat was applied or destroyed instead of native fallback");
                float baseline = wheel == first ? nativeLeftStress : nativeRightStress;
                helper.assertTrue(Math.abs(wheel.calculateStressApplied() - baseline) <= baseline * .001,
                    "Malformed wheel retained paid stress instead of native fallback: baseline=" + baseline
                        + " actual=" + wheel.calculateStressApplied());
            }
            helper.assertTrue(Math.abs(BulkNative.pairWork(controller, 4f) - 4f) < .001,
                "Malformed wheel pair retained a rated work multiplier");
            helper.setBlock(controllerPos.east(), net.minecraft.world.level.block.Blocks.AIR);
            helper.assertTrue(Math.abs(BulkNative.pairWork(controller, 4f) - 4f) < .001,
                "Single orphan wheel still accelerated native controller");
            helper.succeed();
        });
    }

    // Both wheel rigs reach relative x=12. The 3x3x3 empty template leaves
    // their motors outside its bounds; drill_workshop is 16x6x12, all air,
    // with no entities, so it contains the entire native test assembly.
    @GameTest(template = "drill_workshop", timeoutTicks = 180)
    public static void poweredWheelControllersActuallyProcessOreWithRatedWorkAndStress(GameTestHelper helper) {
        // This is Create's real iron-ore crushing recipe, not the mechanical wheel craft.
        var crushingId = new ResourceLocation("create:crushing/iron_ore");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(crushingId).isPresent(),
            "Actual Create iron-ore crushing recipe missing");
        var result = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:crushed_raw_iron"));
        helper.assertTrue(result != null && result != Items.AIR, "Native guaranteed crushed-iron output missing");
        var finished = new long[]{-1, -1};
        var ejected = new long[]{-1, -1};
        var centers = new BlockPos[]{new BlockPos(4, 2, 4), new BlockPos(11, 2, 4)};
        var controllers = new CrushingWheelControllerBlockEntity[2];
        // Register before GameTest starts ticking. Registering onEachTick from
        // inside tick 10 schedules a second callback for the current tick and
        // can corrupt the test scheduler or add its final check twice.
        helper.onEachTick(() -> {
            for (int i = 0; i < 2; i++) {
                if (controllers[i] == null) continue;
                if (finished[i] < 0) {
                    var inventory = controllers[i].inventory;
                    if (inventory.appliedRecipe) for (int slot = 1; slot < inventory.getSlots(); slot++)
                        if (inventory.getStackInSlot(slot).is(result)
                            && inventory.getStackInSlot(slot).getCount() >= 2) finished[i] = helper.getTick();
                }
                if (finished[i] >= 0 && ejected[i] < 0 && !helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(helper.absolutePos(centers[i])).inflate(2),
                    entity -> entity.getItem().is(result) && entity.getItem().getCount() >= 2).isEmpty())
                    ejected[i] = helper.getTick();
            }
        });
        for (var center : centers) for (var direction : new Direction[]{Direction.WEST, Direction.EAST}) {
            var wheelPos = center.relative(direction);
            helper.setBlock(wheelPos, AllBlocks.CRUSHING_WHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            // Native kinetic source remains attached throughout item processing.
            helper.setBlock(wheelPos.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        }
        helper.runAtTickTime(5, () -> {
            for (var center : centers) for (var direction : new Direction[]{Direction.WEST, Direction.EAST}) {
                var motor = helper.getLevel().getBlockEntity(helper.absolutePos(center.relative(direction).below()));
                helper.assertTrue(motor instanceof CreativeMotorBlockEntity, "Native wheel motor missing after ticks");
                ((CreativeMotorBlockEntity) motor).generatedSpeed.setValue(direction == Direction.WEST ? 64 : -64);
            }
        });
        helper.runAtTickTime(10, () -> {
            // Build the supported iron-hammer/rock-plate pair through Create's actual
            // shifted mechanical inventory and recipe, as in the existing wheel test.
            var found = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation("kubejs:powered_works/refinement/crushing_wheels")).orElseThrow();
            helper.assertTrue(found instanceof BulkWheelRecipe, "Rated wheel craft lost its native serializer");
            var recipe = (BulkWheelRecipe) found;
            CraftingContainer grid = new TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            }, 9, 9);
            for (int i = 0; i < recipe.getIngredients().size(); i++) {
                var ingredient = recipe.getIngredients().get(i);
                if (!ingredient.isEmpty()) {
                    helper.assertTrue(ingredient.getItems().length > 0, "Native wheel ingredient missing at " + i);
                    grid.setItem(2 + i % recipe.getWidth() + (1 + i / recipe.getWidth()) * 9,
                        ingredient.getItems()[0].copy());
                }
            }
            for (var binding : recipe.parts()) {
                var hammer = binding.role().equals("hammer");
                var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(hammer
                    ? "tconstruct:hammer_head" : "tconstruct:large_plate"));
                helper.assertTrue(item instanceof ToolPartItem, "Native rated wheel part missing");
                grid.setItem(2 + binding.slot() % recipe.getWidth() + (1 + binding.slot() / recipe.getWidth()) * 9,
                    ((ToolPartItem) item).withMaterial(MaterialVariantId.parse(hammer
                        ? "tconstruct:iron" : "tconstruct:rock#stone")));
            }
            var encoded = new CompoundTag(); var entries = new ListTag();
            for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
                var entry = new CompoundTag(); entry.putInt("x", x); entry.putInt("y", 8 - y);
                entry.put("item", grid.getItem(x + 9 * y).serializeNBT()); entries.add(entry);
            }
            encoded.put("Grid", entries);
            var grouped = RecipeGridHandler.GroupedItems.read(encoded); grouped.calcStats();
            var nativeGrid = new MechanicalCraftingInventory(grouped);
            helper.assertTrue(recipe.matches(nativeGrid, helper.getLevel()), "Actual rated wheel craft does not match");
            var pair = recipe.assemble(nativeGrid, helper.getLevel().registryAccess());
            var settings = SettingsNbt.read(pair.getTag(), "create:crushing_wheel");
            helper.assertTrue(pair.getCount() == 2 && settings != null
                && Math.abs(settings.rate() - NATIVE_ROCK_WHEEL_WORK) < .001
                && Math.abs(settings.properties().getOrDefault("load", 0.0) - 1) < .001,
                "Crafted pair lost rated work or paid native stress");
            float[] firstStress = new float[2];
            for (int i = 0; i < 2; i++) {
                var center = centers[i];
                var west = helper.getLevel().getBlockEntity(helper.absolutePos(center.west()));
                var east = helper.getLevel().getBlockEntity(helper.absolutePos(center.east()));
                var controller = helper.getLevel().getBlockEntity(helper.absolutePos(center));
                helper.assertTrue(west instanceof CrushingWheelBlockEntity && east instanceof CrushingWheelBlockEntity
                    && controller instanceof CrushingWheelControllerBlockEntity
                    && controller.getBlockState().getValue(CrushingWheelControllerBlock.VALID),
                    "Powered, counter-rotating wheels did not form native controller " + i);
                var left = (CrushingWheelBlockEntity) west; var right = (CrushingWheelBlockEntity) east;
                helper.assertTrue(left.getSpeed() * right.getSpeed() < 0 && Math.abs(left.getSpeed()) == Math.abs(right.getSpeed()),
                    "Wheel pair lacks equal, counter-rotating native kinetic speed " + i);
                helper.assertTrue(KineticVariantBehaviour.get(left) != null && KineticVariantBehaviour.get(right) != null,
                    "Powered wheels did not initialize native setting behaviours");
                if (i == 0) {
                    KineticVariantBehaviour.get(left).install(pair.getTag());
                    KineticVariantBehaviour.get(right).install(pair.getTag());
                }
                firstStress[i] = left.calculateStressApplied() + right.calculateStressApplied();
                helper.assertTrue(firstStress[i] > 0, "Powered wheel pair does not pay native kinetic stress " + i);
                controllers[i] = (CrushingWheelControllerBlockEntity) controller;
            }
            helper.assertTrue(Math.abs(firstStress[0] / firstStress[1] - 1) < .001,
                "Crafted .75 work-rate wheel pair changed native stress instead of retaining paid load=1");
            helper.assertTrue(controllers[0].crushingspeed > 0 && Math.abs(controllers[0].crushingspeed - controllers[1].crushingspeed) < .001,
                "Wheel controllers do not share the same positive native speed");
            for (int i = 0; i < 2; i++) {
                var controller = controllers[i];
                // Two ore make native log2(batchSize)=1; a single item would
                // clamp both speeds to 20 and hide the real rated work delta.
                var before = controller.inventory.getStackInSlot(0).copy();
                if (!before.isEmpty()) {
                    helper.assertTrue(before.is(Items.IRON_ORE) && before.getCount() == 2
                            && !controller.inventory.appliedRecipe && controller.processingEntity == null,
                        "Unexpected preexisting native controller input: " + before);
                    // Remove only the input slot introduced by the shared fixture
                    // before inserting an explicitly owned two-ore batch below.
                    controller.inventory.setStackInSlot(0, ItemStack.EMPTY);
                }
                helper.assertTrue(controller.inventory.isEmpty(), "Native controller has unrelated preexisting output " + i);
                var remainder = controller.inventory.insertItem(0, new ItemStack(Items.IRON_ORE, 2), false);
                helper.assertTrue(remainder.isEmpty(), "Native controller rejected explicit test ore " + i
                    + ": before=" + before + " remainder=" + remainder + " stored=" + controller.inventory.getStackInSlot(0)
                    + " entity=" + controller.processingEntity);
                helper.assertTrue(controller.findRecipe().map(r -> r.getId().equals(crushingId)).orElse(false),
                    "Inserted ore did not select the expected loaded Create crushing recipe " + i);
                helper.assertTrue(controller.inventory.remainingTime > 20, "Native recipe duration was not initialized " + i);
            }
            var startingDuration = controllers[0].inventory.remainingTime;
            helper.runAtTickTime(16, () -> {
                float ratedProgress = startingDuration - controllers[0].inventory.remainingTime;
                float nativeProgress = startingDuration - controllers[1].inventory.remainingTime;
                helper.assertTrue(ratedProgress > 0 && nativeProgress > 0
                    && Math.abs(ratedProgress / nativeProgress - NATIVE_ROCK_WHEEL_WORK) < .015,
                    "Native controller tick did not use paid .75 work at equal input speed: rated="
                        + ratedProgress + " default=" + nativeProgress);
                helper.assertTrue(Math.abs(controllers[0].crushingspeed - controllers[1].crushingspeed) < .001,
                    "Controller progress comparison changed native wheel speed");
            });
            helper.runAtTickTime(130, () -> helper.assertTrue(finished[0] >= 0 && finished[1] >= 0
                && ejected[0] >= 0 && ejected[1] >= 0,
                "Controller did not process AND eject guaranteed crushed iron: rated="
                    + finished[0] + "/" + ejected[0] + " default=" + finished[1] + "/" + ejected[1]));
            helper.succeedWhen(() -> helper.assertTrue(finished[0] > finished[1] && finished[1] > 0
                && ejected[0] > ejected[1] && ejected[1] > 0,
                "Crafted rated pair did not process/eject guaranteed ore yield after native pair: rated="
                    + finished[0] + "/" + ejected[0] + " default=" + finished[1] + "/" + ejected[1]));
        });
    }

    @GameTest(template = "drill_workshop", timeoutTicks = 100)
    public static void ejectorSpringChangesNativeWindupWithMatchingStress(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create/TConstruct dependencies required");
        var limb = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:bow_limb"));
        helper.assertTrue(limb instanceof ToolPartItem, "Missing real native bow limb");
        ItemStack[] prepared = new ItemStack[2];
        for (int i = 0; i < 2; i++) {
            var material = i == 0 ? "tconstruct:wood#oak" : "tconstruct:iron";
            var spring = ((ToolPartItem) limb).withMaterial(MaterialVariantId.parse(material));
            var rated = NativePartCapture.bake(BulkPartSettings.LAUNCHER, Map.of("spring", spring),
                () -> BulkPartSettings.bake("create:weighted_ejector", BulkPartSettings.LAUNCHER, Map.of("spring", spring)));
            prepared[i] = AllBlocks.WEIGHTED_EJECTOR.asStack();
            SettingsNbt.write(prepared[i].getOrCreateTag(), rated);
            helper.setBlock(new BlockPos(2 + i * 4, 2, 3), AllBlocks.WEIGHTED_EJECTOR.getDefaultState());
        }
        helper.runAtTickTime(5, () -> {
            float[] charges = new float[2], stress = new float[2];
            for (int i = 0; i < 2; i++) {
                var block = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2 + i * 4, 2, 3)));
                helper.assertTrue(block instanceof EjectorBlockEntity, "Native ejector block entity missing after ordinary ticks");
                var ejector = (EjectorBlockEntity) block;
                var variant = KineticVariantBehaviour.get(ejector);
                helper.assertTrue(variant != null, "Native ejector tick failed to attach its variant behaviour");
                ejector.setTarget(12, 0);
                ejector.setSpeed(64);
                // Measure the native reference on this exact BE before installing any rating;
                // do not repair a changed target/speed by resetting it after the fact.
                variant.install(new CompoundTag());
                float nativeCharge = ejector.getWindUpSpeed(), nativeStress = ejector.calculateStressApplied();
                float nativeSpeed = ejector.getSpeed();
                var nativeTarget = ejector.getTargetPosition().subtract(ejector.getBlockPos());
                helper.assertTrue(nativeCharge > 0 && nativeStress > 0 && nativeTarget.distManhattan(BlockPos.ZERO) == 12,
                    "Native launcher reference unavailable: charge=" + nativeCharge + " stress=" + nativeStress
                        + " speed=" + nativeSpeed + " target=" + nativeTarget);
                variant.install(prepared[i].getTag());
                helper.assertTrue(variant.settings() != null, "Crafted launcher spring settings were not installed");
                charges[i] = ejector.getWindUpSpeed(); // Native getter with rated spring hook.
                stress[i] = ejector.calculateStressApplied(); // Actual native kinetic cost.
                float ratedSpeed = ejector.getSpeed();
                var ratedTarget = ejector.getTargetPosition().subtract(ejector.getBlockPos());
                if (i == 0) {
                    var invalid = prepared[i].getTag().copy();
                    invalid.getCompound(SettingsNbt.KEY).getCompound("parts").getCompound("spring")
                        .getCompound("LIMB_VELOCITY").putDouble("reference", -1);
                    variant.install(invalid);
                    helper.assertTrue(variant.settings() == null
                        && ejector.saveWithFullMetadata().getCompound(SettingsNbt.KEY).getCompound("parts")
                            .getCompound("spring").getCompound("LIMB_VELOCITY").getDouble("reference") == -1,
                        "Invalid launcher stat applied or lost native saved payload");
                    float invalidCharge = ejector.getWindUpSpeed(), invalidStress = ejector.calculateStressApplied();
                    float invalidSpeed = ejector.getSpeed();
                    var invalidTarget = ejector.getTargetPosition().subtract(ejector.getBlockPos());
                    String measured = "native(charge=" + nativeCharge + ",stress=" + nativeStress + ",speed=" + nativeSpeed
                        + ",target=" + nativeTarget + ") rated(charge=" + charges[i] + ",stress=" + stress[i]
                        + ",speed=" + ratedSpeed + ",target=" + ratedTarget + ") invalid(charge=" + invalidCharge
                        + ",stress=" + invalidStress + ",speed=" + invalidSpeed + ",target=" + invalidTarget
                        + ") rated/native=" + (charges[i] / nativeCharge) + "," + (stress[i] / nativeStress)
                        + " invalid/native=" + (invalidCharge / nativeCharge) + "," + (invalidStress / nativeStress);
                    helper.assertTrue(Math.abs(nativeSpeed - ratedSpeed) < .001 && Math.abs(nativeSpeed - invalidSpeed) < .001
                        && nativeTarget.equals(ratedTarget) && nativeTarget.equals(invalidTarget),
                        "Launcher install changed native speed/target: " + measured);
                    helper.assertTrue(Math.abs(invalidCharge - nativeCharge) <= Math.max(.000001f, nativeCharge * .0001f),
                        "Malformed spring charge did not return to same-BE native reference: " + measured);
                    helper.assertTrue(Math.abs(invalidStress - nativeStress) <= Math.max(.00001f, nativeStress * .0001f),
                        "Malformed spring stress did not return to same-BE native reference: " + measured);
                    var woodSettings = SettingsNbt.read(prepared[i].getTag(), "create:weighted_ejector");
                    helper.assertTrue(woodSettings != null, "Real wood spring lost its baked native limb values");
                    double recharge = woodSettings.properties().get("recharge_rate");
                    double envelope = woodSettings.properties().get("launch_envelope");
                    double expectedCharge = Math.max(.75, Math.min(1.5,
                        recharge * (1 + (envelope - 1) * 11d / 15)));
                    double expectedStress = Math.max(.75, Math.min(1.5, recharge * Math.max(1, envelope)));
                    helper.assertTrue(Math.abs(charges[i] / nativeCharge - expectedCharge) < .001,
                        "Crafted wood spring did not rate native windup at far target: " + measured);
                    helper.assertTrue(Math.abs(stress[i] / nativeStress - expectedStress) < .001
                            && expectedStress >= expectedCharge,
                        "Crafted wood spring did not pay at least its native windup work: " + measured);
                } else {
                    String ironMeasured = "nativeCharge=" + nativeCharge + " ratedCharge=" + charges[i]
                        + " chargeRatio=" + (charges[i] / nativeCharge) + " nativeStress=" + nativeStress
                        + " ratedStress=" + stress[i] + " stressRatio=" + (stress[i] / nativeStress)
                        + " nativeSpeed=" + nativeSpeed + " ratedSpeed=" + ratedSpeed
                        + " nativeTarget=" + nativeTarget + " ratedTarget=" + ratedTarget;
                    helper.assertTrue(Math.abs(nativeSpeed - ratedSpeed) < .001 && nativeTarget.equals(ratedTarget),
                        "Iron spring changed native speed/target: " + ironMeasured);
                    helper.assertTrue(Math.abs(charges[i] / nativeCharge - 1) < .001,
                        "Iron spring changed reference native windup: " + ironMeasured);
                    helper.assertTrue(Math.abs(stress[i] / nativeStress - 1) < .001,
                        "Iron spring changed reference native stress: " + ironMeasured);
                }
            }
            // Pinned wood limb draws faster than iron: native draw-speed modifiers
            // 0 versus -0.2, so wood's 1.25x recharge must also pay >=1.25x stress.
            helper.assertTrue(charges[1] > 0 && charges[0] > charges[1],
                "Native spring windup did not change for distinct real materials");
            helper.assertTrue(stress[1] > 0 && stress[0] > stress[1]
                    && stress[0] / stress[1] >= charges[0] / charges[1],
                "Ejector gained faster charge without corresponding native stress accounting");
            var wood = SettingsNbt.read(prepared[0].getTag(), "create:weighted_ejector");
            helper.assertTrue(wood != null && Math.abs(wood.properties().get("recharge_rate") - 1.25) < .001
                && Math.abs(wood.properties().get("launch_envelope") - (1d / 1.1)) < .001,
                "Real wood bow limb no longer matches pinned TCon draw/velocity modifiers");
            // Wood is an actual crafted part. The other cases exercise valid persisted
            // ratio BOUNDARIES with no material labels; they do not claim that a new
            // TConstruct material or recipe supplies those synthetic boundary values.
            double[][] profiles = {{wood.properties().get("recharge_rate"), wood.properties().get("launch_envelope")},
                {1.5, 1.5}, {.75, 1.5}, {1.5, .75}};
            var boundaryBlock = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(6, 2, 3)));
            helper.assertTrue(boundaryBlock instanceof EjectorBlockEntity, "Native launcher boundary fixture disappeared");
            var boundary = (EjectorBlockEntity) boundaryBlock;
            var behaviour = KineticVariantBehaviour.get(boundary);
            helper.assertTrue(behaviour != null, "Native launcher boundary behaviour missing after ticks");
            for (int profile = 0; profile < profiles.length; profile++) {
                double recharge = profiles[profile][0], envelope = profiles[profile][1];
                var tag = new CompoundTag();
                if (profile == 0) tag = prepared[0].getTag().copy();
                else SettingsNbt.write(tag, launcherBoundary(recharge, envelope));
                for (int distance : new int[]{1, 12}) {
                    boundary.setTarget(distance, 0); boundary.setSpeed(64);
                    behaviour.install(new CompoundTag());
                    float nativeCharge = boundary.getWindUpSpeed(), nativeStress = boundary.calculateStressApplied();
                    helper.assertTrue(nativeCharge > 0 && nativeStress > 0
                        && boundary.getTargetPosition().subtract(boundary.getBlockPos()).distManhattan(BlockPos.ZERO) == distance,
                        "Unconfigured native launcher baseline or target unavailable");
                    behaviour.install(tag);
                    helper.assertTrue(behaviour.settings() != null, "Valid bounded launcher ratio rejected");
                    double actualWork = boundary.getWindUpSpeed() / nativeCharge;
                    double actualCost = boundary.calculateStressApplied() / nativeStress;
                    double distanceWeight = Math.min(1, Math.max(0, distance - 1) / 15.0);
                    double expectedWork = Math.max(.75, Math.min(1.5, recharge * (1 + (envelope - 1) * distanceWeight)));
                    double expectedCost = Math.max(.75, Math.min(1.5, recharge * Math.max(1, envelope)));
                    helper.assertTrue(Math.abs(actualWork - expectedWork) < .001 && Math.abs(actualCost - expectedCost) < .001,
                        "Native launcher lost valid low/high/mixed bounded ratios at distance " + distance + " profile " + profile);
                    helper.assertTrue(actualCost + .001 >= actualWork,
                        "Native launcher charged less stress than its work at distance " + distance + " profile " + profile);
                }
            }
            helper.succeed();
        });
    }
}
