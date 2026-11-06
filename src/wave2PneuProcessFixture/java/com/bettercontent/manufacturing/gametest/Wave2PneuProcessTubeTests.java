package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.compressed.CompressedVariant;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedPressureTubeBlock;
import com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedPressureTubeBlockEntity;
import com.simibubi.create.AllBlocks;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.block.PNCBlockStateProperties;
import me.desht.pneumaticcraft.api.block.PressureTubeConnection;
import me.desht.pneumaticcraft.common.block.entity.PressureTubeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.UUID;

/** Native CC bracket application on a genuinely rated PNCR pressure tube, not a synthetic CC recipe. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuProcessTubeTests {
    private static final ResourceLocation TUBE = new ResourceLocation("pneumaticcraft:pressure_tube");
    private static TransientCraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }
    @GameTest(template = "wave2_pneu_process_workshop")
    public static void nativeBracketActionCarriesRatedPressureAndAirFromRealTube(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("compressedcreativity") && ModList.get().isLoaded("pneumaticcraft"),
            "Real CC and PNCR missing");
        var found = helper.getLevel().getRecipeManager().byKey(TUBE)
            .orElseThrow(() -> new AssertionError("Native PNCR tube construction missing"));
        helper.assertTrue(found instanceof PartVariantRecipe, "Configured native PNCR tube recipe not effective");
        var recipe = (PartVariantRecipe) found;
        var grid = grid();
        var part = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:large_plate"));
        helper.assertTrue(part instanceof ToolPartItem, "Real TCon plate missing");
        // Pack wood has no head; pinned flint head durability 85 vs iron 250
        // yields a real bounded 0.75 pressure envelope on the consumed plate.
        grid.setItem(0, ((ToolPartItem) part).withMaterial(new MaterialId("tconstruct:flint")));
        for (int slot : new int[]{1, 2}) {
            var available = recipe.getIngredients().get(slot).getItems();
            helper.assertTrue(available.length > 0, "Native tube construction ingredient absent: " + slot);
            grid.setItem(slot, available[0].copy());
        }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native tube construction rejected eligible flint plate");
        var source = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(source.getCount() == 8 && SettingsNbt.read(source.getTag(), TUBE.toString()) != null,
            "Native eight-tube yield or baked pressure setting lost");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(((BlockItem) source.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos,
            Direction.WEST, source.copy(), Direction.UP)).consumesAction(), "PNCR tube placement failed");
        var original = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(original instanceof PressureTubeBlockEntity
            && Math.abs(PneupressureBuild.rate(original, "pressure_envelope") - .75) < .001,
            "Crafted native pressure rating did not reach tube");
        // CC's native bracket action is allowed only for straight tubes; leave the other four sides open.
        var state = helper.getLevel().getBlockState(pos)
            .setValue(PNCBlockStateProperties.PressureTubes.EAST, PressureTubeConnection.CONNECTED)
            .setValue(PNCBlockStateProperties.PressureTubes.WEST, PressureTubeConnection.CONNECTED);
        helper.getLevel().setBlock(pos, state, 2);
        var inputAir = original.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY)
            .orElseThrow(() -> new AssertionError("Native PNCR tube air handler missing"));
        inputAir.addAir(inputAir.getVolume() / 2);
        int beforeAir = inputAir.getAir();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "rated_bracket"));
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.METAL_BRACKET.asStack());
        BracketedPressureTubeBlock.usingBracketOnPressureTube(new PlayerInteractEvent.RightClickBlock(player,
            InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        var converted = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(converted instanceof BracketedPressureTubeBlockEntity
            && ForgeRegistries.BLOCKS.getKey(converted.getBlockState().getBlock()).toString()
                .equals("compressedcreativity:bracketed_pressure_tube"), "Native CC bracket conversion failed");
        var variant = CompressedVariant.get(converted);
        helper.assertTrue(variant != null && Math.abs(variant.property("pressure_envelope") - .75) < .001,
            "Native CC bracket dropped PNCR baked pressure rating");
        var bracketAir = converted.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY)
            .orElseThrow(() -> new AssertionError("CC bracketed native air handler missing"));
        helper.assertTrue(bracketAir.getAir() == beforeAir && bracketAir.getDangerPressure() <= inputAir.getDangerPressure(),
            "Bracketing lost stored air or raised native pressure danger limit");
        var clone = ((BracketedPressureTubeBlock) converted.getBlockState().getBlock())
            .getCloneItemStack(converted.getBlockState(), null, helper.getLevel(), pos, player);
        helper.assertTrue(clone.is(source.getItem())
            && SettingsNbt.read(clone.getTag(), TUBE.toString()) != null,
            "Native CC pick-block lost configured PNCR tube identity");
        var invalid = new CompoundTag();
        var bracketId = ForgeRegistries.BLOCKS.getKey(converted.getBlockState().getBlock()).toString();
        SettingsNbt.write(invalid, SettingsNbt.inherit(bracketId, "pressure_envelope",
            SettingsNbt.read(source.getTag(), TUBE.toString())));
        invalid.getCompound(SettingsNbt.KEY).getCompound("components").getCompound("source")
            .putDouble("pressure_envelope", 1.5);
        variant.install(invalid);
        var invalidClone = ((BracketedPressureTubeBlock) converted.getBlockState().getBlock())
            .getCloneItemStack(converted.getBlockState(), null, helper.getLevel(), pos, player);
        helper.assertTrue(variant.settings() == null && variant.property("pressure_envelope") == 1
            && !invalidClone.hasTag() && bracketAir.getDangerPressure() <= inputAir.getDangerPressure(),
            "Out-of-range bracket pressure envelope gained native pressure or fake native tube settings");
        helper.succeed();
    }
}
