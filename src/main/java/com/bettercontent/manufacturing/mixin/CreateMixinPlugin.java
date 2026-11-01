package com.bettercontent.manufacturing.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.List;
import java.util.Set;

/** All current hooks belong to the optional pinned Create adapter, including its placement bridge. */
public final class CreateMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        var info = FMLLoader.getLoadingModList().getMods().stream().filter(mod -> mod.getModId().equals("create")).findFirst();
        if (info.isEmpty()) return false;
        if (!Set.of("6.0.8", "6.0.8-289").contains(info.get().getVersion().toString()))
            throw new IllegalStateException("Manufacturing Create adapter expects pinned 6.0.8 / same-source 6.0.8-289 fixture; got " + info.get().getVersion());
        return true;
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
