package com.bettercontent.manufacturing.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.List;
import java.util.Set;

/** Gate native-family hooks by their owning mod; the vanilla placement bridge remains available without Create. */
public final class CreateMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String name = mixin.substring(mixin.lastIndexOf('.') + 1);
        // The shared vanilla BlockItem bridge guards each optional family at the callsite.
        if (name.equals("BlockItemBuildMixin")) return true;
        String owner;
        if (name.startsWith("Sifter")) owner = "createsifter";
        else if (name.startsWith("Pneu") || name.startsWith("Pneumatictools")) owner = "pneumaticcraft";
        else if (name.startsWith("Compressed")) owner = "compressedcreativity";
        else if (name.startsWith("Diesel")) owner = "createdieselgenerators";
        else if (name.startsWith("Powergrid")) owner = "powergrid";
        else if (name.startsWith("Bees")) owner = "complicated_bees";
        else if (name.startsWith("Prettypipes")) owner = "prettypipes";
        else if (name.startsWith("Sophisticatedstorage")) owner = "sophisticatedstorage";
        else if (name.startsWith("LittleLogistics")) owner = "littlelogistics";
        else if (name.startsWith("Trackwork")) owner = "trackwork";
        else if (name.startsWith("Clockwork")) owner = "vs_clockwork";
        else if (name.startsWith("Eureka")) owner = "vs_eureka";
        else if (name.startsWith("PrecisionQuarryInscriber")) owner = "ae2";
        else if (name.startsWith("PrecisionQuarryRat")) owner = "rats";
        else if (name.startsWith("PrecisionQuarrySmasher")) owner = "alexscaves";
        else owner = "create";
        var mods = FMLLoader.getLoadingModList().getMods();
        var info = mods.stream().filter(mod -> mod.getModId().equals(owner)).findFirst();
        if (info.isEmpty()) return false;
        if (owner.equals("create") || owner.equals("createsifter") || mixin.contains(".createfluids.")) {
            var create = mods.stream().filter(mod -> mod.getModId().equals("create")).findFirst();
            if (create.isEmpty()) return false;
            if (!Set.of("6.0.8", "6.0.8-289").contains(create.get().getVersion().toString()))
                throw new IllegalStateException("Manufacturing Create adapter expects pinned 6.0.8; got " + create.get().getVersion());
        }
        if (owner.equals("createsifter") && !info.get().getVersion().toString().equals("1.20.1-1.8.6-6.0.6"))
            throw new IllegalStateException("Manufacturing expects pinned Create Sifting 1.20.1-1.8.6-6.0.6");
        if (owner.equals("sophisticatedstorage") && mods.stream().noneMatch(mod -> mod.getModId().equals("sophisticatedcore"))) return false;
        return true;
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
