package com.bettercontent.manufacturing.integration.bees;

import com.accbdd.complicated_bees.block.entity.gyrofuge.AbstractGyrofugeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional native owner path: an already assembled unit must see its saved rating after item placement. */
final class BeesGyrofugeRefresh {
    private BeesGyrofugeRefresh() {}

    static void afterInstall(BlockEntity blockEntity) {
        if (!(blockEntity instanceof AbstractGyrofugeBlockEntity unit)) return;
        var logic = unit.getLogic();
        if (logic != null) logic.getController().ifPresent(controller -> controller.setLogic(logic));
    }
}
