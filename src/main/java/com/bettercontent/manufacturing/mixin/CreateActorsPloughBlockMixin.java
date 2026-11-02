package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.createactors.CreateActorsPloughBlockEntity;
import com.simibubi.create.content.contraptions.actors.plough.PloughBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** The native plough has no BE; a minimal persistent one carries its crafted settings onto contraptions. */
@Mixin(value = PloughBlock.class, remap = false)
public abstract class CreateActorsPloughBlockMixin implements EntityBlock {
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreateActorsPloughBlockEntity(pos, state);
    }
}
