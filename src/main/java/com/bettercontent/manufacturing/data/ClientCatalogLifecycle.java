package com.bettercontent.manufacturing.data;

import net.minecraftforge.api.distmarker.Dist;
import slimeknights.tconstruct.library.events.MaterialsLoadedEvent;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "better_manufacturing", value = Dist.CLIENT)
public final class ClientCatalogLifecycle {
    private ClientCatalogLifecycle() {}
    // Recipe previews depend on the native Tinkers material registry, not the retired
    // engineering-policy packet. The pinned TCon registry posts this Forge-bus event
    // after its material/stat synchronization completes on the client.
    @SubscribeEvent public static void materialsLoaded(MaterialsLoadedEvent event) {
        if (net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient()
                && MaterialRegistry.isFullyLoaded() && net.minecraftforge.fml.ModList.get().isLoaded("emi"))
            net.minecraft.client.Minecraft.getInstance().execute(dev.emi.emi.runtime.EmiReloadManager::reload);
    }
}
