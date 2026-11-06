package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.integration.createactors.CreateActorsPartSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartCapture;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;

/** Native Create actor operation only: crop visits are construction-only, debris/tunnel breaking is rated. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CreateActorTests {
    private Wave2CreateActorTests() {}

    private static ItemStack part(String id, String material) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (!(item instanceof ToolPartItem tool)) throw new AssertionError("Missing real actor tool part " + id);
        return tool.withMaterial(MaterialVariantId.parse(material));
    }

    private static float nativeBreakingSpeed(GameTestHelper helper, Object behaviour, String block, CompoundTag tag) throws Exception {
        var state = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(block)).defaultBlockState();
        var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state, tag);
        var context = new MovementContext(helper.getLevel(), info, new CarriageContraption());
        context.motion = new Vec3(.3, 0, 0); // Native rail progress above its 2-point minimum.
        var method = BlockBreakingMovementBehaviour.class.getDeclaredMethod("getBlockBreakingSpeed", MovementContext.class);
        method.setAccessible(true);
        return (float) method.invoke(behaviour, context); // Invokes Roller override where applicable.
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void configuredPloughAndRollerChangeOnlyNativeBlockBreaking(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("create") && ModList.get().isLoaded("tconstruct"),
            "Real Create/TConstruct dependencies are required");
        for (String id : new String[]{"create:mechanical_plough", "create:mechanical_roller"}) {
            String function = id.endsWith("plough") ? "better_manufacturing:create/field_work" : "better_manufacturing:create/rolling";
            String partId = id.endsWith("plough") ? "tconstruct:small_axe_head" : "tconstruct:large_plate";
            Object movement = id.endsWith("plough") ? new PloughMovementBehaviour() : new RollerMovementBehaviour();
            // Pack wood has no head; pinned slimewood has an eligible head speed 4
            // versus iron 6, clamped to the native 0.75 working-rate floor.
            var cheapPart = part(partId, "tconstruct:slimewood");
            var ironPart = part(partId, "tconstruct:iron");
            var cheap = NativePartCapture.bake(function, Map.of("working_surface", cheapPart),
                () -> CreateActorsPartSettings.bake(id, function, Map.of("working_surface", cheapPart)));
            var iron = NativePartCapture.bake(function, Map.of("working_surface", ironPart),
                () -> CreateActorsPartSettings.bake(id, function, Map.of("working_surface", ironPart)));
            helper.assertTrue(Math.abs(cheap.rate() - .75) < 1e-6 && iron.rate() == 1,
                "Eligible native head contrast did not preserve bounded breaking work");
            var cheapTag = new CompoundTag(); SettingsNbt.write(cheapTag, cheap);
            var ironTag = new CompoundTag(); SettingsNbt.write(ironTag, iron);
            float slower = nativeBreakingSpeed(helper, movement, id, cheapTag);
            float baseline = nativeBreakingSpeed(helper, movement, id, ironTag);
            helper.assertTrue(baseline > 0 && Math.abs(slower / baseline - cheap.rate()) < .01,
                "Native contraption block-breaking ignored baked material work: " + id + " " + slower + " / " + baseline);
            var malformed = cheapTag.copy(); malformed.getCompound(SettingsNbt.KEY).putInt("version", 99);
            malformed.putString("unrelated_native_actor_data", "preserve");
            helper.assertTrue(Math.abs(nativeBreakingSpeed(helper, movement, id, malformed) - baseline) < .01,
                "Malformed settings did not fall back to native breaking rate");
            helper.assertTrue(malformed.getString("unrelated_native_actor_data").equals("preserve"), "Native actor data was removed");
        }
        helper.succeed();
    }

}
