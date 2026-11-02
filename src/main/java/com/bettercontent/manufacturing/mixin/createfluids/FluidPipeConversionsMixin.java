package com.bettercontent.manufacturing.mixin.createfluids;

import com.bettercontent.manufacturing.integration.createfluids.FluidPipeConversions;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Run native Create conversion unchanged, copying the baked settings across the BE swap. */
public final class FluidPipeConversionsMixin {
    private FluidPipeConversionsMixin() {}
    @Mixin(value = FluidPipeBlock.class, remap = false)
    public abstract static class ToGlass {
        @Redirect(method = "onWrenched", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
        private boolean manufacturing$toGlass(Level level, BlockPos pos, BlockState state) {
            return FluidPipeConversions.swap(level, pos, state, 3);
        }
    }
    @Mixin(value = GlassFluidPipeBlock.class, remap = false)
    public abstract static class FromGlass {
        @Redirect(method = "onWrenched", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private boolean manufacturing$fromGlass(Level level, BlockPos pos, BlockState state, int flags) {
            return FluidPipeConversions.swap(level, pos, state, flags);
        }
    }
    @Mixin(value = EncasedPipeBlock.class, remap = false)
    public abstract static class Encased {
        @Redirect(method = {"onWrenched", "handleEncasing"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
        private boolean manufacturing$encased(Level level, BlockPos pos, BlockState state) {
            return FluidPipeConversions.swap(level, pos, state, 3);
        }
    }
}
