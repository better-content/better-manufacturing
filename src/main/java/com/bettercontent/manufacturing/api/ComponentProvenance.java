package com.bettercontent.manufacturing.api;

import java.util.Map;

/** Quantities use 90 quanta per Tinkers material unit; not chemical or hazard mass. */
public record ComponentProvenance(String item, String materialVariant, String statRole,
                                  long materialQuanta, boolean wholePart, Map<String, String> retainedData) {
    public static final long QUANTA_PER_UNIT = 90;
    public static final long MAX_QUANTA = 90L * 64 * 64;
    public static final int MAX_METADATA_BYTES = 2048;
    public ComponentProvenance {
        Identifiers.id(item);
        Identifiers.variant(materialVariant);
        Identifiers.id(statRole);
        if (materialQuanta <= 0 || materialQuanta > MAX_QUANTA) throw new IllegalArgumentException("Invalid attributable quantity");
        retainedData = Map.copyOf(retainedData);
        if (retainedData.size() > 16) throw new IllegalArgumentException("Too many retained metadata fields");
        int bytes = 0;
        for (var entry : retainedData.entrySet()) {
            Identifiers.id(entry.getKey());
            bytes += entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            bytes += entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        if (bytes > MAX_METADATA_BYTES) throw new IllegalArgumentException("Retained metadata too large");
        if (!wholePart && !retainedData.isEmpty()) throw new IllegalArgumentException("Cannot split stateful parts into material credit");
    }
    /** Called only for explicit recipe batch allocation, never inferred from output stack size. */
    public ComponentProvenance divide(int outputs) {
        if (outputs < 1 || outputs > 64 || materialQuanta % outputs != 0) throw new IllegalArgumentException("Batch must divide exactly");
        if (outputs == 1) return this;
        return new ComponentProvenance(item, materialVariant, statRole, materialQuanta / outputs, false, retainedData);
    }
}
