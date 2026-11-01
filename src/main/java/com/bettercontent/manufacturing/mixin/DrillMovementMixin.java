package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.DrillEngineering;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class DrillMovementMixin {
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$rate(MovementContext context, CallbackInfoReturnable<Float> callback) {
        if ((Object) this instanceof DrillMovementBehaviour)
            callback.setReturnValue((float) (callback.getReturnValueF() * DrillEngineering.evaluate(context.blockEntityData).rate()));
    }
    @Redirect(method = "visitNewPosition", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingMovementBehaviour;canBreak(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean manufacturing$start(BlockBreakingMovementBehaviour self, Level world, BlockPos pos, BlockState state, MovementContext context, BlockPos visited) {
        return self.canBreak(world, pos, state) && (!(self instanceof DrillMovementBehaviour) || DrillEngineering.evaluate(context.blockEntityData).canCut(state));
    }
    @Redirect(method = "tickBreaker", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingMovementBehaviour;canBreak(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean manufacturing$continue(BlockBreakingMovementBehaviour self, Level world, BlockPos pos, BlockState state, MovementContext context) {
        return self.canBreak(world, pos, state) && (!(self instanceof DrillMovementBehaviour) || DrillEngineering.evaluate(context.blockEntityData).canCut(state));
    }
}
