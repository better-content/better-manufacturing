package com.bettercontent.manufacturing.integration.pneutransfer;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;
import java.util.Set;

/** Bounded native head-speed proxy changes fitting throughput; storage and fluid acceptance remain native. */
public final class PneuTransferSettings {
    public static final String FUNCTION = "better_manufacturing:pneumaticcraft/fluid_transfer";
    private static final Set<String> OUTPUTS = Set.of("pneumaticcraft:gas_lift", "pneumaticcraft:small_tank",
            "pneumaticcraft:medium_tank", "pneumaticcraft:large_tank", "pneumaticcraft:huge_tank",
            "pneumaticcraft:liquid_hopper", "pneumaticcraft:omnidirectional_hopper");
    private PneuTransferSettings() {}

    public static boolean supports(String output) { return OUTPUTS.contains(output); }

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !supports(output) || !inputs.keySet().equals(Set.of("wetted_fitting")))
            throw new IllegalArgumentException("Unsupported pneumatic transfer recipe");
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material data not loaded");
        ItemStack stack = inputs.get("wetted_fitting");
        if (stack == null || !(stack.getItem() instanceof ToolPartItem part)
                || !"tconstruct:large_plate".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Expected large plate fitting");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Invalid plate material");
        // Approximate flow response from the consumed plate's native working
        // speed, not a material-specific hydraulic table or fluid whitelist.
        double rate = NativePartRates.relative(part, stack, Measure.HEAD_MINING_SPEED);
        return new MachineSettings(output, Map.of("transfer_rate", rate),
                Map.of("wetted_fitting", material.toString()), null);
    }

    /** Reject wrong-property/extra-property saved tags rather than promoting them to throughput. */
    public static boolean valid(MachineSettings settings, String output) {
        if (settings == null || !supports(output) || !output.equals(settings.output())
                || !settings.properties().keySet().equals(Set.of("transfer_rate"))) return false;
        double rate = settings.properties().get("transfer_rate");
        return Double.isFinite(rate) && rate >= .75 && rate <= 1.5;
    }

    /** Only validated, already-baked settings are consulted at runtime; absence means stock throughput. */
    public static double rate(Object entity) {
        if (!(entity instanceof PneuTransferSettingsHolder holder)) return 1;
        var settings = holder.betterManufacturing$transferSettings();
        return settings != null && valid(settings, settings.output()) ? settings.properties().get("transfer_rate") : 1;
    }

    public static int scale(int amount, Object entity) {
        return Math.max(1, (int) Math.round(amount * rate(entity)));
    }

    /** Integer-native timers/divisors round toward the baseline to keep effective throughput bounded. */
    public static int interval(int baseline, Object entity) {
        double rating = rate(entity);
        double value = baseline / rating;
        return Math.max(1, (int) (rating < 1 ? Math.floor(value) : Math.ceil(value)));
    }
}
