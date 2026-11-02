package com.bettercontent.manufacturing.data;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "better_manufacturing", value = Dist.CLIENT)
public final class ClientCatalogLifecycle {
    private ClientCatalogLifecycle() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { EngineeringCatalog.disconnectClient(); }
    @SubscribeEvent public static void changed(EngineeringCatalog.Changed event) {
        if (event.client && EngineeringCatalog.view(true).ready() && net.minecraftforge.fml.ModList.get().isLoaded("emi"))
            dev.emi.emi.runtime.EmiReloadManager.reload();
    }
}
