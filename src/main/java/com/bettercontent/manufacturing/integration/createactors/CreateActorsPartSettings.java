package com.bettercontent.manufacturing.integration.createactors;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Map;

/** Role-specific material ratings evaluated once when the actor is crafted. */
public final class CreateActorsPartSettings {
    private CreateActorsPartSettings() {}

    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        String required = switch (output) {
            case "create:mechanical_plough" -> "tconstruct:small_axe_head";
            case "create:mechanical_roller" -> "tconstruct:large_plate";
            default -> throw new IllegalArgumentException("Unsupported Create actor");
        };
        String requiredFunction = output.equals("create:mechanical_roller")
            ? "better_manufacturing:create/rolling" : "better_manufacturing:create/field_work";
        if (!requiredFunction.equals(function) || !inputs.keySet().equals(java.util.Set.of("working_surface")))
            throw new IllegalArgumentException("Invalid Create actor parts");
        ItemStack stack = inputs.get("working_surface");
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ToolPartItem part)
            || !required.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))))
            throw new IllegalArgumentException("Wrong actor working surface");
        // Both real native operations are block breaking. The edge and plate
        // therefore share their native head mining-speed stat, iron-normalized;
        // instant tilling/paving remain unmodified construction operations.
        double rate = NativePartRates.relative(part, stack, Measure.HEAD_MINING_SPEED);
        return new MachineSettings(output, Map.of("work_rate", rate),
            Map.of("working_surface", part.getMaterial(stack).toString()), null);
    }
}
