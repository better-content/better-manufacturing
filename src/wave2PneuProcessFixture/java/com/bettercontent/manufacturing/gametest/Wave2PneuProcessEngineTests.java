package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import com.bettercontent.manufacturing.integration.compressed.CompressedVariant;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.lgmrszd.compressedcreativity.blocks.compressed_air_engine.CompressedAirEngineBlockEntity;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/** A larger baked rating cannot gain free air when CC's native engine already runs at 256 RPM. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuProcessEngineTests {
    private static final String ID = "compressedcreativity:compressed_air_engine";
    /** Synthetic numerical boundary, not a claim of obtainable 1.5-rated parts. */
    private static MachineSettings boundary(double rating) {
        var numeric = new NumericInputs("better_manufacturing:compressedcreativity/conversion",
            Map.of("plate", Map.of(Measure.HEAD_MINING_SPEED, new NumericInputs.Stat(rating, 1)),
                "handle", Map.of(Measure.HANDLE_MINING_SPEED, new NumericInputs.Stat(rating - 1, 0))),
            Map.of("rotor", Map.of("conversion", rating)));
        return new MachineSettings(ID, NumericEvaluator.evaluate(ID, numeric, null), Map.of(), null, numeric);
    }
    private static CompressedAirEngineBlockEntity engine(GameTestHelper helper, int x, double rating) {
        var source = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(ID)));
        helper.assertTrue(source.getItem() instanceof BlockItem, "Native CC engine item missing");
        if (rating != 1) SettingsNbt.write(source.getOrCreateTag(), boundary(rating));
        var pos = helper.absolutePos(new BlockPos(x, 2, 2));
        helper.assertTrue(((BlockItem) source.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos,
            Direction.WEST, source.copy(), Direction.UP)).consumesAction(), "Native CC engine placement failed");
        var block = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(block instanceof CompressedAirEngineBlockEntity, "Native CC engine BE missing");
        return (CompressedAirEngineBlockEntity) block;
    }
    private static IAirHandlerMachine air(CompressedAirEngineBlockEntity engine) {
        return engine.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY)
            .orElseThrow(() -> new AssertionError("Native CC engine air handler missing"));
    }
    @GameTest(template = "wave2_pneu_process_workshop")
    public static void cappedRpmCannotImproveAirEfficiencyWithoutAdditionalWork(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("compressedcreativity") && ModList.get().isLoaded("pneumaticcraft"),
            "Real CC/PNCR not loaded");
        var nativeEngine = engine(helper, 2, 1);
        var high = engine(helper, 5, 1.5);
        var low = engine(helper, 8, .75);
        helper.assertTrue(CompressedVariant.get(high) != null && CompressedVariant.get(high).property("conversion") == 1.5,
            "Valid bounded engine settings did not reach native BE");
        for (var block : new CompressedAirEngineBlockEntity[]{nativeEngine, high, low}) {
            var handler = air(block);
            handler.addAir(4 * handler.getVolume());
            helper.assertTrue(handler.getPressure() >= 3, "Native engine lacks its 3-bar work pressure");
            block.tick(); // Native engine crosses its own working pressure threshold.
        }
        helper.assertTrue(Math.abs(nativeEngine.getGeneratedSpeed()) == 256
            && Math.abs(high.getGeneratedSpeed()) == 256 && Math.abs(low.getGeneratedSpeed()) == 192,
            "Native RPM limit or low-rated work response changed");
        int baselineBefore = air(nativeEngine).getAir();
        int highBefore = air(high).getAir();
        int lowBefore = air(low).getAir();
        for (int tick = 0; tick < 30; tick++) {
            nativeEngine.tick(); high.tick(); low.tick();
        }
        int baselineCost = baselineBefore - air(nativeEngine).getAir();
        int highCost = highBefore - air(high).getAir();
        int lowCost = lowBefore - air(low).getAir();
        helper.assertTrue(baselineCost > 0 && highCost == baselineCost,
            "Above-cap rating reduced native air cost at unchanged 256 RPM");
        helper.assertTrue(lowCost > 0 && lowCost < baselineCost,
            "Lower work setting failed to lower both RPM and matching air use");
        helper.succeed();
    }

    @GameTest(template = "wave2_pneu_process_workshop")
    public static void oversizedSettingsNeverEnterNativePacketOrDropButBoundedUnknownDoes(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("compressedcreativity") && ModList.get().isLoaded("pneumaticcraft"),
            "Real CC/PNCR not loaded");
        var machine = engine(helper, 2, 1);
        // Create attaches block-entity behaviours during its native init tick;
        // immediate placement only guarantees the native CC block entity exists.
        helper.runAtTickTime(5, () -> {
        var variant = CompressedVariant.get(machine);
        helper.assertTrue(variant != null, "Native CC settings behaviour missing after ticks");
        var oversized = new CompoundTag();
        var untrusted = new CompoundTag();
        untrusted.putString("payload", "x".repeat(3000));
        oversized.put(SettingsNbt.KEY, untrusted);
        variant.install(oversized);
        var save = new CompoundTag();
        save.putString("unrelated_native", "preserve");
        variant.write(save, false);
        helper.assertTrue(variant.property("conversion") == 1 && !save.contains(SettingsNbt.KEY)
            && !variant.item().getTag().contains(SettingsNbt.KEY)
            && save.getString("unrelated_native").equals("preserve"),
            "Oversized settings were copied into native save/drop or changed baseline");
        var bounded = new CompoundTag();
        SettingsNbt.write(bounded, boundary(.75));
        bounded.getCompound(SettingsNbt.KEY).putInt("version", 999);
        variant.install(bounded);
        var retained = new CompoundTag();
        variant.writeSafe(retained);
        helper.assertTrue(variant.property("conversion") == 1 && retained.getCompound(SettingsNbt.KEY).getInt("version") == 999,
            "Bounded unknown settings were erased instead of using native baseline");
        var wrongRole = new CompoundTag();
        SettingsNbt.write(wrongRole, boundary(.75));
        wrongRole.getCompound(SettingsNbt.KEY).putString("function", "better_manufacturing:compressedcreativity/blower");
        variant.install(wrongRole);
        helper.assertTrue(variant.settings() == null && variant.property("conversion") == 1
            && variant.item().getTag().contains(SettingsNbt.KEY),
            "Wrong-family v2 engine settings were executed or stripped from safe drops");
        helper.succeed();
        });
    }
}
