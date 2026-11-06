package com.bettercontent.manufacturing.integration.createbulk;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Native tool-part stats are sampled at craft; installed machines derive responses from saved inputs. */
public final class BulkPartSettings {
    public static final String WHEEL = "better_manufacturing:createbulk/crushing_wheel";
    public static final String LAUNCHER = "better_manufacturing:createbulk/weighted_ejector";
    public static final String STEAM = "better_manufacturing:createbulk/steam_engine";
    private BulkPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        var labels = new LinkedHashMap<String, String>();
        if (WHEEL.equals(function) && "create:crushing_wheel".equals(output) && inputs.keySet().equals(Set.of("hammer", "plate"))) {
            // Head mining speed determines ore work; plate head durability bears
            // the reaction load. The weaker part limits pair work, while each
            // wheel pays at least that work in native kinetic stress.
            double hammer = rating(inputs.get("hammer"), "tconstruct:hammer_head", Measure.HEAD_MINING_SPEED, labels, "hammer");
            double plate = rating(inputs.get("plate"), "tconstruct:large_plate", Measure.HEAD_DURABILITY, labels, "plate");
            var budget = wheelBudget(hammer, plate);
            return new MachineSettings(output, Map.of("work_rate", budget.workRate(), "load", budget.load()), labels, null);
        }
        if (LAUNCHER.equals(function) && "create:weighted_ejector".equals(output) && inputs.keySet().equals(Set.of("spring"))) {
            // Native bow-limb draw speed recharges the ejector; native arrow
            // velocity bounds its throw. Target physics remain Create-owned.
            double recharge = rating(inputs.get("spring"), "tconstruct:bow_limb", Measure.LIMB_DRAW_SPEED, labels, "spring");
            double envelope = rating(inputs.get("spring"), "tconstruct:bow_limb", Measure.LIMB_VELOCITY, labels, "spring");
            return new MachineSettings(output, Map.of("recharge_rate", recharge, "launch_envelope", envelope), labels, null);
        }
        if (STEAM.equals(function) && "create:steam_engine".equals(output) && inputs.keySet().equals(Set.of("piston_rod"))) {
            // Handle movement speed converts supplied steam; its native
            // durability limits transmitted mechanical stress, not boiler heat.
            double conversion = rating(inputs.get("piston_rod"), "tconstruct:tough_handle", Measure.HANDLE_MINING_SPEED, labels, "piston_rod");
            double limit = rating(inputs.get("piston_rod"), "tconstruct:tough_handle", Measure.HANDLE_DURABILITY, labels, "piston_rod");
            return new MachineSettings(output, Map.of("conversion", conversion, "mechanical_limit", limit), labels, null);
        }
        throw new IllegalArgumentException("Unsupported Create bulk variant: " + function + "/" + output);
    }

    /** Native pair work can never outrun the per-wheel paid stress multiplier. */
    public record WheelBudget(double workRate, double load) {}
    public static WheelBudget wheelBudget(double hammer, double plate) {
        if (!Double.isFinite(hammer) || !Double.isFinite(plate)
            || hammer < .75 || hammer > 1.5 || plate < .75 || plate > 1.5)
            throw new IllegalArgumentException("Invalid native wheel part rates");
        return new WheelBudget(Math.min(hammer, plate), Math.max(hammer, plate));
    }

    private static double rating(ItemStack stack, String partId, Measure measure,
                                 Map<String, String> labels, String role) {
        if (stack == null || stack.isEmpty() || !partId.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())))
                || !(stack.getItem() instanceof ToolPartItem part)) throw new IllegalArgumentException("Wrong " + role + " part");
        double rate = NativePartRates.relative(part, stack, measure);
        labels.put(role, part.getMaterial(stack).toString());
        return rate;
    }
}
