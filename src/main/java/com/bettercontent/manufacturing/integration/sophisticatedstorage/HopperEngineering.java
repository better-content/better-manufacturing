package com.bettercontent.manufacturing.integration.sophisticatedstorage;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
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
        // Native handle mining speed sets the saved cooldown ratio; the installed
        // upgrade still owns its filters, directions, stack size and tier.
        double ratio = NativePartRates.relative(part, handle, NativePartRates.Measure.HANDLE_MINING_SPEED);
        return new MachineSettings(output, Map.of("transfer_rate", ratio), Map.of("actuator", material.toString()), null);
    }

    /** Native upgrade tier is the baseline; only a bounded saved handle work rate changes cadence. */
    public static long ratedInterval(long nativeTicks, double rate) {
        if (nativeTicks <= 0 || !Double.isFinite(rate) || rate < .75 || rate > 1.5) return nativeTicks;
        return Math.max(1, Math.round(nativeTicks / rate));
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
        return ratedInterval(nativeTicks, rate);
    }
}
