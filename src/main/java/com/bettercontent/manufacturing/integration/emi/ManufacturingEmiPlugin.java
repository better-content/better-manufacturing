package com.bettercontent.manufacturing.integration.emi;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import dev.emi.emi.api.*;
import dev.emi.emi.api.stack.Comparison;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Keep one native recipe page per machine; crafted settings distinguish actual instances. */
@EmiEntrypoint
public final class ManufacturingEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        registry.addRecipeDecorator((recipe, widgets) -> {
            if (!(recipe.getBackingRecipe() instanceof PartVariantRecipe)) return;
            // The original result is intentionally generic; the assembled item's tooltip
            // reports the numbers of the actual consumed parts, including mixed materials.
            widgets.addText(Component.translatable("better_manufacturing.emi.generic"), 87, 43, 0x777777, false);
            widgets.addTooltipText(List.of(Component.translatable("better_manufacturing.emi.generic_explanation")),
                87, 42, 31, 12);
        });
        for (var raw : registry.getRecipeManager().getRecipes()) {
            if (!(raw instanceof PartVariantRecipe recipe)) continue;
            var item = recipe.getResultItem(RegistryAccess.EMPTY).getItem();
            var output = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (output == null) continue;
            String outputId = output.toString();
            registry.setDefaultComparison(item, Comparison.compareData(stack -> SettingsNbt.read(stack.getNbt(), outputId)));
            // EMI already displays the genuine recipe and a generic result. Do not replace it
            // with one misleading all-roles-same-material page per TCon material or index
            // fabricated output stacks. The crafting grid supplies the actual part numbers.
        }
    }
}
