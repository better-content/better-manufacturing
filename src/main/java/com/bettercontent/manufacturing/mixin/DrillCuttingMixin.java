package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class DrillCuttingMixin {
    @Inject(method = "getBreakSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$rate(CallbackInfoReturnable<Float> callback) {
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null) callback.setReturnValue((float) (callback.getReturnValueF() * behaviour.state().rate()));
    }
    @Inject(method = "shouldRun", at = @At("HEAD"), cancellable = true)
    private void manufacturing$invalidBuild(CallbackInfoReturnable<Boolean> callback) {
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null && !behaviour.state().allowed()) callback.setReturnValue(false);
    }
    @Inject(method = "canBreak", at = @At("RETURN"), cancellable = true)
    private void manufacturing$tier(BlockState state, float hardness, CallbackInfoReturnable<Boolean> callback) {
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null && !behaviour.state().canCut(state)) callback.setReturnValue(false);
    }
}
