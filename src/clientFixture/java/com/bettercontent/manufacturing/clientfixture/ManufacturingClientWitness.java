package com.bettercontent.manufacturing.clientfixture;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import com.bettercontent.manufacturing.integration.create.DrillEngineering;
import com.bettercontent.manufacturing.integration.sifter.MeshEngineering;
import com.bettercontent.manufacturing.integration.sifter.ScreenAccess;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.nio.file.Files;

/** Isolated real-client witness for v2 native settings, packets, generic EMI pages and framebuffer captures. */
@Mod.EventBusSubscriber(modid = ManufacturingMod.ID, value = Dist.CLIENT)
public final class ManufacturingClientWitness {
    private static int ticks, phase, next;
    private static volatile BlockPos drillPos, meshPos;
    private static volatile ItemStack drill, mesh;
    private static volatile Throwable setupFailure;
    private static boolean setup;
    private static final String DRILL_RECIPE = "create:crafting/kinetics/mechanical_drill";
    private static final String MESH_RECIPE = "createsifter:string_mesh";
    private static final String[] IMAGES = {"drill-native-recipe.png", "drill-installed-world.png", "drill-inventory.png",
        "mesh-native-recipe.png", "mesh-installed-world.png", "mesh-inventory.png"};
    private ManufacturingClientWitness() {}

    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }

    private static ItemStack craft(net.minecraft.server.level.ServerLevel level, String recipeId, String partId, String material) {
        var loaded = level.getRecipeManager().byKey(new ResourceLocation(recipeId)).orElseThrow();
        if (!(loaded instanceof PartVariantRecipe recipe)) throw new IllegalStateException("Native part recipe absent: " + recipeId);
        var part = ForgeRegistries.ITEMS.getValue(new ResourceLocation(partId));
        if (!(part instanceof ToolPartItem nativePart)) throw new IllegalStateException("Native TCon part absent: " + partId);
        var input = grid();
        if (recipeId.equals(DRILL_RECIPE)) {
            for (int slot : new int[] {1, 3, 5}) input.setItem(slot, AllItems.ANDESITE_ALLOY.asStack());
            input.setItem(4, nativePart.withMaterial(new MaterialId(material)));
            input.setItem(7, AllBlocks.ANDESITE_CASING.asStack());
        } else {
            for (int slot : new int[] {1, 3, 5, 7}) input.setItem(slot, nativePart.withMaterial(new MaterialId(material)));
            input.setItem(4, new ItemStack(Items.STRING));
        }
        if (!recipe.matches(input, level)) throw new IllegalStateException("Real native part craft rejected: " + recipeId);
        var result = recipe.assemble(input, level.registryAccess());
        var id = ForgeRegistries.ITEMS.getKey(result.getItem());
        var settings = id == null ? null : SettingsNbt.read(result.getTag(), id.toString());
        if (result.isEmpty() || settings == null || settings.numericInputs() == null)
            throw new IllegalStateException("Real crafting omitted v2 numerical snapshot: " + recipeId);
        return result;
    }

    private static void rejectV1(ItemStack instance, String output) {
        var old = instance.copy();
        old.getOrCreateTag().getCompound(SettingsNbt.KEY).putInt("version", 1);
        if (SettingsNbt.read(old.getTag(), output) != null || old.getTag().getCompound(SettingsNbt.KEY).getInt("version") != 1)
            throw new IllegalStateException("Obsolete v1 settings were accepted or mutated: " + output);
    }

    private static boolean recipePage(String recipeId, String output) {
        var recipe = EmiApi.getRecipeManager().getRecipe(new ResourceLocation(recipeId));
        if (recipe == null) return false;
        var emiOutput = recipe.getOutputs().get(0);
        var stack = emiOutput.getItemStack();
        if (!stack.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation(output)))
            || SettingsNbt.read(stack.getTag(), output) != null
            || EmiApi.getIndexStacks().stream().noneMatch(index -> index.isEqual(emiOutput))
            || EmiApi.getRecipeManager().getRecipesByOutput(emiOutput).isEmpty())
            throw new IllegalStateException("Generic native recipe output is not searchable in EMI: " + recipeId);
        EmiApi.displayRecipe(recipe);
        return true;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("better_manufacturing.clientFixture") || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        try {
            if (++ticks > 7000) throw new IllegalStateException("Timed out at phase " + phase + "; screen="
                + (client.screen == null ? "none" : client.screen.getClass().getName()));
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!setup) {
                setup = true;
                var server = client.getSingleplayerServer(); var playerId = client.player.getUUID();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(playerId);
                        player.setGameMode(GameType.CREATIVE);
                        var level = player.serverLevel();
                        drill = craft(level, DRILL_RECIPE, "tconstruct:pick_head", "tconstruct:slimewood");
                        mesh = craft(level, MESH_RECIPE, "tconstruct:tool_handle", "tconstruct:slimewood");
                        rejectV1(drill, DrillEngineering.CHASSIS);
                        rejectV1(mesh, "createsifter:string_mesh");
                        if (DrillEngineering.evaluate(drill.getTag()).rate() != .75
                            || Math.abs(MeshEngineering.evaluate(mesh, false).rate() - .85) > 1e-6)
                            throw new IllegalStateException("Captured TCon native rates differ from the pinned materials");
                        var pos = player.blockPosition().offset(2, 0, 0);
                        // Only the copied owner-local client world: clear nearby foliage so
                        // the real machine/mesh screenshots do not hide in jungle leaves.
                        for (int x = -2; x <= 6; x++) for (int z = -3; z <= 3; z++) {
                            level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                            for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(pos, AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DrillBlock.FACING, Direction.NORTH));
                        CreateManufacturing.install(level, pos, drill.copy()); drillPos = pos;
                        var screenPos = pos.offset(2, 0, 0);
                        level.setBlockAndUpdate(screenPos.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(screenPos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createsifter:sifter")).defaultBlockState());
                        var screen = (SifterBlockEntity) level.getBlockEntity(screenPos);
                        screen.meshInv.insertItem(0, mesh.copy(), false); screen.sendData(); meshPos = screenPos;
                        player.getInventory().add(drill.copy()); player.getInventory().add(mesh.copy());
                        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos));
                    } catch (Throwable failure) { setupFailure = failure; }
                });
                return;
            }
            if (setupFailure != null) throw new IllegalStateException("Real v2 server setup failed", setupFailure);
            if (drillPos == null || meshPos == null || drill == null || mesh == null) return;
            if (!(client.level.getBlockEntity(meshPos) instanceof ScreenAccess screen)) return;
            var behaviour = CreateManufacturing.behaviour(client.level.getBlockEntity(drillPos));
            if (behaviour == null || !behaviour.hasRecord()) return;
            var drillExpected = SettingsNbt.read(drill.getTag(), DrillEngineering.CHASSIS);
            var meshExpected = SettingsNbt.read(mesh.getTag(), "createsifter:string_mesh");
            var clientDrill = SettingsNbt.read(behaviour.record(), DrillEngineering.CHASSIS);
            if (clientDrill == null || !clientDrill.equals(drillExpected) || behaviour.state().rate() != .75)
                throw new IllegalStateException("Real drill client packet lost its v2 numerical snapshot");
            var clientMesh = screen.manufacturing$screenState();
            if (!clientMesh.configured() || Math.abs(clientMesh.rate() - .85) > 1e-6
                || !ItemStack.isSameItemSameTags(((SifterBlockEntity) client.level.getBlockEntity(meshPos)).meshInv.getStackInSlot(0), mesh))
                throw new IllegalStateException("Real mesh client packet lost its configured v2 item: " + meshExpected);
            if (phase < IMAGES.length * 2 && ticks < next) return;
            if (phase < IMAGES.length * 2 && phase % 2 == 0) {
                int image = phase / 2;
                if (image == 0 || image == 3) {
                    if (!recipePage(image == 0 ? DRILL_RECIPE : MESH_RECIPE,
                        image == 0 ? DrillEngineering.CHASSIS : "createsifter:string_mesh")) return;
                } else if (image == 2 || image == 5) {
                    client.setScreen(new InventoryScreen(client.player));
                    // Move the real client mouse to the crafted item's hotbar slot
                    // so the framebuffer contains its actual v2 tooltip, not an EMI
                    // index item underneath the world GUI.
                    double x = image == 2 ? 512 : 549;
                    org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(), x, 505);
                    var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                    move.setAccessible(true);
                    move.invoke(client.mouseHandler, client.getWindow().getWindow(), x, 505d);
                } else {
                    client.setScreen(null);
                    var aim = image == 1 ? drillPos : meshPos;
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                        if (image == 4) player.teleportTo(aim.getX() + 2.5, aim.getY() + 1, aim.getZ() + 3.5);
                        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(aim));
                    });
                }
                phase++; next = ticks + 35;
            } else if (phase < IMAGES.length * 2) {
                screenshot(client, IMAGES[phase / 2]); phase++; next = ticks + 20;
            } else {
                Files.writeString(client.gameDirectory.toPath().resolve("client-witness.json"), """
                    {"status": "passed", "scope":"isolated_userdev_v2_drill_sifter_client", "pack_runtime_accepted":false,
                     "v2_drill_packet":true, "v2_mesh_packet":true, "emi_native_pages_searchable":true,
                     "v1_settings_rejected":true, "screenshots":["drill-native-recipe.png","drill-installed-world.png","drill-inventory.png",
                     "mesh-native-recipe.png","mesh-installed-world.png","mesh-inventory.png"]}
                    """);
                org.slf4j.LoggerFactory.getLogger("ManufacturingClientWitness").info("MANUFACTURING_CLIENT_COMPLETE");
                client.stop();
            }
        } catch (Throwable failure) {
            try { screenshot(client, "failure.png"); Files.writeString(client.gameDirectory.toPath().resolve("client-witness.json"), "{\"status\":\"failed\"}"); }
            catch (Exception ignored) { }
            failure.printStackTrace(); client.stop();
        }
    }
    private static void screenshot(Minecraft client, String name) throws java.io.IOException {
        var directory = client.gameDirectory.toPath().resolve("screenshots"); Files.createDirectories(directory);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
    }
}
