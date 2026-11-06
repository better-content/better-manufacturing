package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only inherit pre-baked settings from actual consumed native Create item-application inputs. */
public final class ClockworkItemApplication {
    private ClockworkItemApplication() {}
    /** Reject only the two native conversions whose working input must already contain paid parts. */
    public static boolean canApply(Recipe<?> recipe, ItemStack processed, ItemStack held) {
        if (recipe == null) return true;
        String path = recipe.getId().toString();
        if (path.equals("vs_clockwork:item_application/gas_engine"))
            return itemId(processed).equals("create:fluid_tank")
                && itemId(held).equals("vs_clockwork:duct") && validDuct(held);
        if (path.equals("vs_clockwork:item_application/reactionwheel"))
            return itemId(processed).equals("create:flywheel")
                && itemId(held).equals("create:railway_casing") && validFlywheel(processed);
        return true;
    }
    private static boolean validDuct(ItemStack stack) {
        var settings = SettingsNbt.read(stack.getTag(), "vs_clockwork:duct");
        return settings != null && settings.harvestTier() == null
            && settings.properties().keySet().equals(Set.of("gas_transfer"))
            && settings.materials().keySet().equals(Set.of("plate", "handle"))
            && settings.materials().values().stream().allMatch(s -> !s.isBlank())
            && settings.numericInputs() != null
            && settings.numericInputs().function().equals("better_manufacturing:clockwork/gas_thermal")
            && settings.numericInputs().parts().keySet().equals(Set.of("plate", "handle"))
            && settings.numericInputs().components().isEmpty()
            && valid(settings.properties().get("gas_transfer"));
    }
    private static boolean validFlywheel(ItemStack stack) {
        var settings = SettingsNbt.read(stack.getTag(), "create:flywheel");
        return settings != null && settings.harvestTier() == null
            && settings.properties().keySet().equals(Set.of("actuator_response"))
            && settings.materials().keySet().equals(Set.of("blade", "handle"))
            && settings.materials().values().stream().allMatch(s -> !s.isBlank())
            && settings.numericInputs() != null
            && settings.numericInputs().function().equals("better_manufacturing:clockwork/reactionwheel_prep")
            && settings.numericInputs().parts().keySet().equals(Set.of("blade", "handle"))
            && settings.numericInputs().components().keySet().equals(Set.of("blank"))
            && valid(settings.properties().get("actuator_response"));
    }
    public static List<ItemStack> inherit(Recipe<?> recipe, ItemStack processed, ItemStack held,
                                           List<ItemStack> nativeResults) {
        String path = recipe.getId().toString();
        if (path.equals("vs_clockwork:item_application/gas_engine")
            && itemId(processed).equals("create:fluid_tank") && itemId(held).equals("vs_clockwork:duct")) {
            var duct = SettingsNbt.read(held.getTag(), "vs_clockwork:duct");
            if (validDuct(held))
                bake(nativeResults, "vs_clockwork:gas_engine", "gas_transfer",
                    "better_manufacturing:clockwork/item_application_gas_engine", "duct", duct);
        } else if (path.equals("vs_clockwork:item_application/reactionwheel")
            && itemId(processed).equals("create:flywheel") && itemId(held).equals("create:railway_casing")) {
            // A working flywheel carries only its final response and material labels;
            // native item application consumes it, with no qualification marker or part provenance.
            var prep = SettingsNbt.read(processed.getTag(), "create:flywheel");
            if (validFlywheel(processed))
                bake(nativeResults, "vs_clockwork:reactionwheel", "actuator_response",
                    "better_manufacturing:clockwork/item_application_reactionwheel", "flywheel", prep);
        }
        // The pre-consumption recipe filter rejects unconfigured inputs before native work begins.
        return nativeResults;
    }
    private static boolean valid(Double value) {
        return value != null && Double.isFinite(value) && value >= .75 && value <= 1.5;
    }
    private static String itemId(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
    private static void bake(List<ItemStack> results, String target, String property,
                             String function, String role, MachineSettings source) {
        var numeric = new NumericInputs(function, Map.of(), Map.of(role, Map.of(property, source.properties().get(property))));
        var derived = NumericEvaluator.evaluate(target, numeric, null);
        for (var result : results) {
            if (target.equals(itemId(result)))
                SettingsNbt.write(result.getOrCreateTag(), new MachineSettings(target,
                    derived, source.materials(), null, numeric));
        }
    }
}
