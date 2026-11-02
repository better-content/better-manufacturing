package com.bettercontent.manufacturing.integration.createfluids;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.Map;
import java.util.Set;

/** Approximate bounded fluid-hardware response from the consumed plate's native TCon tool stats. */
public final class FluidPartSettings {
    private static final Set<String> ENVELOPE = Set.of("create:basin", "create:fluid_pipe", "create:fluid_tank",
        "create:fluid_valve", "create:item_drain", "create:spout");
    private static final Set<String> TRANSFER = Set.of("create:hose_pulley", "create:portable_fluid_interface");
    private FluidPartSettings() {}

    /** Physical bounds after the actual native plate stat is read at craft time. */
    public static double boundedFluidRating(String output, String property, double rating) {
        if (!Double.isFinite(rating) || rating < .75 || rating > 1.5)
            throw new IllegalArgumentException("Invalid native plate rate");
        if (property.equals("fluid_capacity")) return Math.max(1.0, rating);
        if (property.equals("fluid_rate") && output.equals("create:fluid_pipe")) return Math.min(1.0, rating);
        if (!property.equals("fluid_rate")) throw new IllegalArgumentException("Unknown fluid property");
        return rating;
    }

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
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem plate)
            || !"tconstruct:large_plate".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Not a large plate");
        var material = plate.getMaterial(stack);
        if (!plate.canUseMaterial(material.getId())) throw new IllegalArgumentException("Unsupported plate material");
        String property = output.equals("create:basin") || output.equals("create:fluid_tank") ? "fluid_capacity" : "fluid_rate";
        // Tool head durability approximates vessel structural capacity; mining
        // speed approximates working fluid transfer. Neither is a literal fluid
        // affinity, conductivity or pressure qualification. Existing native amount
        // and source budgets still govern every executed transfer.
        double rating = NativePartRates.relative(plate, stack, property.equals("fluid_capacity")
            ? NativePartRates.Measure.HEAD_DURABILITY : NativePartRates.Measure.HEAD_MINING_SPEED);
        // Filled vessels keep their contents; passive pipes never amplify pressure.
        // The powered pump owns its energy and pressure independently of this part.
        return new MachineSettings(output, Map.of(property, boundedFluidRating(output, property, rating)),
            Map.of(role, material.toString()), null);
    }
}
