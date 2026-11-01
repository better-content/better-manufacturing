package com.bettercontent.manufacturing;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ManufacturingMod.ID)
public final class ManufacturingMod {
    public static final String ID = "better_manufacturing";
    public ManufacturingMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ManufacturingContent.ITEMS.register(bus);
        com.bettercontent.manufacturing.recipe.ManufacturingRecipes.SERIALIZERS.register(bus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ManufacturingTooltips::append);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((slimeknights.tconstruct.library.events.MaterialsLoadedEvent event) -> {
            com.bettercontent.manufacturing.integration.create.DrillEngineering.invalidate();
            if (net.minecraftforge.fml.ModList.get().isLoaded("create")) com.bettercontent.manufacturing.integration.create.CreateManufacturing.materialsChanged();
        });
        if (net.minecraftforge.fml.ModList.get().isLoaded("create"))
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(com.simibubi.create.content.kinetics.drill.DrillBlockEntity.class,
                com.bettercontent.manufacturing.integration.create.CreateManufacturing::attach);
    }
}
