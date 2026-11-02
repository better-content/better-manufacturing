package com.bettercontent.manufacturing.integration.sifter;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Craft-time frame rating. The installed Sifter stores only the resulting settings, never its parts. */
public final class SifterFrameSettings {
    public static final String FUNCTION = "better_manufacturing:sifter/machine";
    private static final Set<String> OUTPUTS = Set.of("createsifter:sifter", "createsifter:brass_sifter");
    private SifterFrameSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!FUNCTION.equals(function) || !OUTPUTS.contains(output)) throw new IllegalArgumentException("Unsupported Sifter frame");
        boolean brass = output.equals("createsifter:brass_sifter");
        if (!inputs.keySet().equals(brass ? Set.of("left", "right", "core") : Set.of("left", "right"))
                || !MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Wrong Sifter frame parts");
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        var iron = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || iron == null || iron.traits().getOrDefault("screen_rate", 0.0) <= 0)
            throw new IllegalArgumentException("Missing screen reference");
        var labels = new LinkedHashMap<String, String>();
        double rate = 0;
        for (String role : new String[]{"left", "right"}) {
            ItemStack stack = inputs.get(role);
            var partId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (!(stack.getItem() instanceof ToolPartItem part) || partId == null || !partId.toString().equals("tconstruct:tool_handle"))
                throw new IllegalArgumentException("Wrong Sifter frame shape");
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Invalid Sifter frame material");
            var policy = catalog.policies().get(material.toString());
            if (policy == null) policy = catalog.policies().get(material.getId().toString());
            if (policy == null || !policy.supportedParts().contains("tconstruct:tool_handle"))
                throw new IllegalArgumentException("Unrated Sifter frame material");
            double rating = policy.traits().getOrDefault("screen_rate", 0.0);
            if (!Double.isFinite(rating) || rating <= 0) throw new IllegalArgumentException("Missing screen rating");
            rate += bounded(rating / iron.traits().get("screen_rate"));
            labels.put(role, material.toString());
        }
        if (brass) {
            ItemStack core = inputs.get("core");
            if (core == null || core.isEmpty()) throw new IllegalArgumentException("Brass Sifter requires a Sifter");
            var coreId = ForgeRegistries.ITEMS.getKey(core.getItem());
            if (coreId == null || !coreId.toString().equals("createsifter:sifter"))
                throw new IllegalArgumentException("Brass Sifter requires a Sifter");
            var previous = SettingsNbt.read(core.getTag(), coreId.toString());
            // Only an actually unconfigured native Sifter contributes 1x. A
            // present but malformed/wrong-function record cannot be laundered
            // into a new brass frame or supply an airflow/pressure work rate.
            if (core.hasTag() && core.getTag().contains(SettingsNbt.KEY)
                && (previous == null || !previous.properties().keySet().equals(Set.of("work_rate"))))
                throw new IllegalArgumentException("Invalid configured Sifter predecessor");
            rate += previous == null ? 1 : previous.rate(); // Native/unconfigured predecessor is the baseline.
            if (previous != null) previous.materials().forEach((role, material) -> labels.put("core_" + role, material));
            rate /= 3;
        } else rate /= 2;
        return new MachineSettings(output, Map.of("work_rate", bounded(rate)), labels, null);
    }

    /** Two native parts of the same Sifter share one total work/stress envelope, not multiplied bonuses. */
    public static double combined(double frameRate, double installedMeshRate, boolean hasMesh) {
        return hasMesh ? bounded((bounded(frameRate) + bounded(installedMeshRate)) / 2) : bounded(frameRate);
    }
    private static double bounded(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid screen rate");
        return Math.max(.75, Math.min(1.5, value));
    }
}
