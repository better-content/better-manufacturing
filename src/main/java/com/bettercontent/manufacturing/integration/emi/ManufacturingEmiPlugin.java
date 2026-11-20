package com.bettercontent.manufacturing.integration.emi;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.recipe.PartVariantRecipe;
import dev.emi.emi.api.*;
import dev.emi.emi.api.stack.Comparison;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import java.util.List;

/** Keep one native recipe page per machine; crafted settings distinguish actual instances. */
@EmiEntrypoint
public final class ManufacturingEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        // A generic TCon part ingredient matches every physical material in the
        // native crafting grid. EMI's material/NBT-aware inventory comparison
        // otherwise marks real iron (and other) parts missing and disables its
        // transfer button. Compare the *part item* here; the actual stack and
        // its material are still consumed and rated by PartVariantRecipe.
        var partItemOnly = Comparison.of((left, right) -> true);
        // TCon/JEI register strict part-NBT comparisons after this plugin. Apply
        // the ordinary native ingredient's item-only matching after all plugins
        // have registered, not during this plugin's early register() callback.
        registry.addDeferredRecipes(consumer -> {
            for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
                if (item instanceof ToolPartItem) registry.setDefaultComparison(item, partItemOnly);
            }
        });
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
            // The result stays generic; the actual transferred parts determine its rating.
        }
    }
}
