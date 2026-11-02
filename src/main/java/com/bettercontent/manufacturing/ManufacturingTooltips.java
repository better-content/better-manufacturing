package com.bettercontent.manufacturing;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class ManufacturingTooltips {
    private ManufacturingTooltips() {}
    public static void append(ItemTooltipEvent event) {
        var stack = event.getItemStack(); var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || id.toString().equals("create:mechanical_harvester")) return; // Construction-only; historical tags confer no native work.
        var settings = SettingsNbt.read(stack.getTag(), id.toString());
        if (settings == null) return;
        // Do not describe a valid-v1 but wrong-family tag as a native rating when its consumer uses baseline.
        var required = switch (id.toString()) {
            case "create:encased_fan", "create:propeller" -> java.util.Set.of("airflow");
            case "create:mechanical_pump" -> java.util.Set.of("pressure");
            case "create:crushing_wheel" -> java.util.Set.of("work_rate", "load");
            case "create:weighted_ejector" -> java.util.Set.of("recharge_rate", "launch_envelope");
            case "create:mechanical_drill", "create:mechanical_saw", "create:mechanical_press", "create:millstone",
                 "create:whisk", "create:mechanical_mixer", "create:mechanical_plough", "create:mechanical_roller",
                 "createsifter:sifter", "createsifter:brass_sifter" -> java.util.Set.of("work_rate");
            default -> java.util.Set.<String>of();
        };
        if (!required.isEmpty() && !settings.properties().keySet().equals(required)) return;
        if (required.stream().anyMatch(key -> key.equals("load") || key.equals("recharge_rate") || key.equals("launch_envelope"))
            && required.stream().anyMatch(key -> {
                double value = settings.properties().get(key);
                return value < .75 || value > 1.5;
            })) return;
        // The record already contains final baked values. Never look up live TCon material
        // policies in a tooltip: reloads affect future crafts, not this saved item.
        settings.materials().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
            event.getToolTip().add(Component.translatable("better_manufacturing.variant.material",
                entry.getKey(), entry.getValue()).withStyle(ChatFormatting.GRAY)));
        settings.properties().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            String property = entry.getKey();
            String value = String.format(java.util.Locale.ROOT, "%.2f", entry.getValue());
            // A loose component has no stress cost of its own. This is its saved rating,
            // not a claim about a future kinetic network or a live material reevaluation.
            if (property.equals("work_rate") || property.equals("airflow")
                || property.equals("pressure") && id.toString().equals("create:mechanical_pump")) {
                String key = property.equals("airflow") ? "better_manufacturing.variant.airflow"
                    : property.equals("pressure") && id.toString().equals("create:mechanical_pump")
                        ? "better_manufacturing.variant.pressure" : "better_manufacturing.variant.rate";
                if (property.equals("work_rate") && (id.toString().equals("create:mechanical_plough")
                    || id.toString().equals("create:mechanical_roller")))
                    key = id.getPath().equals("mechanical_plough") ? "better_manufacturing.variant.debris"
                        : "better_manufacturing.variant.excavation";
                event.getToolTip().add(Component.translatable(key, value).withStyle(ChatFormatting.GRAY));
            } else {
                event.getToolTip().add(Component.translatable("better_manufacturing.variant.property",
                    property.replace('_', ' '), value).withStyle(ChatFormatting.GRAY));
            }
        });
        if (settings.harvestTier() != null && (id.toString().equals("create:mechanical_drill")
            || id.toString().equals("create:mechanical_saw")))
            event.getToolTip().add(Component.translatable("better_manufacturing.variant.tier", settings.harvestTier())
                .withStyle(ChatFormatting.GRAY));
    }
}
