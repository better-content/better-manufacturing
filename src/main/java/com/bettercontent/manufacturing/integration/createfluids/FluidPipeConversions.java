package com.bettercontent.manufacturing.integration.createfluids;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Preserve the original part-built pipe settings when Create changes only its shell/shape. */
public final class FluidPipeConversions {
    private FluidPipeConversions() {}
    public static boolean swap(Level world, BlockPos pos, BlockState state, int flags) {
        var previous = FluidVariantBehaviour.get(world.getBlockEntity(pos));
        var tag = new CompoundTag();
        if (previous != null) previous.writeSafe(tag);
        boolean changed = world.setBlock(pos, state, flags);
        if (changed && tag.contains(SettingsNbt.KEY)) {
            var nextBE = world.getBlockEntity(pos);
            if (nextBE instanceof SmartBlockEntity smart && FluidVariantBehaviour.supports(smart)) {
                var next = FluidVariantBehaviour.get(smart);
                if (next == null) {
                    next = new FluidVariantBehaviour(smart);
                    smart.attachBehaviourLate(next);
                }
                next.install(tag);
            }
        }
        return changed;
    }
}
