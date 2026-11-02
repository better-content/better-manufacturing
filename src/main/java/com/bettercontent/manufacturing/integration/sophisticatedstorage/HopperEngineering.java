package com.bettercontent.manufacturing.integration.sophisticatedstorage;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/** Calculate a hopper actuator once at crafting; installed upgrades use only their own saved record. */
public final class HopperEngineering {
    public static final String FUNCTION = "better_manufacturing:sophisticatedstorage/hopper";
    private HopperEngineering() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !(output.equals("sophisticatedstorage:hopper_upgrade")
                || output.equals("sophisticatedstorage:advanced_hopper_upgrade")) || !inputs.keySet().equals(java.util.Set.of("actuator")))
            throw new IllegalArgumentException("Unsupported hopper variant");
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material data not loaded");
        var handle = inputs.get("actuator");
        if (handle == null || handle.isEmpty() || !(handle.getItem() instanceof ToolPartItem part)
                || !"tconstruct:tool_handle".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(handle.getItem()))))
            throw new IllegalArgumentException("Hopper requires a tool handle");
        var material = part.getMaterial(handle);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported handle material");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        var materialId = material.toString();
        var policy = catalog.policies().get(materialId);
        if (policy == null) policy = catalog.policies().get(material.getId().toString());
        var iron = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || policy == null || iron == null
                || !policy.supportedParts().contains("tconstruct:tool_handle"))
            throw new IllegalArgumentException("No hopper handle rating");
        double rating = policy.traits().getOrDefault("transfer_rate", 0.0);
        double reference = iron.traits().getOrDefault("transfer_rate", 0.0);
        if (!Double.isFinite(rating) || rating <= 0 || !Double.isFinite(reference) || reference <= 0)
            throw new IllegalArgumentException("No hopper transfer rating");
        double ratio = Math.max(.75, Math.min(1.5, rating / reference));
        return new MachineSettings(output, Map.of("transfer_rate", ratio), Map.of("actuator", materialId), null);
    }

    /** Native upgrade item retains its own tier/configured interval and transfer stack/filter capabilities. */
    public static long interval(ItemStack installedUpgrade, long nativeTicks) {
        if (installedUpgrade == null || installedUpgrade.isEmpty() || nativeTicks <= 0) return nativeTicks;
        var id = ForgeRegistries.ITEMS.getKey(installedUpgrade.getItem());
        if (id == null || !(id.toString().equals("sophisticatedstorage:hopper_upgrade")
                || id.toString().equals("sophisticatedstorage:advanced_hopper_upgrade"))) return nativeTicks;
        var settings = SettingsNbt.read(installedUpgrade.getTag(), id.toString());
        if (settings == null) return nativeTicks;
        double rate = settings.properties().getOrDefault("transfer_rate", 1.0);
        if (!Double.isFinite(rate) || rate < .75 || rate > 1.5) return nativeTicks;
        return Math.max(1, Math.round(nativeTicks / rate));
    }
}
