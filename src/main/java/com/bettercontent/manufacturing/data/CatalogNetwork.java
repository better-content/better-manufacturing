package com.bettercontent.manufacturing.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

final class CatalogNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(new ResourceLocation("better_manufacturing", "engineering"))
        .networkProtocolVersion(() -> "1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    record SnapshotPacket(long generation, String encoded) {}
    private CatalogNetwork() {}
    static void register() {
        CHANNEL.messageBuilder(SnapshotPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((packet, buffer) -> { buffer.writeVarLong(packet.generation()); buffer.writeUtf(packet.encoded(), EngineeringPolicyJson.MAX_BYTES); })
            .decoder(buffer -> new SnapshotPacket(buffer.readVarLong(), buffer.readUtf(EngineeringPolicyJson.MAX_BYTES)))
            .consumerMainThread((packet, context) -> {
                if (context.get().getNetworkManager().isConnected()) EngineeringCatalog.receive(packet.generation(), packet.encoded());
            }).add();
    }
    static void send(ServerPlayer player, SnapshotPacket packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }
}
