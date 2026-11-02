package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.block.entity.VacuumPumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;

/** Native PNCR pressure gate, negative vacuum and whole-air accounting, not a condenser proxy. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2PneuProcessVacuumTests {
    private static final String ID = "pneumaticcraft:vacuum_pump";
    private static ItemStack part(String shape, String material) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct:" + shape));
        if (!(item instanceof ToolPartItem tool)) throw new AssertionError("Native TCon part missing: " + shape);
        return tool.withMaterial(new MaterialId(material));
    }
    private static VacuumPumpBlockEntity pump(GameTestHelper helper, int x, boolean wood) {
        var source = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(ID)));
        helper.assertTrue(source.getItem() instanceof BlockItem, "Real PNCR vacuum pump item unavailable");
        if (wood) {
            var rotor = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft:turbine_rotor")));
            helper.assertTrue(!rotor.isEmpty(), "Native rotor prerequisite missing");
            var settings = PneuProcessSettings.bake(ID, "better_manufacturing:pneuprocess/vacuum_thermal", Map.of(
                "plate", part("large_plate", "tconstruct:wood"), "handle", part("tough_handle", "tconstruct:wood"),
                "rotor_a", rotor, "rotor_b", rotor.copy()));
            helper.assertTrue(Math.abs(settings.properties().get("transfer_rate") - Math.sqrt(.75)) < .001,
                "Cheap material plus ordinary native rotors did not rate pump");
            SettingsNbt.write(source.getOrCreateTag(), settings);
        }
        var pos = helper.absolutePos(new BlockPos(x, 2, 2));
        helper.assertTrue(((BlockItem) source.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), pos,
            Direction.WEST, source.copy(), Direction.UP)).consumesAction(), "Native vacuum pump placement failed");
        var entity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(entity instanceof VacuumPumpBlockEntity, "Native vacuum pump BE missing");
        return (VacuumPumpBlockEntity) entity;
    }
    private static IAirHandlerMachine side(VacuumPumpBlockEntity pump, Direction side) {
        return pump.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY, side)
            .orElseThrow(() -> new AssertionError("PNCR air handler absent on expected native side " + side));
    }
    @GameTest(template = "wave2_pneu_process_workshop")
    public static void ratedVacuumPaysWholeAirAndNeverBypassesNativePressureGate(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("pneumaticcraft") && ModList.get().isLoaded("tconstruct"),
            "Real PNCR/TCon not loaded");
        var wood = pump(helper, 2, true);
        var iron = pump(helper, 5, false);
        helper.assertTrue(Math.abs(PneuProcessNative.rate(wood, "transfer_rate") - Math.sqrt(.75)) < .001
            && PneuProcessNative.rate(iron, "transfer_rate") == 1, "Settings did not transfer at native placement");
        var woodInput = side(wood, wood.getInputSide());
        var woodVacuum = side(wood, wood.getVacuumSide());
        var ironInput = side(iron, iron.getInputSide());
        var ironVacuum = side(iron, iron.getVacuumSide());
        wood.tickServer(); iron.tickServer();
        helper.assertTrue(woodVacuum.getAir() == 0 && ironVacuum.getAir() == 0,
            "Unpowered vacuum pump generated free vacuum");
        woodInput.addAir(3 * woodInput.getVolume());
        ironInput.addAir(3 * ironInput.getVolume());
        helper.assertTrue(woodInput.getPressure() > 2 && ironInput.getPressure() > 2,
            "Native PNCR pressure supply did not reach operating gate");
        var woodBefore = woodInput.getAir(); var ironBefore = ironInput.getAir();
        wood.tickServer(); iron.tickServer();
        helper.assertTrue(woodVacuum.getAir() == -3 && ironVacuum.getAir() == -4,
            "Native vacuum sign/transfer budget changed");
        helper.assertTrue(woodBefore - woodInput.getAir() == 9 && ironBefore - ironInput.getAir() == 10,
            "Vacuum transfer did not pay matching whole compressed-air budget");
        helper.succeed();
    }
}
