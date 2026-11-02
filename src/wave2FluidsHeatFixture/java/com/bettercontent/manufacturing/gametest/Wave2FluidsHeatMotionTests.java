package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The real Create motion methods, not just recipe settings, must vary with a consumed plate. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2FluidsHeatMotionTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void valveAndHoseMovementCarryMatchingKineticCosts(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create and TCon are required for fluid motion");
        String[] names = {"fluid_valve", "hose_pulley"};
        var machines = new KineticBlockEntity[2][2];
        for (int family = 0; family < names.length; family++) {
            for (int i = 0; i < 2; i++) {
                var crafted = WorkshopVariantTests.craft(helper, names[family],
                    i == 0 ? "tconstruct:rock#stone" : "tconstruct:iron");
                BlockPos pos = new BlockPos(1 + i, 1, 1 + family);
                var placed = ((BlockItem) crafted.getItem()).place(new DirectionalPlaceContext(
                    helper.getLevel(), helper.absolutePos(pos), Direction.WEST, crafted.copy(), Direction.UP));
                helper.assertTrue(placed.consumesAction(), names[family] + " native placement failed");
                machines[family][i] = (KineticBlockEntity) helper.getBlockEntity(pos);
            }
        }
        // Create attaches behaviours on the first ordinary BE tick. Never invoke initialize()
        // by hand: keep all four placed machines alive until their normal lifecycle has run.
        helper.runAtTickTime(3, () -> {
            for (int family = 0; family < names.length; family++) {
                String name = names[family];
                double[] work = new double[2], stress = new double[2], nativeImpact = new double[2];
                for (int i = 0; i < 2; i++) {
                    var machine = machines[family][i];
                    helper.assertTrue(machine == helper.getBlockEntity(new BlockPos(1 + i, 1, 1 + family)),
                        name + " native BE changed before work measurement");
                    var variant = FluidVariantBehaviour.get(machine);
                    helper.assertTrue(variant != null && variant.rate("fluid_rate") == (i == 0 ? .75 : 1),
                        name + " cheap/iron settings did not survive native lifecycle");
                    nativeImpact[i] = BlockStressValues.getImpact(machine.getBlockState().getBlock());
                    machine.setSpeed(64);
                    stress[i] = machine.calculateStressApplied();
                    if (machine instanceof HosePulleyBlockEntity hose) {
                        work[i] = Math.abs(hose.getMovementSpeed());
                    } else if (machine instanceof FluidValveBlockEntity valve) {
                        // Pinned Create keeps getChaseSpeed private; inspect its real motion path.
                        try {
                            var method = FluidValveBlockEntity.class.getDeclaredMethod("getChaseSpeed");
                            helper.assertTrue(method.trySetAccessible(), "Native valve motion method inaccessible");
                            work[i] = ((Number) method.invoke(valve)).doubleValue();
                        } catch (ReflectiveOperationException ex) {
                            throw new AssertionError("Native valve motion could not be inspected", ex);
                        }
                    } else throw new IllegalStateException("Wrong native fluid machine " + name);
                }
                helper.assertTrue(work[1] > 0 && work[0] > 0 && Math.abs(work[0] / work[1] - .75) < .001,
                    name + " native motion did not follow the fitting: " + work[0] + "/" + work[1]);
                helper.assertTrue(Math.abs(nativeImpact[0] - nativeImpact[1]) < .001,
                    name + " samples have different native stress configurations");
                if (name.equals("hose_pulley") || nativeImpact[1] > 0) {
                    // A hose must pay real native stress; an optionally configured valve must
                    // also scale it when its own native baseline has nonzero impact.
                    helper.assertTrue(nativeImpact[1] > 0 && stress[1] > 0 && stress[0] > 0
                        && stress[0] < stress[1] && Math.abs(stress[1] - nativeImpact[1]) < .001
                        && Math.abs(stress[0] / stress[1] - .75) < .001,
                        name + " did not pay scaled native kinetic cost: " + stress[0] + "/" + stress[1]);
                } else {
                    // Pinned Create has no valve impact entry: 0 native SU stays 0, not an
                    // invented charge or a meaningless 0/0 stress-ratio acceptance.
                    helper.assertTrue(stress[0] == 0 && stress[1] == 0,
                        "Native zero-cost valve acquired invented stress: " + stress[0] + "/" + stress[1]);
                }
            }
            helper.succeed();
        });
    }
}
