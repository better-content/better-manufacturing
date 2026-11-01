package com.bettercontent.manufacturing.api;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Bounded per-owner cache. Conditions such as live pressure/heat must be checked outside it. */
public final class ProfileCache<V> {
    private record Key(MachineBuild build, String profile) {}
    private final int capacity;
    private final LinkedHashMap<Key, V> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long version = -1;
    public ProfileCache(int capacity) {
        if (capacity < 1 || capacity > 16384) throw new IllegalArgumentException("Invalid cache capacity");
        this.capacity = capacity;
    }
    public synchronized V resolve(MachineBuild build, String profile, long dataVersion, Supplier<V> loader) {
        Identifiers.id(profile);
        if (dataVersion < 0) throw new IllegalArgumentException("Negative data version");
        if (dataVersion != version) { entries.clear(); version = dataVersion; }
        Key key = new Key(build, profile);
        V present = entries.get(key);
        if (present != null) return present;
        V result = java.util.Objects.requireNonNull(loader.get());
        entries.put(key, result);
        if (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
        return result;
    }
    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }
}
