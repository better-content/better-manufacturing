package com.bettercontent.manufacturing.persistence;

import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;

/** Validate before recursive copying or SNBT encoding. Does not mutate the caller's tag. */
public final class BoundedNbt {
    private static final int MAX_DEPTH = 8, MAX_NODES = 256, MAX_TEXT_BYTES = 2048;
    private int nodes, textBytes;
    private BoundedNbt() {}
    public static void check(Tag root) { new BoundedNbt().visit(root, 0); }
    private void text(String value) {
        if (value.length() > MAX_TEXT_BYTES) throw new IllegalArgumentException("Component metadata text too large");
        textBytes += value.getBytes(StandardCharsets.UTF_8).length;
        if (textBytes > MAX_TEXT_BYTES) throw new IllegalArgumentException("Component metadata text budget exceeded");
    }
    private void visit(Tag tag, int depth) {
        if (depth > MAX_DEPTH || ++nodes > MAX_NODES) throw new IllegalArgumentException("Component metadata nesting/node limit exceeded");
        if (tag instanceof CompoundTag compound) {
            if (compound.size() > MAX_NODES - nodes) throw new IllegalArgumentException("Component metadata too wide");
            for (String key : compound.getAllKeys()) { text(key); visit(compound.get(key), depth + 1); }
        } else if (tag instanceof CollectionTag<?> collection) {
            if (collection.size() > MAX_NODES - nodes) throw new IllegalArgumentException("Component metadata collection too large");
            for (Tag child : collection) visit(child, depth + 1);
        } else if (tag instanceof StringTag string) text(string.getAsString());
        else if (!(tag instanceof NumericTag)) throw new IllegalArgumentException("Unsupported component metadata type");
    }
}
