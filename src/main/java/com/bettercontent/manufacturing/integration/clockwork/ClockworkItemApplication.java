package com.bettercontent.manufacturing.integration.clockwork;

import com.bettercontent.manufacturing.api.MachineSettings;
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
    public static List<ItemStack> inherit(Recipe<?> recipe, ItemStack processed, ItemStack held,
                                           List<ItemStack> nativeResults) {
        String path = recipe.getId().toString();
        if (path.equals("vs_clockwork:item_application/gas_engine")
            && itemId(processed).equals("create:fluid_tank") && itemId(held).equals("vs_clockwork:duct")) {
            var duct = SettingsNbt.read(held.getTag(), "vs_clockwork:duct");
            if (duct != null && duct.properties().keySet().equals(Set.of("gas_transfer"))
                && duct.materials().keySet().equals(Set.of("plate", "handle"))
                && valid(duct.properties().get("gas_transfer")))
                bake(nativeResults, "vs_clockwork:gas_engine", "gas_transfer",
                    duct.properties().get("gas_transfer"), duct.materials());
        } else if (path.equals("vs_clockwork:item_application/reactionwheel")
            && itemId(processed).equals("create:flywheel") && itemId(held).equals("create:railway_casing")) {
            // A working flywheel carries only its final response and material labels;
            // native item application consumes it, with no qualification marker or part provenance.
            var prep = SettingsNbt.read(processed.getTag(), "create:flywheel");
            if (prep != null && prep.harvestTier() == null
                && prep.properties().keySet().equals(Set.of("actuator_response"))
                && prep.materials().keySet().equals(Set.of("blade", "handle"))
                && prep.materials().values().stream().allMatch(s -> !s.isBlank())
                && valid(prep.properties().get("actuator_response")))
                bake(nativeResults, "vs_clockwork:reactionwheel", "actuator_response",
                    prep.properties().get("actuator_response"), prep.materials());
        }
        // Unconfigured flywheel and duct retain their exact stock item-application result and cost.
        return nativeResults;
    }
    private static boolean valid(Double value) {
        return value != null && Double.isFinite(value) && value >= .75 && value <= 1.5;
    }
    private static String itemId(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
    private static void bake(List<ItemStack> results, String target, String property,
                             double rate, Map<String, String> materials) {
        for (var result : results) {
            if (target.equals(itemId(result)))
                SettingsNbt.write(result.getOrCreateTag(), new MachineSettings(target,
                    Map.of(property, rate), materials, null));
        }
    }
}
