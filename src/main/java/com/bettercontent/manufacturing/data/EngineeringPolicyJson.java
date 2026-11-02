package com.bettercontent.manufacturing.data;

import com.bettercontent.manufacturing.api.MaterialPolicy;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict, bounded codec shared by datapacks and server-authoritative client snapshots. */
public final class EngineeringPolicyJson {
    public static final int MAX_POLICIES = 1024, MAX_BYTES = 262144;
    private static final Set<String> FIELDS = Set.of("schema", "material", "supported_parts", "native_stats", "traits", "compatible_processes");
    private EngineeringPolicyJson() {}
    public static MaterialPolicy read(JsonObject json) {
        if (!json.keySet().equals(FIELDS) || !json.get("schema").isJsonPrimitive() || !json.getAsJsonPrimitive("schema").isNumber()
                || !json.get("schema").getAsBigDecimal().equals(java.math.BigDecimal.ONE)) throw new IllegalArgumentException("Unsupported engineering policy schema/fields");
        String mode = string(json.get("native_stats"));
        if (!Set.of("base_material", "none").contains(mode)) throw new IllegalArgumentException("Unknown native stat binding");
        if (!json.get("traits").isJsonObject() || json.getAsJsonObject("traits").size() > 64) throw new IllegalArgumentException("Invalid trait map");
        var traits = new TreeMap<String, Double>();
        json.getAsJsonObject("traits").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Trait is not numerical");
            traits.put(entry.getKey(), entry.getValue().getAsDouble());
        });
        return new MaterialPolicy(string(json.get("material")), strings(json.get("supported_parts"), 32), mode.equals("base_material"), traits,
            strings(json.get("compatible_processes"), 64));
    }
    public static JsonObject write(MaterialPolicy policy) {
        var result = new JsonObject(); result.addProperty("schema", 1); result.addProperty("material", policy.material());
        result.addProperty("native_stats", policy.useNativeStats() ? "base_material" : "none");
        result.add("supported_parts", strings(policy.supportedParts())); result.add("compatible_processes", strings(policy.compatibleProcesses()));
        var traits = new JsonObject(); new TreeMap<>(policy.traits()).forEach(traits::addProperty); result.add("traits", traits);
        return result;
    }
    public static String encode(Map<String, MaterialPolicy> policies) {
        if (policies.size() > MAX_POLICIES) throw new IllegalArgumentException("Too many engineering policies");
        var array = new JsonArray();
        new TreeMap<>(policies).forEach((id, policy) -> {
            if (!id.equals(policy.material())) throw new IllegalArgumentException("Policy identity mismatch");
            array.add(write(policy));
        });
        String encoded = array.toString(); checkSize(encoded); return encoded;
    }
    public static Map<String, MaterialPolicy> decode(String encoded) {
        checkSize(encoded);
        // Reject nested input before the JSON parser recurses. Authored documents have depth <= 3.
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 8) throw new IllegalArgumentException("Nested engineering payload"); }
            else if (c == '}' || c == ']') depth--;
        }
        final JsonElement element;
        try (var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(encoded))) {
            reader.setLenient(false);
            element = readStrict(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing engineering data");
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("Malformed engineering JSON", failure); }
        if (!element.isJsonArray() || element.getAsJsonArray().size() > MAX_POLICIES) throw new IllegalArgumentException("Invalid engineering snapshot");
        var result = new LinkedHashMap<String, MaterialPolicy>();
        for (var value : element.getAsJsonArray()) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid engineering document");
            var policy = read(value.getAsJsonObject());
            if (result.putIfAbsent(policy.material(), policy) != null) throw new IllegalArgumentException("Duplicate engineering material");
        }
        return Map.copyOf(result);
    }
    private static JsonElement readStrict(com.google.gson.stream.JsonReader reader) throws java.io.IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); var object = new JsonObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IllegalArgumentException("Duplicate engineering field: " + key);
                    object.add(key, readStrict(reader));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); var array = new JsonArray();
                while (reader.hasNext()) array.add(readStrict(reader));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Malformed engineering JSON");
        };
    }
    private static void checkSize(String text) {
        if (text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Engineering snapshot too large");
    }
    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    private static Set<String> strings(JsonElement value, int limit) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > limit) throw new IllegalArgumentException("Invalid engineering set");
        var result = new HashSet<String>();
        for (var element : value.getAsJsonArray()) if (!result.add(string(element))) throw new IllegalArgumentException("Duplicate engineering set entry");
        return Set.copyOf(result);
    }
    private static JsonArray strings(Set<String> values) {
        var result = new JsonArray(); values.stream().sorted().forEach(result::add); return result;
    }
}
