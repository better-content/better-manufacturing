package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MaterialPolicy;
import com.bettercontent.manufacturing.data.*;
import com.bettercontent.manufacturing.integration.create.*;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class EngineeringReloadTests {
    @GameTest(template = "drill_workshop", batch = "engineering_reload", timeoutTicks = 1200)
    public static void changedNativeDataAndAtomicPolicyReload(GameTestHelper helper) throws Exception {
        helper.assertTrue(Boolean.getBoolean("forge.gameTestServer"), "Reload fixture may only modify an owner-local GameTest world");
        var server = helper.getLevel().getServer();
        var initial = EngineeringCatalog.view(false);
        helper.assertTrue(initial.ready() && initial.policies().containsKey("tconstruct:wood"), "Authored engineering policies were not loaded");
        var wood = CreateManufacturing.behaviour(CreateDrillTests.placed(helper, new BlockPos(1, 1, 1), CreateDrillTests.craft(helper, "tconstruct:wood")));
        var cobalt = CreateManufacturing.behaviour(CreateDrillTests.placed(helper, new BlockPos(3, 1, 1), CreateDrillTests.craft(helper, "tconstruct:cobalt")));
        var screen = (com.bettercontent.manufacturing.integration.sifter.ScreenAccess) SifterFrameTests.machine(helper, 10, SifterFrameTests.mesh(helper, "string", "tconstruct:iron"), false);
        helper.assertTrue(screen.manufacturing$screenState().rate() == 1, "Missing native iron frame reference");
        var wetScreen = (com.bettercontent.manufacturing.integration.sifter.ScreenAccess) SifterFrameTests.machine(helper, 13, SifterFrameTests.mesh(helper, "string", "tconstruct:wood"), true);
        helper.assertTrue(wetScreen.manufacturing$screenState().rate() == .75, "Missing crafted wood settings");
        double oldRate = cobalt.state().rate();
        helper.assertTrue(wood.state().allowed() && oldRate > 1 && oldRate < 1.5, "Missing distinct native reference builds");
        var originalPacks = new ArrayList<>(server.getPackRepository().getSelectedIds());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("manufacturing-catalog-test");
        helper.assertTrue(!Files.exists(pack), "Refusing to overwrite an existing reload fixture");
        Path policy = pack.resolve("data/better_manufacturing/engineering/materials/wood.json");
        Path stats = pack.resolve("data/tconstruct/tinkering/materials/stats/iron.json");
        Files.createDirectories(policy.getParent()); Files.createDirectories(stats.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Owner-local Manufacturing reload fixture\"}}");
        var priorWood = initial.policies().get("tconstruct:wood");
        var restricted = new MaterialPolicy(priorWood.material(), Set.of("tconstruct:tool_handle"), true, Map.of("screen_rate", 1.25), Set.of());
        Files.writeString(policy, EngineeringPolicyJson.write(restricted).toString());
        try (var input = server.getResourceManager().getResource(new ResourceLocation("tconstruct:tinkering/materials/stats/iron.json")).orElseThrow().openAsReader()) {
            var json = JsonParser.parseReader(input).getAsJsonObject();
            json.getAsJsonObject("stats").getAsJsonObject("tconstruct:head").addProperty("mining_speed", 3.0);
            Files.writeString(stats, json.toString());
        }
        server.getPackRepository().reload();
        var changedPacks = new ArrayList<>(originalPacks); changedPacks.add("file/manufacturing-catalog-test");
        server.reloadResources(changedPacks).thenRunAsync(() -> {
            helper.assertTrue(wood.state().rate() == .75 && cobalt.state().rate() == oldRate, "Reload changed an existing cutter");
            helper.assertTrue(DrillEngineering.evaluate(CreateDrillTests.craft(helper, "tconstruct:cobalt").getTag()).rate() == 1.5, "Future cutter craft ignored changed native stats");
            helper.assertTrue(screen.manufacturing$screenState().rate() == 1 && wetScreen.manufacturing$screenState().rate() == .75, "Reload changed an installed mesh");
            var freshMesh = SifterFrameTests.mesh(helper, "string", "tconstruct:wood");
            helper.assertTrue(com.bettercontent.manufacturing.integration.sifter.MeshEngineering.evaluate(freshMesh, false).rate() == 1.25, "Future mesh craft ignored changed authored rating");
            helper.assertTrue(EngineeringCatalog.view(false).generation() > initial.generation(), "Catalog generation did not change");
            try { Files.writeString(policy, "{\"schema\":999}"); } catch (Exception failure) { throw new CompletionException(failure); }
        }, server).thenCompose(ignored -> {
            var beforeFailedReload = EngineeringCatalog.view(false);
            return server.reloadResources(changedPacks).handleAsync((ok, failure) -> {
                helper.assertTrue(failure != null, "Malformed engineering reload unexpectedly succeeded");
                helper.assertTrue(EngineeringCatalog.view(false).equals(beforeFailedReload), "Failed reload published a partial engineering catalog");
                return null;
            }, server);
        }).thenCompose(ignored -> {
            var beforeFailedReload = EngineeringCatalog.view(false);
            try { Files.writeString(policy, "{\"schema\":"); } catch (Exception failure) { throw new CompletionException(failure); }
            return server.reloadResources(changedPacks).handleAsync((ok, failure) -> {
                helper.assertTrue(failure != null, "Syntactically broken engineering file was silently skipped");
                helper.assertTrue(EngineeringCatalog.view(false).equals(beforeFailedReload), "Syntax failure removed authored restrictions");
                return null;
            }, server);
        }).thenCompose(ignored -> server.reloadResources(originalPacks)).whenComplete((ok, failure) -> server.execute(() -> {
            if (failure != null) { helper.fail("Actual reload fixture failed: " + failure); return; }
            helper.assertTrue(wood.state().allowed() && Math.abs(cobalt.state().rate() - oldRate) < 1e-9, "Restored datapacks left stale machine state");
            helper.assertTrue(EngineeringCatalog.view(false).policies().equals(initial.policies()), "Restored catalog differs from original");
            helper.assertTrue(screen.manufacturing$screenState().rate() == 1 && wetScreen.manufacturing$screenState().wetCompatible(), "Restored Sifter policy remained cached");
            helper.succeed();
        }));
    }
}
