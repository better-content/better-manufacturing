package com.bettercontent.manufacturing.integration.compressed;

import net.minecraft.world.item.ItemStack;

/** Runtime helpers must remain outside Mixin's reserved transformation namespace. */
public final class CompressedBlowerRates {
    private CompressedBlowerRates() {}
    public interface MeshAccess {
        ItemStack manufacturing$getInstalledMesh();
        void manufacturing$setInstalledMesh(ItemStack stack);
    }
    public static double rate(Object block) {
        var variant = CompressedVariant.get(block);
        double body = variant == null ? 1 : variant.property("airflow");
        if (!(block instanceof MeshAccess mesh)) return body;
        ItemStack stack = mesh.manufacturing$getInstalledMesh();
        if (stack.isEmpty()) return body;
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("compressedcreativity")) return body;
        var settings = com.bettercontent.manufacturing.persistence.SettingsNbt.read(stack.getTag(), id.toString());
        if (settings == null || !settings.properties().keySet().equals(java.util.Set.of("airflow"))) return body;
        double screen = settings.properties().get("airflow");
        return Math.min(body, Double.isFinite(screen) && screen >= .75 && screen <= 1.5 ? screen : 1);
    }
}
