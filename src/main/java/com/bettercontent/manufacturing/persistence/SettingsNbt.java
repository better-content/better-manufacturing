package com.bettercontent.manufacturing.persistence;

import com.bettercontent.manufacturing.api.MachineSettings;
import net.minecraft.nbt.*;
import java.util.*;

/** Only this small record is copied by placement/drop hooks; unrelated native NBT is untouched. */
public final class SettingsNbt {
    public static final String KEY = "better_manufacturing:settings";
    private SettingsNbt() {}
    public static void write(CompoundTag holder, MachineSettings settings) {
        var tag = new CompoundTag(); tag.putInt("version", MachineSettings.VERSION); tag.putString("output", settings.output());
        var properties = new CompoundTag(); settings.properties().forEach(properties::putDouble); tag.put("properties", properties);
        var materials = new CompoundTag(); settings.materials().forEach(materials::putString); tag.put("materials", materials);
        if (settings.harvestTier() != null) tag.putString("harvest_tier", settings.harvestTier());
        holder.put(KEY, tag);
    }
    public static MachineSettings read(CompoundTag holder, String output) {
        if (holder == null || !holder.contains(KEY, Tag.TAG_COMPOUND)) return null;
        try {
            var tag = holder.getCompound(KEY); BoundedNbt.check(tag);
            if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != MachineSettings.VERSION
                || !tag.contains("output", Tag.TAG_STRING) || !tag.getString("output").equals(output)
                || !tag.contains("properties", Tag.TAG_COMPOUND) || !tag.contains("materials", Tag.TAG_COMPOUND)) return null;
            if (!Set.of("version", "output", "properties", "materials", "harvest_tier").containsAll(tag.getAllKeys())) return null;
            var values = new HashMap<String, Double>(); var labels = new HashMap<String, String>();
            var props = tag.getCompound("properties"); var materials = tag.getCompound("materials");
            for (String key : props.getAllKeys()) {
                if (!props.contains(key, Tag.TAG_DOUBLE)) return null;
                values.put(key, props.getDouble(key));
            }
            for (String key : materials.getAllKeys()) {
                if (!materials.contains(key, Tag.TAG_STRING)) return null;
                labels.put(key, materials.getString(key));
            }
            if (tag.contains("harvest_tier") && !tag.contains("harvest_tier", Tag.TAG_STRING)) return null;
            return new MachineSettings(output, values, labels, tag.contains("harvest_tier") ? tag.getString("harvest_tier") : null);
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
