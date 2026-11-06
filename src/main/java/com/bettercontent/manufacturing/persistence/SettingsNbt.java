package com.bettercontent.manufacturing.persistence;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import net.minecraft.nbt.*;
import java.util.*;

/** Only this small record is copied by placement/drop hooks; unrelated native NBT is untouched. */
public final class SettingsNbt {
    public static final String KEY = "better_manufacturing:settings";
    private SettingsNbt() {}
    public static void write(CompoundTag holder, MachineSettings settings) {
        var numeric = settings.numericInputs();
        if (numeric == null) throw new IllegalArgumentException("Configured output requires numeric inputs");
        var tag = new CompoundTag();
        tag.putInt("version", MachineSettings.VERSION);
        tag.putString("output", settings.output());
        var materials = new CompoundTag(); settings.materials().forEach(materials::putString); tag.put("materials", materials);
        if (settings.harvestTier() != null) tag.putString("harvest_tier", settings.harvestTier());
        if (!NumericEvaluator.evaluate(settings.output(), numeric, settings.harvestTier()).equals(settings.properties()))
            throw new IllegalArgumentException("Numeric inputs do not reproduce machine properties");
        tag.putString("function", numeric.function());
        var parts = new CompoundTag();
        numeric.parts().forEach((role, measures) -> {
            var stats = new CompoundTag();
            measures.forEach((measure, pair) -> {
                var value = new CompoundTag();
                value.putDouble("selected", pair.selected()); value.putDouble("reference", pair.reference());
                stats.put(measure.name(), value);
            });
            parts.put(role, stats);
        });
        tag.put("parts", parts);
        var components = new CompoundTag();
        numeric.components().forEach((role, properties) -> {
            var values = new CompoundTag(); properties.forEach(values::putDouble);
            components.put(role, values);
        });
        tag.put("components", components);
        BoundedNbt.check(tag);
        holder.put(KEY, tag);
    }
    /** An actual consumed configured component contributes its final numerical response to a new output. */
    public static MachineSettings inherit(String output, String property, MachineSettings source) {
        if (source == null || !source.properties().containsKey(property))
            throw new IllegalArgumentException("Missing inherited property");
        var numeric = new NumericInputs("better_manufacturing:inherit/" + property, Map.of(),
            Map.of("source", Map.of(property, source.properties().get(property))));
        return new MachineSettings(output, NumericEvaluator.evaluate(output, numeric, null), source.materials(), null, numeric);
    }
    /** Keep both independent native compressor limits during a real consumed upgrade. */
    public static MachineSettings inheritCompressor(String output, MachineSettings source) {
        if (source == null || !source.properties().keySet().equals(Set.of("compression_rate", "pressure_envelope")))
            throw new IllegalArgumentException("Incomplete compressor upgrade");
        var numeric = new NumericInputs("better_manufacturing:inherit/compressor_upgrade", Map.of(),
            Map.of("source", source.properties()));
        return new MachineSettings(output, NumericEvaluator.evaluate(output, numeric, null), source.materials(), null, numeric);
    }
    public static MachineSettings read(CompoundTag holder, String output) {
        if (holder == null || !holder.contains(KEY, Tag.TAG_COMPOUND)) return null;
        try {
            var tag = holder.getCompound(KEY); BoundedNbt.check(tag);
            if (!tag.contains("version", Tag.TAG_INT) || !tag.contains("output", Tag.TAG_STRING)
                || !tag.getString("output").equals(output) || !tag.contains("materials", Tag.TAG_COMPOUND)) return null;
            if (tag.getInt("version") != MachineSettings.VERSION) return null;
            if (tag.contains("harvest_tier") && !tag.contains("harvest_tier", Tag.TAG_STRING)) return null;
            String harvestTier = tag.contains("harvest_tier") ? tag.getString("harvest_tier") : null;
            var labels = new HashMap<String, String>();
            var materials = tag.getCompound("materials");
            for (String key : materials.getAllKeys()) {
                if (!materials.contains(key, Tag.TAG_STRING)) return null;
                labels.put(key, materials.getString(key));
            }
            if (!tag.contains("function", Tag.TAG_STRING) || !tag.contains("parts", Tag.TAG_COMPOUND)
                || !tag.contains("components", Tag.TAG_COMPOUND)
                || !Set.of("version", "output", "function", "parts", "components", "materials", "harvest_tier").containsAll(tag.getAllKeys())) return null;
            var parts = new HashMap<String, Map<NativePartRates.Measure, NumericInputs.Stat>>();
            var partTags = tag.getCompound("parts");
            for (String role : partTags.getAllKeys()) {
                if (!partTags.contains(role, Tag.TAG_COMPOUND)) return null;
                var measures = new EnumMap<NativePartRates.Measure, NumericInputs.Stat>(NativePartRates.Measure.class);
                var stats = partTags.getCompound(role);
                for (String name : stats.getAllKeys()) {
                    if (!stats.contains(name, Tag.TAG_COMPOUND)) return null;
                    var pair = stats.getCompound(name);
                    if (!pair.getAllKeys().equals(Set.of("selected", "reference"))
                        || !pair.contains("selected", Tag.TAG_DOUBLE) || !pair.contains("reference", Tag.TAG_DOUBLE)) return null;
                    measures.put(NativePartRates.Measure.valueOf(name), new NumericInputs.Stat(pair.getDouble("selected"), pair.getDouble("reference")));
                }
                parts.put(role, measures);
            }
            var components = new HashMap<String, Map<String, Double>>();
            var componentTags = tag.getCompound("components");
            for (String role : componentTags.getAllKeys()) {
                if (!componentTags.contains(role, Tag.TAG_COMPOUND)) return null;
                var properties = new HashMap<String, Double>();
                var fields = componentTags.getCompound(role);
                for (String property : fields.getAllKeys()) {
                    if (!fields.contains(property, Tag.TAG_DOUBLE)) return null;
                    properties.put(property, fields.getDouble(property));
                }
                components.put(role, properties);
            }
            var numeric = new NumericInputs(tag.getString("function"), parts, components);
            return new MachineSettings(output, NumericEvaluator.evaluate(output, numeric, harvestTier), labels, harvestTier, numeric);
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
