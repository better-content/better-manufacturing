package com.bettercontent.manufacturing.clientfixture;

import com.bettercontent.manufacturing.*;
import com.bettercontent.manufacturing.api.*;
import com.bettercontent.manufacturing.integration.create.*;
import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.bettercontent.manufacturing.tconstruct.NativeParts;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.nio.file.Files;
import java.util.Map;

/** Nonshipping local-client witness. No pack deployment, account interaction or external service. */
@Mod.EventBusSubscriber(modid = ManufacturingMod.ID, value = Dist.CLIENT)
public final class ManufacturingClientWitness {
    private static int ticks, phase, next;
    private static volatile BlockPos target, meshTarget;
    private static boolean setup;
    private static final String[] IDS = {"better_manufacturing:/drill/tconstruct/wood", "better_manufacturing:/drill/tconstruct/iron", "better_manufacturing:/retool/drill/tconstruct/wood",
        com.bettercontent.manufacturing.integration.emi.MeshEmiPlugin.id("mesh", "string_mesh", "tconstruct:wood").toString(),
        com.bettercontent.manufacturing.integration.emi.MeshEmiPlugin.id("mesh", "string_mesh", "tconstruct:iron").toString(),
        com.bettercontent.manufacturing.integration.emi.MeshEmiPlugin.id("mesh_retool", "string_mesh", "tconstruct:iron").toString()};
    private static final String[] IMAGES = {"drill-wood.png", "drill-iron.png", "drill-retool.png", "mesh-wood.png", "mesh-iron.png", "mesh-retool.png"};
    private ManufacturingClientWitness() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("better_manufacturing.clientFixture") || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        try {
            if (++ticks > 3000) throw new IllegalStateException("Timed out at phase " + phase + "; screen=" + (client.screen == null ? "none" : client.screen.getClass().getName()));
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!setup) {
                setup = true;
                var server = client.getSingleplayerServer(); var playerId = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId); player.setGameMode(GameType.CREATIVE);
                    var level = player.serverLevel(); var pos = player.blockPosition().offset(2, 0, 0);
                    level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(pos, AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING, Direction.NORTH));
                    var head = ((ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:pick_head"))).withMaterial(new MaterialId("tconstruct:wood"));
                    ItemStack machine = AllBlocks.MECHANICAL_DRILL.asStack();
                    BuildNbt.write(machine.getOrCreateTag(), new MachineBuild(MachineBuild.VERSION, DrillEngineering.CHASSIS, DrillEngineering.FUNCTION, Map.of("head", NativeParts.read(head))));
                    CreateManufacturing.install(level, pos, machine); target = pos;
                    var screenPos = pos.offset(2, 0, 0);
                    level.setBlockAndUpdate(screenPos.below(), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(screenPos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createsifter:sifter")).defaultBlockState());
                    var screen = (com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity) level.getBlockEntity(screenPos);
                    var handle = ((ToolPartItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:tool_handle"))).withMaterial(new MaterialId("tconstruct:wood"));
                    var frame = new java.util.LinkedHashMap<String, ComponentProvenance>();
                    com.bettercontent.manufacturing.integration.sifter.MeshEngineering.ROLES.forEach(role -> frame.put(role, NativeParts.read(handle)));
                    var mesh = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("createsifter:string_mesh")));
                    BuildNbt.write(mesh.getOrCreateTag(), new MachineBuild(MachineBuild.VERSION, "createsifter:string_mesh", com.bettercontent.manufacturing.integration.sifter.MeshEngineering.FUNCTION, frame));
                    screen.meshInv.insertItem(0, mesh, false); screen.sendData(); meshTarget = screenPos;
                });
                return;
            }
            if (target == null || meshTarget == null) return;
            if (!(client.level.getBlockEntity(meshTarget) instanceof com.bettercontent.manufacturing.integration.sifter.ScreenAccess screen)) return;
            var behaviour = CreateManufacturing.behaviour(client.level.getBlockEntity(target));
            if (behaviour == null || !behaviour.hasRecord()) return;
            var catalog = com.bettercontent.manufacturing.data.EngineeringCatalog.view(true);
            if (!catalog.ready() || !slimeknights.tconstruct.library.materials.MaterialRegistry.isFullyLoaded()) return;
            var screenState = screen.manufacturing$screenState();
            if (screenState.build() == null || !screenState.build().hasProvenance()) return;
            if (!screenState.allowed() || screenState.rate() != .75) throw new IllegalStateException("Real client installed-mesh packet/evaluator mismatch");
            if (!catalog.policies().containsKey("tconstruct:wood") || catalog.policies().get("tconstruct:wood").traits().get("screen_rate") != .75)
                throw new IllegalStateException("Real client engineering catalog packet mismatch");
            if (!behaviour.state().allowed() || behaviour.state().rate() != .75) throw new IllegalStateException("Real client packet/evaluator mismatch");
            if (phase < IDS.length * 2 && ticks < next) return;
            if (phase < IDS.length * 2 && phase % 2 == 0) {
                var recipe = EmiApi.getRecipeManager().getRecipe(new ResourceLocation(IDS[phase / 2]));
                if (recipe == null) return;
                var emiOutput = recipe.getOutputs().get(0);
                if (EmiApi.getIndexStacks().stream().noneMatch(stack -> stack.isEqual(emiOutput))) throw new IllegalStateException("Material-bearing output is missing from the searchable EMI index");
                if (EmiApi.getRecipeManager().getRecipesByOutput(emiOutput).isEmpty()) throw new IllegalStateException("Material-bearing output has no recipe lookup");
                var output = emiOutput.getItemStack();
                if (phase < 6) {
                    var legacy = output.copy();
                    legacy.getTag().getCompound(BuildNbt.KEY).putInt("version", 1);
                    legacy.getTag().getCompound(BuildNbt.KEY).remove("qualifications");
                    if (EmiApi.getRecipeManager().getRecipesByOutput(dev.emi.emi.api.stack.EmiStack.of(legacy)).isEmpty())
                        throw new IllegalStateException("Valid legacy Drill lost EMI recipe lookup");
                    double expected = phase == 2 ? 1 : .75;
                    if (DrillEngineering.evaluate(output.getTag()).rate() != expected) throw new IllegalStateException("EMI output disagrees with live engineering evaluator");
                } else {
                    double expected = phase == 6 ? .75 : 1;
                    if (com.bettercontent.manufacturing.integration.sifter.MeshEngineering.evaluate(output, true).rate() != expected)
                        throw new IllegalStateException("Mesh EMI output disagrees with live engineering evaluator");
                }
                EmiApi.displayRecipe(recipe); phase++; next = ticks + 30;
            } else if (phase < IDS.length * 2) {
                screenshot(client, IMAGES[phase / 2]); phase++; next = ticks + 20;
            } else {
                Files.writeString(client.gameDirectory.toPath().resolve("client-witness.json"), """
                    {"status": "passed", "scope": "isolated_userdev_drill_sifter_client", "pack_runtime_accepted": false,
                     "client_packet_rate": 0.75, "engineering_catalog_sync": true, "emi_recipe_outputs_match_evaluator": true, "emi_index_and_output_lookup": true, "legacy_drill_lookup": true,
                     "mesh_packet_rate": 0.75, "screenshots": ["drill-wood.png", "drill-iron.png", "drill-retool.png", "mesh-wood.png", "mesh-iron.png", "mesh-retool.png"]}
                    """);
                org.slf4j.LoggerFactory.getLogger("ManufacturingClientWitness").info("MANUFACTURING_CLIENT_COMPLETE");
                client.stop();
            }
        } catch (Throwable failure) {
            try {
                screenshot(client, "failure.png");
                Files.writeString(client.gameDirectory.toPath().resolve("client-witness.json"), "{\"status\":\"failed\"}");
            } catch (Exception ignored) { }
            failure.printStackTrace(); client.stop();
        }
    }
    private static void screenshot(Minecraft client, String name) throws java.io.IOException {
        var directory = client.gameDirectory.toPath().resolve("screenshots"); Files.createDirectories(directory);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
    }
}
