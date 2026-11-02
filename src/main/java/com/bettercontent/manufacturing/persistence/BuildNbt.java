package com.bettercontent.manufacturing.persistence;

import com.bettercontent.manufacturing.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.Set;

/** Read never mutates the holder, including malformed/unknown future records. */
public final class BuildNbt {
    public static final String KEY = "better_manufacturing:build";
    public record ReadResult(MachineBuild build, String error, boolean present) {
        public boolean valid() { return build != null; }
    }
    private BuildNbt() {}
    public static ReadResult read(CompoundTag holder, String chassis, String function) {
        if (holder == null || !holder.contains(KEY)) return new ReadResult(MachineBuild.stock(chassis, function), "", false);
        if (!holder.contains(KEY, Tag.TAG_COMPOUND)) return new ReadResult(null, "invalid_build_tag_type", true);
        try {
            CompoundTag root = holder.getCompound(KEY);
            typed(root, "version", Tag.TAG_INT);
            int version = root.getInt("version");
            if (version != 1 && version != MachineBuild.VERSION) throw new IllegalArgumentException("unsupported_build_version");
            exact(root, version == 1 ? Set.of("version", "chassis", "function", "components")
                : Set.of("version", "chassis", "function", "components", "qualifications"));
            if (version == 1 && !(chassis.equals("create:mechanical_drill") && function.equals("better_manufacturing:create/cutting")))
                throw new IllegalArgumentException("unsupported_legacy_chassis");
            var qualifications = new java.util.HashSet<String>();
            if (version == MachineBuild.VERSION) {
                typed(root, "qualifications", Tag.TAG_LIST);
                var list = (net.minecraft.nbt.ListTag) root.get("qualifications");
                if (list.size() > MachineBuild.MAX_QUALIFICATIONS || (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING))
                    throw new IllegalArgumentException("invalid_qualifications");
                for (int i = 0; i < list.size(); i++) {
                    if (!qualifications.add(Identifiers.id(list.getString(i)))) throw new IllegalArgumentException("duplicate_qualification");
                }
            }
            typed(root, "chassis", Tag.TAG_STRING);
            typed(root, "function", Tag.TAG_STRING); typed(root, "components", Tag.TAG_COMPOUND);
            CompoundTag parts = root.getCompound("components");
            if (parts.size() > MachineBuild.MAX_ROLES) throw new IllegalArgumentException("too_many_roles");
            var components = new LinkedHashMap<String, ComponentProvenance>();
            for (String role : parts.getAllKeys()) {
                typed(parts, role, Tag.TAG_COMPOUND);
                CompoundTag p = parts.getCompound(role);
                exact(p, Set.of("item", "material", "stat_role", "quanta", "whole_part", "retained"));
                typed(p, "item", Tag.TAG_STRING); typed(p, "material", Tag.TAG_STRING); typed(p, "stat_role", Tag.TAG_STRING);
                typed(p, "quanta", Tag.TAG_LONG); typed(p, "whole_part", Tag.TAG_BYTE); typed(p, "retained", Tag.TAG_COMPOUND);
                if (p.getByte("whole_part") != 0 && p.getByte("whole_part") != 1) throw new IllegalArgumentException("invalid_boolean");
                CompoundTag retained = p.getCompound("retained");
                if (retained.size() > 16) throw new IllegalArgumentException("too_many_metadata_fields");
                var data = new LinkedHashMap<String, String>();
                for (String key : retained.getAllKeys()) { typed(retained, key, Tag.TAG_STRING); data.put(key, retained.getString(key)); }
                components.put(role, new ComponentProvenance(p.getString("item"), p.getString("material"), p.getString("stat_role"),
                    p.getLong("quanta"), p.getBoolean("whole_part"), data));
            }
            MachineBuild build = new MachineBuild(MachineBuild.VERSION, root.getString("chassis"), root.getString("function"), components, qualifications);
            if (!build.chassis().equals(chassis) || !build.function().equals(function)) throw new IllegalArgumentException("chassis_mismatch");
            if (!build.hasProvenance()) throw new IllegalArgumentException("empty_crafted_build");
            return new ReadResult(build, "", true);
        } catch (IllegalArgumentException failure) {
            return new ReadResult(null, failure.getMessage(), true);
        }
    }
    public static void write(CompoundTag holder, MachineBuild build) {
        if (!build.hasProvenance()) throw new IllegalArgumentException("Stock builds do not manufacture provenance");
        CompoundTag root = new CompoundTag();
        root.putInt("version", build.version()); root.putString("chassis", build.chassis()); root.putString("function", build.function());
        CompoundTag parts = new CompoundTag();
        build.components().forEach((role, part) -> {
            CompoundTag p = new CompoundTag();
            p.putString("item", part.item()); p.putString("material", part.materialVariant()); p.putString("stat_role", part.statRole());
            p.putLong("quanta", part.materialQuanta()); p.putBoolean("whole_part", part.wholePart());
            CompoundTag retained = new CompoundTag(); part.retainedData().forEach(retained::putString); p.put("retained", retained);
            parts.put(role, p);
        });
        root.put("components", parts);
        var qualifications = new net.minecraft.nbt.ListTag();
        build.qualifications().stream().sorted().forEach(id -> qualifications.add(net.minecraft.nbt.StringTag.valueOf(id)));
        root.put("qualifications", qualifications);
        holder.put(KEY, root);
    }
    private static void typed(CompoundTag tag, String name, int type) {
        if (!tag.contains(name, type)) throw new IllegalArgumentException("invalid_field_type:" + name);
    }
    private static void exact(CompoundTag tag, Set<String> keys) {
        if (!tag.getAllKeys().equals(keys)) throw new IllegalArgumentException("unknown_or_missing_fields");
    }
}
