package com.bettercontent.manufacturing.integration.sifter;

import com.bettercontent.manufacturing.api.MachineBuild;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Native installed mesh carries its fixed settings, not recoverable frame parts. */
public final class MeshEngineering {
    public static final String FUNCTION = "better_manufacturing:sifter/frame";
    public static final List<String> ROLES = List.of("north", "east", "south", "west");
    public static final Set<String> MESHES = Set.of("createsifter:string_mesh", "createsifter:andesite_mesh", "createsifter:zinc_mesh", "createsifter:brass_mesh", "createsifter:advanced_brass_mesh");
    public record State(double rate, boolean configured) {
        public State(double rate) { this(rate, false); }
        public boolean allowed() { return true; }
        public String reason() { return ""; }
        public boolean wetCompatible() { return true; }
        public MachineBuild build() { return null; }
    }
    private MeshEngineering() {}
    public static State evaluate(ItemStack mesh, boolean client) {
        var id = ForgeRegistries.ITEMS.getKey(mesh.getItem());
        if (mesh.isEmpty() || id == null || !MESHES.contains(id.toString())) return new State(1);
        var settings = SettingsNbt.read(mesh.getTag(), id.toString());
        // Only a mesh's baked screen work rate may change native Sifter timing.
        // A valid v1 record carrying another family's airflow/pressure must not
        // turn an installed mesh into a rated screen or increase its paid stress.
        if (settings == null || !settings.properties().keySet().equals(Set.of("work_rate"))) return new State(1);
        return new State(settings.rate(), true);
    }
    public static List<Component> describe(State state) {
        return List.of(Component.translatable("better_manufacturing.variant.rate", String.format(Locale.ROOT, "%.2f", state.rate())));
    }
    public static void invalidate() {}
    public static long epoch() { return 0; }
}
