package com.bettercontent.manufacturing.gametest;

import com.bettercontent.manufacturing.ManufacturingMod;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartSettings;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

/** Baked v1 values must match the native Create consumer, not merely pass generic NBT decoding. */
@GameTestHolder(ManufacturingMod.ID)
@PrefixGameTestTemplate(false)
public final class Wave2CoreSettingsTests {
    @GameTest(template = "drill_workshop")
    public static void wrongNativePropertyFallsBackWithoutDestroyingTag(GameTestHelper helper) {
        helper.setBlock(2, 1, 2, AllBlocks.MECHANICAL_PRESS.getDefaultState());
        // Create posts BlockEntityBehaviourEvent on the first native BE tick, not on setBlock.
        helper.runAfterDelay(2, () -> checkPressAfterNativeInitialization(helper));
    }
    private static void checkPressAfterNativeInitialization(GameTestHelper helper) {
        var block = (KineticBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.assertTrue(block != null, "Create press missing from real fixture");
        var behaviour = KineticVariantBehaviour.get(block);
        helper.assertTrue(behaviour != null, "Create native variant behaviour not attached");
        var holder = new CompoundTag(); holder.putInt("UnrelatedNativeData", 73);
        for (double rate : new double[]{.75, 1.0, 1.5}) {
            SettingsNbt.write(holder, new MachineSettings("create:mechanical_press", Map.of("work_rate", rate), Map.of(), null));
            behaviour.read(holder, false);
            helper.assertTrue(behaviour.settings() != null && behaviour.rate() == rate, "Valid baked native work rate was lost");
        }
        SettingsNbt.write(holder, new MachineSettings("create:mechanical_press", Map.of("airflow", 1.5), Map.of(), null));
        helper.assertTrue(SettingsNbt.read(holder, "create:mechanical_press") != null, "Wrong-property control must be valid v1 NBT");
        var original = holder.get(SettingsNbt.KEY).copy();
        behaviour.read(holder, false);
        helper.assertTrue(behaviour.settings() == null && behaviour.rate() == 1, "Foreign property changed native press work");
        var persisted = new CompoundTag(); behaviour.writeSafe(persisted);
        helper.assertTrue(original.equals(persisted.get(SettingsNbt.KEY)) && holder.getInt("UnrelatedNativeData") == 73,
            "Fallback discarded settings tag or unrelated native data");

        SettingsNbt.write(holder, new MachineSettings("create:mechanical_press", Map.of("work_rate", 1.5), Map.of(), null));
        helper.assertTrue(SettingsNbt.read(holder, "create:mechanical_press") != null, "Out-of-range control did not start valid");
        holder.getCompound(SettingsNbt.KEY).getCompound("properties").putDouble("work_rate", 100);
        original = holder.get(SettingsNbt.KEY).copy();
        behaviour.read(holder, false);
        helper.assertTrue(behaviour.settings() == null && behaviour.rate() == 1 && holder.getInt("UnrelatedNativeData") == 73,
            "Out-of-range native rate did not fall back non-destructively");
        persisted = new CompoundTag(); behaviour.writeSafe(persisted);
        helper.assertTrue(original.equals(persisted.get(SettingsNbt.KEY)), "Out-of-range fallback erased original raw setting");

        SettingsNbt.write(holder, new MachineSettings("create:mechanical_press", Map.of("work_rate", .75), Map.of(), null));
        helper.assertTrue(SettingsNbt.read(holder, "create:mechanical_press") != null, "Wrong-output control did not start valid");
        holder.getCompound(SettingsNbt.KEY).putString("output", "create:mechanical_pump");
        original = holder.get(SettingsNbt.KEY).copy();
        behaviour.read(holder, false);
        helper.assertTrue(behaviour.settings() == null && behaviour.rate() == 1 && holder.getInt("UnrelatedNativeData") == 73,
            "Wrong-output settings changed native press work");
        persisted = new CompoundTag(); behaviour.writeSafe(persisted);
        helper.assertTrue(original.equals(persisted.get(SettingsNbt.KEY)), "Wrong-output fallback erased original raw setting");

        holder.putString(SettingsNbt.KEY, "old-metadata");
        behaviour.read(holder, false);
        persisted = new CompoundTag(); behaviour.writeSafe(persisted);
        helper.assertTrue(behaviour.rate() == 1 && persisted.getString(SettingsNbt.KEY).equals("old-metadata"),
            "Bounded unknown-version/malformed tag was discarded instead of preserved");
        holder.putString(SettingsNbt.KEY, "x".repeat(3000));
        var untrustedInput = holder.copy();
        behaviour.read(holder, false);
        persisted = new CompoundTag(); behaviour.writeSafe(persisted);
        helper.assertTrue(behaviour.rate() == 1 && !persisted.contains(SettingsNbt.KEY) && holder.equals(untrustedInput)
            && holder.getInt("UnrelatedNativeData") == 73, "Oversized untrusted tag was copied or unrelated data modified");
        helper.succeed();
    }

    @GameTest(template = "drill_workshop")
    public static void configuredWhiskUsesOnlyItsOwnNativeRate(GameTestHelper helper) {
        var whiskItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:whisk"));
        helper.assertTrue(whiskItem != null, "Real Create whisk is missing from fixture");
        var whisk = new ItemStack(whiskItem);
        var plain = PartSettings.bake("create:mechanical_mixer", "better_manufacturing:create/mixing", Map.of("whisk", whisk));
        helper.assertTrue(plain.rate() == 1, "Unconfigured native whisk no longer has baseline rate");
        SettingsNbt.write(whisk.getOrCreateTag(), new MachineSettings("create:whisk", Map.of("work_rate", 1.5), Map.of("handle", "tconstruct:wood"), null));
        var inherited = PartSettings.bake("create:mechanical_mixer", "better_manufacturing:create/mixing", Map.of("whisk", whisk));
        helper.assertTrue(inherited.rate() == 1.5 && "tconstruct:wood".equals(inherited.materials().get("whisk_handle")),
            "Mixer lost configured native whisk rate/labels");
        SettingsNbt.write(whisk.getOrCreateTag(), new MachineSettings("create:whisk", Map.of("pressure", 1.5), Map.of(), null));
        boolean rejected = false;
        try { PartSettings.bake("create:mechanical_mixer", "better_manufacturing:create/mixing", Map.of("whisk", whisk)); }
        catch (IllegalArgumentException wrongFunction) { rejected = true; }
        helper.assertTrue(rejected, "Wrong-family whisk rating laundered into a new mixer craft");
        helper.succeed();
    }
}
