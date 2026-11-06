package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import com.bettercontent.manufacturing.integration.create.DrillEngineering;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.google.gson.JsonParser;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletionException;

/** Reload native TCon stats without reinterpreting any already crafted v2 machine or installed state. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class EngineeringReloadTests {
    @GameTest(template = "drill_workshop", batch = "engineering_reload", timeoutTicks = 1200)
    public static void changedNativeDataKeepsV2SnapshotsAndInstalledState(GameTestHelper helper) throws Exception {
        helper.assertTrue(Boolean.getBoolean("forge.gameTestServer"), "Reload fixture may only modify an owner-local GameTest world");
        var server = helper.getLevel().getServer();
        var original = CreateDrillTests.craft(helper, "tconstruct:slimewood");
        var iron = CreateDrillTests.craft(helper, "tconstruct:iron");
        double before = DrillEngineering.evaluate(original.getTag()).rate();
        helper.assertTrue(before == .75 && DrillEngineering.evaluate(iron.getTag()).rate() == 1,
            "Missing distinct eligible native head/reference materials");
        var installed = CreateDrillTests.placed(helper, new BlockPos(2, 1, 2), original);
        var state = CreateManufacturing.behaviour(installed).state();
        helper.assertTrue(state.rate() == before, "Installation did not retain v2 numerical inputs");
        var saved = installed.saveWithFullMetadata();
        var originalSettings = original.getTag().get(SettingsNbt.KEY).copy();
        var originalPacks = new ArrayList<>(server.getPackRepository().getSelectedIds());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("manufacturing-native-v2-reload-test");
        helper.assertTrue(!Files.exists(pack), "Refusing to overwrite an existing reload fixture");
        Path stats = pack.resolve("data/tconstruct/tinkering/materials/stats/iron.json");
        Files.createDirectories(stats.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Owner-local v2 material reload fixture\"}}");
        try (var input = server.getResourceManager().getResource(new ResourceLocation("tconstruct:tinkering/materials/stats/iron.json")).orElseThrow().openAsReader()) {
            var json = JsonParser.parseReader(input).getAsJsonObject();
            json.getAsJsonObject("stats").getAsJsonObject("tconstruct:head").addProperty("mining_speed", 3.0);
            Files.writeString(stats, json.toString());
        }
        server.getPackRepository().reload();
        var changedPacks = new ArrayList<>(originalPacks);
        changedPacks.add("file/manufacturing-native-v2-reload-test");
        server.reloadResources(changedPacks).thenRunAsync(() -> {
            helper.assertTrue(original.getTag().get(SettingsNbt.KEY).equals(originalSettings)
                && DrillEngineering.evaluate(original.getTag()).rate() == before,
                "Material reload reinterpreted a crafted v2 numeric snapshot");
            helper.assertTrue(CreateManufacturing.behaviour(installed).state().rate() == before,
                "Material reload reinterpreted an installed cutter");
            var restored = new DrillBlockEntity(installed.getType(), installed.getBlockPos(), installed.getBlockState());
            restored.setLevel(helper.getLevel()); restored.load(saved);
            helper.assertTrue(CreateManufacturing.behaviour(restored).state().rate() == before,
                "Material reload reinterpreted saved/reloaded installed settings");
            var drops = Block.getDrops(installed.getBlockState(), helper.getLevel(), installed.getBlockPos(), installed);
            helper.assertTrue(drops.size() == 1 && drops.get(0).getTag().get(SettingsNbt.KEY).equals(originalSettings),
                "Material reload changed a native drop or discarded v2 settings");
            var future = CreateDrillTests.craft(helper, "tconstruct:slimewood");
            helper.assertTrue(DrillEngineering.evaluate(future.getTag()).rate() > before + .1
                && DrillEngineering.evaluate(future.getTag()).rate() < 1.5,
                "Future craft ignored changed native iron reference stats");
        }, server).handle((ok, failure) -> failure).thenCompose(failure ->
            server.reloadResources(originalPacks).handleAsync((ok, restoreFailure) -> {
                try (var files = Files.walk(pack)) {
                    for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                    server.getPackRepository().reload();
                } catch (Exception cleanupFailure) {
                    helper.fail("Native reload fixture could not clean its owner-local datapack: " + cleanupFailure);
                    return null;
                }
                if (failure != null || restoreFailure != null) {
                    helper.fail("Native v2 reload/restore failed: " + (failure != null ? failure : restoreFailure));
                    return null;
                }
                helper.assertTrue(CreateManufacturing.behaviour(installed).state().rate() == before
                    && DrillEngineering.evaluate(CreateDrillTests.craft(helper, "tconstruct:slimewood").getTag()).rate() == before,
                    "Restored native material pack changed old installation or future crafting");
                helper.succeed();
                return null;
            }, server));
    }
}
