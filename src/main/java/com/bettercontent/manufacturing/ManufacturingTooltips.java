package com.bettercontent.manufacturing;

import com.bettercontent.manufacturing.integration.create.DrillEngineering;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class ManufacturingTooltips {
    private ManufacturingTooltips() {}
    public static void append(ItemTooltipEvent event) {
        var stack = event.getItemStack(); var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.toString().equals(DrillEngineering.CHASSIS)) return;
        var state = DrillEngineering.evaluate(stack.getTag()); var lines = event.getToolTip();
        if (!state.allowed()) lines.add(Component.translatable("better_manufacturing.drill.blocked", state.blockedReason()).withStyle(ChatFormatting.RED));
        else if (!state.build().hasProvenance()) lines.add(Component.translatable("better_manufacturing.drill.stock").withStyle(ChatFormatting.GRAY));
        else {
            lines.add(Component.translatable("better_manufacturing.drill.head", state.build().components().get("head").materialVariant()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("better_manufacturing.drill.rate", String.format(java.util.Locale.ROOT, "%.2f", state.rate())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("better_manufacturing.drill.native_tier").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("better_manufacturing.drill.retool").withStyle(ChatFormatting.GRAY));
        }
    }
}
