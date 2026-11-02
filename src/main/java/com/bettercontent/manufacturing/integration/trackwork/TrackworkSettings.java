package com.bettercontent.manufacturing.integration.trackwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;
import java.util.Map;

/** Hub ratings are fixed when crafted. Wheel radius, suspension travel and track geometry stay native. */
public final class TrackworkSettings {
    public static final String FUNCTION = "better_manufacturing:trackwork/running_gear";
    private TrackworkSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !TrackworkVariant.isOutput(output) || !inputs.keySet().equals(java.util.Set.of("hub_suspension")))
            throw new IllegalArgumentException("Unknown running gear or hub role");
        ItemStack stack = inputs.get("hub_suspension");
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Missing hub part");
        String partId = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (!(stack.getItem() instanceof ToolPartItem part) ||
            !(partId.equals("tconstruct:tough_handle") || partId.equals("tconstruct:bow_limb")))
            throw new IllegalArgumentException("Running gear requires a tough handle or bow limb");
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material stats not loaded");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Invalid hub material");
        var materials = MaterialRegistry.getInstance();
        MaterialId iron = new MaterialId("tconstruct:iron");
        double load, response;
        if (partId.equals("tconstruct:bow_limb")) {
            var selected = materials.<LimbMaterialStats>getMaterialStats(material.getId(), LimbMaterialStats.ID).orElseThrow(() -> new IllegalArgumentException("No limb stats"));
            var reference = materials.<LimbMaterialStats>getMaterialStats(iron, LimbMaterialStats.ID).orElseThrow(() -> new IllegalArgumentException("No iron limb reference"));
            load = Math.sqrt((double) selected.durability() / reference.durability());
            response = (1 + selected.drawSpeed()) / (1 + reference.drawSpeed());
        } else {
            var selected = materials.<HandleMaterialStats>getMaterialStats(material.getId(), HandleMaterialStats.ID).orElseThrow(() -> new IllegalArgumentException("No handle stats"));
            var reference = materials.<HandleMaterialStats>getMaterialStats(iron, HandleMaterialStats.ID).orElseThrow(() -> new IllegalArgumentException("No iron handle reference"));
            load = (1 + selected.durability()) / (1 + reference.durability());
            response = (1 + selected.meleeSpeed()) / (1 + reference.meleeSpeed());
        }
        return new MachineSettings(output, Map.of("mechanical_load", bounded(load), "suspension_response", bounded(response)),
            Map.of("hub_suspension", material.toString()), null);
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid hub stats");
        return Math.max(.75, Math.min(1.5, value));
    }

    public static boolean valid(MachineSettings settings, String output) {
        if (settings == null || !settings.output().equals(output) || !TrackworkVariant.isOutput(output)
                || settings.harvestTier() != null
                || !settings.properties().keySet().equals(java.util.Set.of("mechanical_load", "suspension_response"))
                || !settings.materials().keySet().equals(java.util.Set.of("hub_suspension"))) return false;
        for (double value : settings.properties().values())
            if (!Double.isFinite(value) || value < .75 || value > 1.5) return false;
        return true;
    }

    public static double property(Object entity, String name) {
        if (!(entity instanceof TrackworkVariant variant)) return 1;
        var settings = variant.manufacturing$settings();
        return valid(settings, variant.manufacturing$outputId())
                ? settings.properties().getOrDefault(name, 1.0) : 1;
    }

    public static void install(Object entity, ItemStack stack) {
        if (!(entity instanceof TrackworkVariant variant)) return;
        MachineSettings settings = SettingsNbt.read(stack.getTag(), variant.manufacturing$outputId());
        variant.manufacturing$setSettings(valid(settings, variant.manufacturing$outputId()) ? settings : null);
        if (entity instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity nativeEntity) nativeEntity.sendData();
        if (entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic
            && kinetic.getLevel() != null && !kinetic.getLevel().isClientSide && kinetic.hasNetwork())
            kinetic.getOrCreateNetwork().updateStressFor(kinetic, kinetic.calculateStressApplied());
    }
}
