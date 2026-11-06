package com.bettercontent.manufacturing.tconstruct;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.api.NumericEvaluator;
import com.bettercontent.manufacturing.api.NumericInputs;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Captures the numerical TCon values actually used by an accepted recipe, never live runtime material data. */
public final class NativePartCapture {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private NativePartCapture() {}

    private static final class Frame {
        final Map<ItemStack, java.util.List<String>> roles = new IdentityHashMap<>();
        final Map<String, Map<NativePartRates.Measure, NumericInputs.Stat>> parts = new LinkedHashMap<>();
        final Map<String, Map<String, Double>> auxiliary = new LinkedHashMap<>();
        Frame(Map<String, ItemStack> inputs) {
            inputs.forEach((role, stack) -> {
                if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Missing part role: " + role);
                roles.computeIfAbsent(stack, ignored -> new java.util.ArrayList<>()).add(role);
            });
        }
    }

    /** Also used by native-stat readers whose formula differs from NativePartRates. */
    public static void record(ItemStack stack, NativePartRates.Measure measure, double selected, double reference) {
        Frame frame = CURRENT.get();
        if (frame == null) return;
        var roles = frame.roles.get(stack);
        if (roles == null || !(stack.getItem() instanceof ToolPartItem))
            throw new IllegalArgumentException("Unbound native part in numeric recipe");
        var pair = new NumericInputs.Stat(selected, reference);
        for (String role : roles) {
            var previous = frame.parts.computeIfAbsent(role, ignored -> new HashMap<>()).putIfAbsent(measure, pair);
            if (previous != null && !previous.equals(pair)) throw new IllegalArgumentException("Conflicting native part stat");
        }
    }

    public static void recordAuxiliary(String role, String property, double value) {
        Frame frame = CURRENT.get();
        if (frame != null) frame.auxiliary.computeIfAbsent(role, ignored -> new LinkedHashMap<>()).put(property, value);
    }

    public static MachineSettings bake(String function, Map<String, ItemStack> inputs, Supplier<MachineSettings> recipe) {
        if (CURRENT.get() != null) throw new IllegalArgumentException("Nested numeric part baking");
        var frame = new Frame(inputs);
        MachineSettings result;
        CURRENT.set(frame);
        try { result = recipe.get(); }
        finally { CURRENT.remove(); }
        var children = new LinkedHashMap<String, Map<String, Double>>();
        inputs.forEach((role, stack) -> {
            if (stack.getItem() instanceof ToolPartItem) {
                if (!frame.parts.containsKey(role)) throw new IllegalArgumentException("Unmeasured working part: " + role);
            } else {
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null) throw new IllegalArgumentException("Unknown configured component");
                var settings = SettingsNbt.read(stack.getTag(), id.toString());
                if (stack.hasTag() && stack.getTag().contains(SettingsNbt.KEY) && settings == null)
                    throw new IllegalArgumentException("Malformed configured component");
                children.put(role, settings == null || settings.properties().isEmpty()
                    ? Map.of("baseline", 1.0) : settings.properties());
            }
        });
        frame.auxiliary.forEach((role, values) -> {
            if (children.putIfAbsent(role, values) != null || frame.parts.containsKey(role))
                throw new IllegalArgumentException("Duplicate saved auxiliary role");
        });
        var numeric = new NumericInputs(function, frame.parts, children);
        var evaluated = NumericEvaluator.evaluate(result.output(), numeric, result.harvestTier());
        if (!result.properties().keySet().equals(evaluated.keySet()))
            throw new IllegalArgumentException("Numeric formula does not match baked properties");
        evaluated.forEach((key, value) -> {
            if (!Double.isFinite(value) || Math.abs(value - result.properties().get(key)) > 1e-9)
                throw new IllegalArgumentException("Numeric formula differs from native recipe: " + key);
        });
        return new MachineSettings(result.output(), evaluated, result.materials(), result.harvestTier(), numeric);
    }
}
