package com.bettercontent.manufacturing.integration.createactors;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores only baked settings on the stationary actor; Create copies this NBT into movement contexts. */
public final class CreateActorsPloughBlockEntity extends BlockEntity {
    private Tag settings;

    public CreateActorsPloughBlockEntity(BlockPos pos, BlockState state) {
        super(CreateActorsBlocks.PLOUGH.get(), pos, state);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        settings = tag.contains(SettingsNbt.KEY) ? tag.get(SettingsNbt.KEY).copy() : null;
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (settings != null) tag.put(SettingsNbt.KEY, settings.copy());
    }

    public void install(CompoundTag fromItem) {
        settings = fromItem != null && fromItem.contains(SettingsNbt.KEY)
            ? fromItem.get(SettingsNbt.KEY).copy() : null;
        setChanged();
    }
}
