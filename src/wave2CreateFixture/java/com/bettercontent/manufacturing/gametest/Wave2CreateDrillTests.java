package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.create.DrillEngineering;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Consumer rejects a well-formed but wrong-family property before native drilling. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateDrillTests {
    private Wave2CreateDrillTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyV1PropertyFallsBackWithoutDestroyingNativeTag(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create/TConstruct dependencies required");
        // Pack wood has no native head; pinned slimewood is eligible and rates 0.75.
        var good = CreateDrillTests.craft(helper, "tconstruct:slimewood");
        var original = DrillEngineering.evaluate(good.getTag());
        helper.assertTrue(original.tier() != null && original.rate() == .75,
            "Production cheap native drill prerequisite has no rated harvest tier");
        var wrong = good.getTag().copy();
        wrong.putString("create:native_custom_data", "retain");
        var legacy = wrong.getCompound(SettingsNbt.KEY);
        legacy.putInt("version", 1);
        legacy.putString("legacy_property", "pressure"); // A v1 record must never be replayed as v2 cutting evidence.
        helper.assertTrue(SettingsNbt.read(wrong, "create:mechanical_drill") == null,
            "Version-one drill record was accepted as v2 numeric evidence");
        var baseline = DrillEngineering.evaluate(wrong);
        helper.assertTrue(baseline.rate() == 1 && baseline.tier() == null,
            "Drill applied pressure/airflow as fake native cutting speed");
        helper.assertTrue(wrong.getString("create:native_custom_data").equals("retain")
            && wrong.getCompound(SettingsNbt.KEY).getString("legacy_property").equals("pressure"),
            "Fallback destructively edited native NBT");
        helper.succeed();
    }
}
