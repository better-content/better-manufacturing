package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureFuelCost;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.tconstruct.NativePartCapture;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.block.entity.AbstractAirHandlingBlockEntity;
import me.desht.pneumaticcraft.common.block.entity.AirCompressorBlockEntity;
import me.desht.pneumaticcraft.common.block.entity.ElectrostaticCompressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;

@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuMechanicsCompressorTests {
    private static ItemStack part(String id, String material) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (!(item instanceof ToolPartItem tool)) throw new AssertionError("Native compressor working part missing: " + id);
        return tool.withMaterial(MaterialVariantId.parse(material));
    }
    private static AirCompressorBlockEntity machine(GameTestHelper helper, BlockPos relative, String material) {
        var item = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:air_compressor"));
        helper.assertTrue(item != null, "Native PNCR compressor absent");
        helper.setBlock(relative, item.defaultBlockState());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(be instanceof AirCompressorBlockEntity && be instanceof PneupressureBuild, "Compressor native BE/mixin absent");
        var compressor = (AirCompressorBlockEntity) be;
        compressor.onUpgradesChanged(); // Native zero-upgrade baseline before comparing paid work.
        var parts = Map.of("body", part("tconstruct:large_plate", material),
            "handle", part("tconstruct:tough_handle", material));
        var settings = NativePartCapture.bake(PneupressureSettings.COMPRESSION, parts,
            () -> PneupressureSettings.bake("pneumaticcraft:air_compressor", PneupressureSettings.COMPRESSION, parts));
        var tag = new CompoundTag(); SettingsNbt.write(tag, settings);
        ((PneupressureBuild) compressor).pneupressure$install(tag.get(SettingsNbt.KEY));
        return compressor;
    }
    private static int air(AbstractAirHandlingBlockEntity be) throws Exception {
        var field = AbstractAirHandlingBlockEntity.class.getDeclaredField("airHandler");
        field.setAccessible(true);
        return ((IAirHandlerMachine) field.get(be)).getAir();
    }
    @GameTest(template = "pneu_workshop")
    public static void slowerWorkingPartsNeverProduceUnpaidAir(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Real PNCR/TCon missing");
        // Pinned slimewood has a real head (4 vs iron 6 mining speed) and a
        // slower handle (-0.15 vs iron 0); pack wood has no native head.
        double slowerRate = Math.sqrt(.75 * .85);
        var slower = machine(helper, new BlockPos(2, 2, 2), "tconstruct:slimewood");
        var iron = machine(helper, new BlockPos(5, 2, 2), "tconstruct:iron");
        helper.assertTrue(Math.abs(PneupressureBuild.rate(slower, "compression_rate") - slowerRate) < 1e-6
            && PneupressureBuild.rate(iron, "compression_rate") == 1, "Native plates/handles failed contrasting compressor rating");
        for (int n = 0; n < 3; n++) slower.tickServer();
        helper.assertTrue(slower.curFuelUsage >= 1 && air(slower) == 0 && slower.burnTime == 0,
            "Unpowered rated compressor produced free air or rounded fuel cost to zero");
        var slowerRemainder = slower.getPrimaryInventory().insertItem(0, new ItemStack(Items.COAL), false);
        var ironRemainder = iron.getPrimaryInventory().insertItem(0, new ItemStack(Items.COAL), false);
        helper.assertTrue(slowerRemainder.isEmpty() && ironRemainder.isEmpty(), "PNCR native fuel inventory rejected coal");
        for (int n = 0; n < 12; n++) { slower.tickServer(); iron.tickServer(); }
        int slowerAir = air(slower), ironAir = air(iron);
        int slowerBudget = Math.max(1, (int) Math.round(slower.getBaseProduction() * slowerRate));
        int ironBudget = iron.getBaseProduction();
        helper.assertTrue(slowerAir > 0 && ironAir > slowerAir
            && slowerAir % slowerBudget == 0 && ironAir % ironBudget == 0
            && Math.abs(slowerAir / (double) slowerBudget - ironAir / (double) ironBudget) <= 1
            && slower.curFuelUsage >= 1 && iron.curFuelUsage >= 1,
            "Native compressor air production ignored paid per-tick slower work budget: " + slowerAir + "/" + ironAir);
        helper.assertTrue(slower.burnTime > 0 && iron.burnTime > 0
            && Math.abs(slower.burnTime - iron.burnTime)
                <= Math.abs(slowerAir / slowerBudget - ironAir / ironBudget),
            "Rated slimewood compressor did not pay one native burn unit per productive tick: "
                + slower.burnTime + "/" + iron.burnTime);
        helper.succeed();
    }

    // No authored material currently rates >1 for compressed_air_rate. Exercise the upper
    // saved-settings bound as a resource-accounting contract, not an obtainable new craft.
    @GameTest(template = "pneu_workshop")
    public static void fasterSavedRatingPaysFuelAndPersistsFractionAcrossReload(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Real PNCR/TCon missing");
        var pos = new BlockPos(2, 2, 2);
        var fast = machine(helper, pos, "tconstruct:iron");
        var tag = new CompoundTag();
        // Synthetic saved-stat boundary (not a claim that an obtainable material rates 1.5).
        var numeric = new NumericInputs(PneupressureSettings.COMPRESSION,
            Map.of("body", Map.of(Measure.HEAD_MINING_SPEED, new NumericInputs.Stat(1.5, 1),
                    Measure.HEAD_DURABILITY, new NumericInputs.Stat(1, 1)),
                "handle", Map.of(Measure.HANDLE_MINING_SPEED, new NumericInputs.Stat(.5, 0))), Map.of());
        SettingsNbt.write(tag, new MachineSettings("pneumaticcraft:air_compressor",
            NumericEvaluator.evaluate("pneumaticcraft:air_compressor", numeric, null), Map.of(), null, numeric));
        ((PneupressureBuild) fast).pneupressure$install(tag.get(SettingsNbt.KEY));
        helper.assertTrue(PneupressureBuild.rate(fast, "compression_rate") == 1.5, "Rated bound not installed");
        fast.tickServer();
        helper.assertTrue(air(fast) == 0 && fast.curFuelUsage >= 1 && fast.burnTime == 0,
            "Unpowered fast compressor supplied free air");
        var upgradeItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft:speed_upgrade"));
        helper.assertTrue(upgradeItem != null && upgradeItem != Items.AIR, "Native speed-upgrade item missing");
        helper.assertTrue(fast.getUpgradeHandler().insertItem(0, new ItemStack(upgradeItem), false).isEmpty(),
            "Native compressor refused speed upgrade");
        fast.onUpgradesChanged();
        double upgradeCost = fast.getSpeedUsageMultiplierFromUpgrades();
        double upgradeSpeed = fast.getSpeedMultiplierFromUpgrades();
        helper.assertTrue(upgradeCost > 1 && upgradeSpeed > 1, "Native speed upgrade did not change power and work multipliers");
        helper.assertTrue(fast.getPrimaryInventory().insertItem(0, new ItemStack(Items.COAL), false).isEmpty(),
            "Native compressor rejected coal fuel");
        fast.tickServer();
        helper.assertTrue(fast.burnTime > 0 && fast.curFuelUsage >= 1 && air(fast) > 0,
            "Native upgraded compressor did not spend fuel for positive output");
        var saved = fast.saveWithFullMetadata();
        double remainder = saved.getDouble("better_manufacturing:fuel_remainder");
        helper.assertTrue(remainder > 0 && remainder < 1, "Fractional paid-fuel state not bounded at save");
        var drops = Block.getDrops(fast.getBlockState(), helper.getLevel(), fast.getBlockPos(), fast);
        var picked = drops.stream().filter(drop -> drop.is(fast.getBlockState().getBlock().asItem())).findFirst().orElseThrow();
        helper.assertTrue(picked.getTagElement("BlockEntityTag") != null
                && Math.abs(picked.getTagElement("BlockEntityTag").getDouble("better_manufacturing:fuel_remainder") - remainder) < 1e-9,
            "Native PNCR drop reset the paid-fuel fraction on pickup");
        var placedPos = helper.absolutePos(new BlockPos(5, 2, 2));
        helper.assertTrue(((BlockItem) picked.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), placedPos,
            Direction.WEST, picked.copy(), Direction.UP)).consumesAction(), "Native compressor pickup placement failed");
        var replaced = helper.getLevel().getBlockEntity(placedPos);
        helper.assertTrue(replaced instanceof PneupressureFuelCost
            && Math.abs(((PneupressureFuelCost) replaced).pneupressure$fuelRemainder() - remainder) < 1e-9,
            "Native compressor pickup placement reset fractional fuel debt");
        var restoredBE = fast.getType().create(fast.getBlockPos(), fast.getBlockState());
        helper.assertTrue(restoredBE instanceof AirCompressorBlockEntity, "Native compressor type changed on reload");
        restoredBE.setLevel(helper.getLevel()); restoredBE.load(saved);
        var restored = (AirCompressorBlockEntity) restoredBE;
        restored.onUpgradesChanged();
        helper.assertTrue(PneupressureBuild.rate(restored, "compression_rate") == 1.5
            && restored.getSpeedUsageMultiplierFromUpgrades() == upgradeCost,
            "Reload lost rated construction or native speed-upgrade fuel multiplier");
        int startFuel = restored.burnTime, startAir = air(restored);
        double expectedCost = Math.round(restored.getBaseProduction() * 1.5) * upgradeCost / 10;
        int charged = 0;
        for (int n = 0; n < 16; n++) {
            restored.tickServer();
            helper.assertTrue(restored.curFuelUsage >= 1, "Fast air tick rounded fuel cost to zero");
            charged += restored.curFuelUsage;
        }
        int madeAir = air(restored) - startAir;
        int expectedCharged = (int) Math.floor(16 * expectedCost + remainder + 1e-6);
        helper.assertTrue(madeAir > 16 * 10 && charged == startFuel - restored.burnTime
            && charged == expectedCharged,
            "Native upgraded compressor air/fuel accounting diverged after reload: air=" + madeAir
                + " paid=" + charged + " expected=" + expectedCharged);
        var persisted = restored.saveWithFullMetadata();
        var again = restored.getType().create(restored.getBlockPos(), restored.getBlockState());
        helper.assertTrue(again instanceof AirCompressorBlockEntity, "Native compressor unavailable on second reload");
        again.setLevel(helper.getLevel()); again.load(persisted);
        helper.assertTrue(Math.abs(again.saveWithFullMetadata().getDouble("better_manufacturing:fuel_remainder")
            - persisted.getDouble("better_manufacturing:fuel_remainder")) < 1e-9,
            "Unload/reload reset fractional fuel accounting credit");
        var corrupt = persisted.copy(); corrupt.putDouble("better_manufacturing:fuel_remainder", 999);
        corrupt.putString("UnrelatedOwnerField", "preserve");
        var resetBE = again.getType().create(again.getBlockPos(), again.getBlockState());
        helper.assertTrue(resetBE instanceof AirCompressorBlockEntity, "Native compressor unavailable for malformed-debt fallback");
        resetBE.setLevel(helper.getLevel()); resetBE.load(corrupt);
        helper.assertTrue(resetBE instanceof PneupressureFuelCost
            && ((PneupressureFuelCost) resetBE).pneupressure$fuelRemainder() == 0
            && corrupt.getString("UnrelatedOwnerField").equals("preserve"),
            "Malformed fractional fuel debit did not reset safely or erased unrelated owner NBT");
        helper.succeed();
    }

    @GameTest(template = "pneu_workshop")
    public static void lightningChargesNativeGridShareAtEachRecipientRate(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"), "Real PNCR/TCon required");
        var nativeBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft:electrostatic_compressor"));
        var rotor = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft:turbine_rotor"));
        helper.assertTrue(nativeBlock != null && rotor != null && rotor != Items.AIR, "Native electrostatic grid equipment absent");
        var sourcePos = new BlockPos(2, 2, 2); var recipientPos = new BlockPos(5, 2, 2);
        helper.setBlock(sourcePos, nativeBlock.defaultBlockState()); helper.setBlock(recipientPos, nativeBlock.defaultBlockState());
        var sourceBE = helper.getLevel().getBlockEntity(helper.absolutePos(sourcePos));
        var recipientBE = helper.getLevel().getBlockEntity(helper.absolutePos(recipientPos));
        helper.assertTrue(sourceBE instanceof ElectrostaticCompressorBlockEntity
            && recipientBE instanceof ElectrostaticCompressorBlockEntity,
            "PNCR native electrostatic BE absent");
        var source = (ElectrostaticCompressorBlockEntity) sourceBE;
        var recipient = (ElectrostaticCompressorBlockEntity) recipientBE;
        for (var entry : Map.of(source, "tconstruct:iron", recipient, "tconstruct:slimewood").entrySet()) {
            var parts = Map.of("body", part("tconstruct:large_plate", entry.getValue()),
                "handle", part("tconstruct:tough_handle", entry.getValue()), "rotor", new ItemStack(rotor));
            var build = NativePartCapture.bake(PneupressureSettings.COMPRESSION, parts,
                () -> PneupressureSettings.bake("pneumaticcraft:electrostatic_compressor", PneupressureSettings.COMPRESSION, parts));
            var tag = new CompoundTag(); SettingsNbt.write(tag, build);
            ((PneupressureBuild) entry.getKey()).pneupressure$install(tag.get(SettingsNbt.KEY));
        }
        double slowerRate = Math.sqrt(.75 * .85); // native slimewood head+handle vs iron
        helper.assertTrue(PneupressureBuild.rate(source, "compression_rate") == 1
            && Math.abs(PneupressureBuild.rate(recipient, "compression_rate") - slowerRate) < 1e-6,
            "Native materials did not distinguish grid recipients");
        source.tickServer(); recipient.tickServer();
        helper.assertTrue(air(source) == 0 && air(recipient) == 0, "Electrostatic compressor made air without lightning");
        // Pinned PNCR grid caller invokes member.onStruckByLightning(member, grid.size())
        // for each member. This direct private-callback robustness probe follows that
        // receiver==target shape for both materials, but does NOT trigger native weather
        // lightning or prove complete grid traversal. Both stay <= 200000 / 100.
        var strike = ElectrostaticCompressorBlockEntity.class.getDeclaredMethod("onStruckByLightning",
            ElectrostaticCompressorBlockEntity.class, int.class);
        strike.setAccessible(true);
        strike.invoke(source, source, 100);
        helper.assertTrue(air(source) == 2000 && air(recipient) == 0,
            "Native iron grid member charged incorrectly or leaked air to slimewood");
        strike.invoke(recipient, recipient, 100);
        helper.assertTrue(air(source) == 2000 && air(recipient) == Math.round(2000 * slowerRate),
            "Slimewood grid member failed to receive bounded native-rated lightning share: iron="
                + air(source) + " slimewood=" + air(recipient));
        helper.succeed();
    }
}
