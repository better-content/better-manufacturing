package com.bettercontent.manufacturing;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ManufacturingMod.ID)
public final class ManufacturingMod {
    public static final String ID = "better_manufacturing";
    public ManufacturingMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ManufacturingContent.ITEMS.register(bus);
        com.bettercontent.manufacturing.integration.plastics.PolymerFluids.register(bus);
        com.bettercontent.manufacturing.recipe.ManufacturingRecipes.SERIALIZERS.register(bus);
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation(ID, "pneu_empty_tank"),
            com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferEmptyTankIngredient.SERIALIZER);
        if (net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft")) {
            net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation(ID, "pneu_native_cost_bundle"),
                com.bettercontent.manufacturing.integration.pneumatictools.PneuNativeCostBundleIngredient.SERIALIZER);
            net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation(ID, "pneu_prepared_drill_bit"),
                com.bettercontent.manufacturing.integration.pneumatictools.PneuPreparedDrillBitIngredient.SERIALIZER);
            net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation(ID, "pneu_prepared_assembly"),
                com.bettercontent.manufacturing.integration.pneumatictools.PneuPreparedAssemblyIngredient.SERIALIZER);
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedstorage") || net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedbackpacks")) {
            net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation(ID, "sophisticated_cost_bundle"),
                com.bettercontent.manufacturing.integration.sophisticatedstorage.SophisticatedCostBundleIngredient.SERIALIZER);
        }
        var loaded = net.minecraftforge.fml.ModList.get();
        if (loaded.isLoaded("create")) com.bettercontent.manufacturing.integration.createactors.CreateActorsBlocks.register(bus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ManufacturingTooltips::append);
        if (loaded.isLoaded("create")) {
            com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour.register();
            com.bettercontent.manufacturing.integration.createbulk.SteamVariantBehaviour.register();
            com.bettercontent.manufacturing.integration.createfluids.FluidVariantBehaviour.register();
            if (loaded.isLoaded("createsifter")) com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour.registerSifter();
        }
        if (loaded.isLoaded("create") && loaded.isLoaded("createdieselgenerators"))
            bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(com.bettercontent.manufacturing.integration.diesel.DieselSchematic::register));
        if (loaded.isLoaded("compressedcreativity")) com.bettercontent.manufacturing.integration.compressed.CompressedVariant.register();
        if (loaded.isLoaded("better_industrial_heat")) com.bettercontent.manufacturing.integration.industrialheat.IndustrialHeatBridge.register();
        if (loaded.isLoaded("vs_clockwork")) com.bettercontent.manufacturing.integration.clockwork.ClockworkPlacement.register();
        if (loaded.isLoaded("create"))
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(com.simibubi.create.content.kinetics.drill.DrillBlockEntity.class,
                com.bettercontent.manufacturing.integration.create.CreateManufacturing::attach);
    }
}
