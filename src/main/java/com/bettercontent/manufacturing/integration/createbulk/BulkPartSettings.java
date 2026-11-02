package com.bettercontent.manufacturing.integration.createbulk;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Role-specific, crafting-only ratings. Installed machines never consult material policies. */
public final class BulkPartSettings {
    public static final String WHEEL = "better_manufacturing:createbulk/crushing_wheel";
    public static final String LAUNCHER = "better_manufacturing:createbulk/weighted_ejector";
    public static final String STEAM = "better_manufacturing:createbulk/steam_engine";
    private BulkPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Part materials not loaded");
        var policies = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        if (!policies.ready()) throw new IllegalArgumentException("Engineering ratings not loaded");
        var labels = new LinkedHashMap<String, String>();
        if (WHEEL.equals(function) && "create:crushing_wheel".equals(output) && inputs.keySet().equals(Set.of("hammer", "plate"))) {
            double hammer = rating(inputs.get("hammer"), "tconstruct:hammer_head", "hammer_crushing", policies, labels, "hammer");
            double plate = rating(inputs.get("plate"), "tconstruct:large_plate", "plate_crushing", policies, labels, "plate");
            double rate = clamp(Math.min(hammer, plate));
            // Stress is paid by EACH wheel, while the controller's progress is rated by the pair.
            return new MachineSettings(output, Map.of("work_rate", rate, "load", clamp(Math.max(hammer, plate))), labels, null);
        }
        if (LAUNCHER.equals(function) && "create:weighted_ejector".equals(output) && inputs.keySet().equals(Set.of("spring"))) {
            double recharge = rating(inputs.get("spring"), "tconstruct:bow_limb", "spring_recharge", policies, labels, "spring");
            double envelope = rating(inputs.get("spring"), "tconstruct:bow_limb", "spring_envelope", policies, labels, "spring");
            return new MachineSettings(output, Map.of("recharge_rate", recharge, "launch_envelope", envelope), labels, null);
        }
        if (STEAM.equals(function) && "create:steam_engine".equals(output) && inputs.keySet().equals(Set.of("piston_rod"))) {
            double conversion = rating(inputs.get("piston_rod"), "tconstruct:tough_handle", "steam_conversion", policies, labels, "piston_rod");
            double limit = rating(inputs.get("piston_rod"), "tconstruct:tough_handle", "steam_limit", policies, labels, "piston_rod");
            return new MachineSettings(output, Map.of("conversion", conversion, "mechanical_limit", limit), labels, null);
        }
        throw new IllegalArgumentException("Unsupported Create bulk variant: " + function + "/" + output);
    }

    private static double rating(ItemStack stack, String partId, String trait,
                                 EngineeringCatalog.Snapshot catalog, Map<String, String> labels, String role) {
        if (stack == null || stack.isEmpty() || !partId.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())))
                || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Wrong " + role + " part");
        var material = part.getMaterial(stack);
        if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported " + role + " material");
        var policy = catalog.policies().get(material.toString());
        if (policy == null) policy = catalog.policies().get(material.getId().toString());
        var reference = catalog.policies().get("tconstruct:iron");
        if (policy == null || reference == null || !policy.supportedParts().contains(partId)) throw new IllegalArgumentException("Unrated " + role + " material");
        double value = policy.traits().getOrDefault(trait, 0.0), iron = reference.traits().getOrDefault(trait, 0.0);
        if (!(value > 0 && iron > 0) || !Double.isFinite(value) || !Double.isFinite(iron)) throw new IllegalArgumentException("No " + trait + " rating");
        labels.put(role, material.toString());
        return clamp(value / iron);
    }
    private static double clamp(double ratio) {
        if (!Double.isFinite(ratio)) throw new IllegalArgumentException("Invalid part rating");
        return Math.max(.75, Math.min(1.5, ratio));
    }
}
