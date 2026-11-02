package com.bettercontent.manufacturing.integration.sifter;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;

public final class SifterManufacturing {
    private static final Map<KineticBlockEntity, Boolean> LOADED = Collections.synchronizedMap(new WeakHashMap<>());
    private SifterManufacturing() {}
    public static void track(KineticBlockEntity block) { LOADED.put(block, true); }
    public static void materialsChanged() {
        KineticBlockEntity[] blocks;
        synchronized (LOADED) { blocks = LOADED.keySet().toArray(KineticBlockEntity[]::new); }
        for (var block : blocks) {
            var level = block.getLevel();
            if (level != null && level.getServer() != null) level.getServer().execute(() -> {
                if (!block.isRemoved() && !block.isChunkUnloaded() && level.getBlockEntity(block.getBlockPos()) == block)
                    ((ScreenAccess) block).manufacturing$refreshScreen();
            });
        }
    }
}
