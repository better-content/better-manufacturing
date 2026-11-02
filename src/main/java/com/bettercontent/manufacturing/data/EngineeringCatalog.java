package com.bettercontent.manufacturing.data;

import com.bettercontent.manufacturing.api.MaterialPolicy;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import java.util.*;

/** Stage reload results; publish only at successful server startup/global datapack synchronization. */
public final class EngineeringCatalog {
    public record Snapshot(long generation, boolean ready, Map<String, MaterialPolicy> policies) {
        public Snapshot { policies = Map.copyOf(policies); }
    }
    public static final class Changed extends Event {
        public final boolean client;
        public Changed(boolean client) { this.client = client; }
    }
    private static volatile Snapshot server = new Snapshot(0, false, Map.of());
    private static volatile Snapshot client = new Snapshot(0, false, Map.of());
    private static volatile Map<String, MaterialPolicy> staged;
    private EngineeringCatalog() {}
    public static Snapshot view(boolean clientSide) { return clientSide ? client : server; }
    public static boolean clientThread() { return net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient(); }
    public static void register() {
        CatalogNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(EngineeringCatalog::addReload);
        MinecraftForge.EVENT_BUS.addListener(EngineeringCatalog::sync);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> publish());
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            staged = null; server = new Snapshot(0, false, Map.of());
            MinecraftForge.EVENT_BUS.post(new Changed(false));
        });
    }
    private static void addReload(AddReloadListenerEvent event) { event.addListener(new Loader()); }
    private static void sync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) publish(); // Player joins cannot publish a failed reload's staged candidate.
        if (!server.ready()) throw new IllegalStateException("Engineering catalog has not completed server startup");
        var packet = new CatalogNetwork.SnapshotPacket(server.generation(), EngineeringPolicyJson.encode(server.policies()));
        if (event.getPlayer() == null) event.getPlayerList().getPlayers().forEach(player -> CatalogNetwork.send(player, packet));
        else CatalogNetwork.send(event.getPlayer(), packet);
    }
    private static void publish() {
        var candidate = staged;
        if (candidate == null) return;
        server = new Snapshot(Math.addExact(server.generation(), 1), true, candidate); staged = null;
        MinecraftForge.EVENT_BUS.post(new Changed(false));
    }
    public static void receive(long generation, String encoded) {
        if (generation < 1) throw new IllegalArgumentException("Invalid engineering generation");
        var decoded = EngineeringPolicyJson.decode(encoded);
        if (client.ready() && generation <= client.generation()) {
            if (generation == client.generation() && client.policies().equals(decoded)) return;
            throw new IllegalArgumentException("Stale/conflicting engineering snapshot");
        }
        client = new Snapshot(generation, true, decoded);
        MinecraftForge.EVENT_BUS.post(new Changed(true));
    }
    public static void disconnectClient() {
        client = new Snapshot(0, false, Map.of()); MinecraftForge.EVENT_BUS.post(new Changed(true));
    }
    private static final class Loader extends SimplePreparableReloadListener<Map<String, MaterialPolicy>> {
        @Override protected Map<String, MaterialPolicy> prepare(ResourceManager manager, ProfilerFiller profiler) {
            // Vanilla's JSON listener logs and skips syntactically broken files. That would silently
            // remove an authored restriction, so syntax, duplicate keys and size must fail the reload.
            var resources = manager.listResources("engineering/materials", id -> id.getPath().endsWith(".json"));
            if (resources.size() > EngineeringPolicyJson.MAX_POLICIES) throw new IllegalArgumentException("Too many engineering files");
            var result = new TreeMap<String, MaterialPolicy>();
            resources.forEach((id, resource) -> {
                try (var input = resource.open()) {
                    byte[] bytes = input.readNBytes(EngineeringPolicyJson.MAX_BYTES + 1);
                    if (bytes.length > EngineeringPolicyJson.MAX_BYTES) throw new IllegalArgumentException("Engineering file too large: " + id);
                    String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                    var policies = EngineeringPolicyJson.decode("[" + text + "]");
                    if (policies.size() != 1) throw new IllegalArgumentException("Invalid engineering document: " + id);
                    var policy = policies.values().iterator().next();
                    if (result.putIfAbsent(policy.material(), policy) != null) throw new IllegalArgumentException("Duplicate engineering material: " + policy.material());
                } catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot read engineering file: " + id, failure); }
            });
            EngineeringPolicyJson.encode(result);
            return Map.copyOf(result);
        }
        @Override protected void apply(Map<String, MaterialPolicy> result, ResourceManager manager, ProfilerFiller profiler) {
            staged = result;
        }
    }
}
