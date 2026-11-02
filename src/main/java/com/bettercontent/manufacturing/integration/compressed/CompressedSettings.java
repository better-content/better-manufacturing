package com.bettercontent.manufacturing.integration.compressed;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.*;

/** Compressed Creativity's separate airflow, pressure and conversion roles are baked at crafting. */
public final class CompressedSettings {
    private CompressedSettings() {}
    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!output.startsWith("compressedcreativity:") || !MaterialRegistry.isFullyLoaded())
            throw new IllegalArgumentException("Unavailable Compressed Creativity parts");
        String property = switch (function) {
            case "better_manufacturing:compressedcreativity/blower", "better_manufacturing:compressedcreativity/mesh" -> "airflow";
            case "better_manufacturing:compressedcreativity/conversion" -> "conversion";
            case "better_manufacturing:compressedcreativity/pressure_tube" -> "pressure_envelope";
            default -> throw new IllegalArgumentException("Unknown compressed function");
        };
        Set<String> required = switch (function) {
            case "better_manufacturing:compressedcreativity/blower" -> Set.of("blade", "handle");
            case "better_manufacturing:compressedcreativity/mesh" -> output.equals("compressedcreativity:mesh_empty") ? Set.of("blade", "handle") : Set.of("frame", "frame2");
            case "better_manufacturing:compressedcreativity/conversion" -> output.equals("compressedcreativity:engine_rotor") ? Set.of("plate", "handle") : output.equals("compressedcreativity:compressed_air_engine") ? Set.of("plate", "handle", "rotor") : Set.of("plate", "handle");
            default -> Set.of("plate");
        };
        if (!inputs.keySet().equals(required)) throw new IllegalArgumentException("Wrong compressed working assembly");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        if (!catalog.ready()) throw new IllegalArgumentException("Missing engineering material ratings");
        var iron = catalog.policies().get("tconstruct:iron");
        if (iron == null) throw new IllegalArgumentException("Missing reference rating");
        var labels = new LinkedHashMap<String, String>();
        double rate = 1.5;
        for (var input : inputs.entrySet()) {
            String role = input.getKey(); ItemStack stack = input.getValue();
            var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) throw new IllegalArgumentException("Unknown component");
            String id = key.toString();
            if (role.equals("frame") || role.equals("frame2") || role.equals("rotor")) {
                String expected = role.startsWith("frame") ? "compressedcreativity:mesh_empty" : "compressedcreativity:engine_rotor";
                if (!id.equals(expected)) throw new IllegalArgumentException("Wrong preassembled component");
                var existing = SettingsNbt.read(stack.getTag(), expected);
                // Legacy/unconfigured components are valid native assemblies at reference performance.
                if (existing == null || !existing.properties().keySet().equals(Set.of(property))) {
                    rate = Math.min(rate, 1); labels.put(role, expected); continue;
                }
                double inherited = existing.properties().get(property);
                if (!Double.isFinite(inherited) || inherited < .75 || inherited > 1.5) throw new IllegalArgumentException("Invalid inherited setting");
                rate = Math.min(rate, inherited);
                existing.materials().forEach((r, material) -> labels.put(role + "_" + r, material));
                continue;
            }
            String requiredPart = switch (role) {
                case "blade" -> "tconstruct:broad_blade";
                case "handle" -> property.equals("conversion") ? "tconstruct:tough_handle" : "tconstruct:tool_handle";
                case "plate" -> "tconstruct:large_plate";
                default -> throw new IllegalArgumentException("Unknown part role");
            };
            if (!id.equals(requiredPart) || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Wrong part shape");
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Incompatible material");
            String materialId = material.toString(); labels.put(role, materialId);
            var policy = catalog.policies().getOrDefault(materialId, catalog.policies().get(material.getId().toString()));
            if (policy == null || !policy.supportedParts().contains(requiredPart)) throw new IllegalArgumentException("Missing part rating");
            String rating = switch (role) {
                case "blade", "handle" -> property.equals("conversion") ? "conversion_rate" : "blower_rate";
                case "plate" -> property.equals("pressure_envelope") ? "pressure_envelope" : "conversion_rate";
                default -> throw new IllegalArgumentException("Unknown rating");
            };
            double value = policy.traits().getOrDefault(rating, 0.0);
            double base = iron.traits().getOrDefault(rating, 0.0);
            if (!Double.isFinite(value) || !Double.isFinite(base) || value <= 0 || base <= 0) throw new IllegalArgumentException("Unrated part");
            rate = Math.min(rate, Math.max(.75, Math.min(1.5, value / base)));
        }
        // Pressure safety must never raise the native pressure tier, only condition its safe envelope.
        if (property.equals("pressure_envelope")) rate = Math.min(1, rate);
        return new MachineSettings(output, Map.of(property, rate), labels, null);
    }
}
