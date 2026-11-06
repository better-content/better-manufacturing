package com.bettercontent.manufacturing.persistence;

import com.bettercontent.manufacturing.api.ComponentProvenance;
import com.bettercontent.manufacturing.api.MachineBuild;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsNbtTest {
    private static final String PRESS = "create:mechanical_press";

    private static MachineSettings press(double selected, double reference) {
        var inputs = new NumericInputs("better_manufacturing:create/pressing",
            Map.of("plate", Map.of(Measure.HEAD_MINING_SPEED, new NumericInputs.Stat(selected, reference))), Map.of());
        return new MachineSettings(PRESS, Map.of("work_rate", Math.max(.75, Math.min(1.5, selected / reference))),
            Map.of("plate", "tconstruct:iron"), null, inputs);
    }

    @Test void v2ReplaysOnlySavedNumbers() {
        var holder = new CompoundTag(); holder.putInt("native", 23);
        SettingsNbt.write(holder, press(9, 6));
        assertEquals(2, holder.getCompound(SettingsNbt.KEY).getInt("version"));
        assertEquals(1.5, SettingsNbt.read(holder, PRESS).rate());
        assertEquals(23, holder.getInt("native"));
        var corrupted = holder.copy();
        corrupted.getCompound(SettingsNbt.KEY).getCompound("parts").getCompound("plate")
            .getCompound("HEAD_MINING_SPEED").putDouble("reference", 0);
        var before = corrupted.copy();
        assertNull(SettingsNbt.read(corrupted, PRESS));
        assertEquals(before, corrupted, "Invalid numerical inputs must remain untouched");
    }

    @Test void v1SettingsAreRejectedAndNeverWritten() {
        var holder = new CompoundTag(); holder.putInt("native", 23);
        var obsolete = new CompoundTag(); obsolete.putInt("version", 1); obsolete.putString("output", PRESS);
        var props = new CompoundTag(); props.putDouble("work_rate", 1.5); obsolete.put("properties", props);
        obsolete.put("materials", new CompoundTag()); holder.put(SettingsNbt.KEY, obsolete);
        var before = holder.copy();
        assertNull(SettingsNbt.read(holder, PRESS));
        assertEquals(before, holder, "Reading v1 must not mutate unrelated or old NBT");
        assertThrows(IllegalArgumentException.class, () -> SettingsNbt.write(holder,
            new MachineSettings(PRESS, Map.of("work_rate", 1.5), Map.of(), null)));
        assertEquals(before, holder, "Rejecting a v1 write must not overwrite the holder");
    }

    @Test void v1DrillBuildIsRejectedWithoutMutation() {
        var part = new ComponentProvenance("tconstruct:pick_head", "tconstruct:iron", "tconstruct:head", 180, true, Map.of());
        var build = new MachineBuild(MachineBuild.VERSION, "create:mechanical_drill", "better_manufacturing:create/cutting",
            Map.of("head", part));
        var holder = new CompoundTag(); holder.putInt("native", 23); BuildNbt.write(holder, build);
        var record = holder.getCompound(BuildNbt.KEY); record.putInt("version", 1); record.remove("qualifications");
        var before = holder.copy();
        var read = BuildNbt.read(holder, build.chassis(), build.function());
        assertTrue(read.present()); assertFalse(read.valid());
        assertEquals(before, holder, "Rejected v1 Drill build must remain inert and untouched");
    }
}
