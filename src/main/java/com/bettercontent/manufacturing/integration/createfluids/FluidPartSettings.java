package com.bettercontent.manufacturing.integration.createfluids;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.data.EngineeringCatalog;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;
import java.util.Set;

/** Calculate the fluid hardware's bounded native capacity/throughput from a consumed plate.
 * Explicit fluid-capacity and flow ratings are not chemical fluid whitelists. */
public final class FluidPartSettings {
    private static final Set<String> ENVELOPE = Set.of("create:basin", "create:fluid_pipe", "create:fluid_tank",
        "create:fluid_valve", "create:item_drain", "create:spout");
    private static final Set<String> TRANSFER = Set.of("create:hose_pulley", "create:portable_fluid_interface");
    private FluidPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String role = switch (function) {
            case "better_manufacturing:create/fluid_envelope" -> {
                if (!ENVELOPE.contains(output)) throw new IllegalArgumentException("Not a fluid envelope");
                yield "liner";
            }
            case "better_manufacturing:create/fluid_transfer" -> {
                if (!TRANSFER.contains(output)) throw new IllegalArgumentException("Not fluid transfer hardware");
                yield "wetted_fitting";
            }
            default -> throw new IllegalArgumentException("Not a fluid hardware function");
        };
        if (!MaterialRegistry.isFullyLoaded() || inputs.size() != 1 || !inputs.containsKey(role))
            throw new IllegalArgumentException("Missing fluid part");
        ItemStack stack = inputs.get(role);
        if (!(stack.getItem() instanceof ToolPartItem plate) || !"tconstruct:large_plate".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Not a large plate");
        var material = plate.getMaterial(stack);
        if (!plate.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported plate material");
        String property = output.equals("create:basin") || output.equals("create:fluid_tank") ? "fluid_capacity" : "fluid_rate";
        String trait = property.equals("fluid_capacity") ? "fluid_capacity" : "fluid_transfer";
        var catalog = EngineeringCatalog.view(EngineeringCatalog.clientThread());
        var policy = catalog.policies().get(material.toString());
        if (policy == null) policy = catalog.policies().get(material.getId().toString());
        var reference = catalog.policies().get("tconstruct:iron");
        if (!catalog.ready() || policy == null || reference == null
            || !policy.supportedParts().contains("tconstruct:large_plate"))
            throw new IllegalArgumentException("Unsupported fluid fitting material");
        double value = policy.traits().getOrDefault(trait, 0d);
        double base = reference.traits().getOrDefault(trait, 0d);
        if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(base) || base <= 0)
            throw new IllegalArgumentException("Missing authored fluid rating");
        double rating = Math.max(.75, Math.min(1.5, value / base));
        // Basin recipes and tank/boiler fluid must never be lost by reducing native capacity.
        if (property.equals("fluid_capacity")) rating = Math.max(1, rating);
        // Passive pipes cannot amplify pressure; only the powered pump (separate handle recipe) can do so.
        if (output.equals("create:fluid_pipe")) rating = Math.min(1, rating);
        return new MachineSettings(output, Map.of(property, rating), Map.of(role, material.toString()), null);
    }
}
