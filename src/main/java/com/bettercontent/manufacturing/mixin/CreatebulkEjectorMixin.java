package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.createbulk.BulkNative;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spring rating charges Create's native launcher, retaining target, trajectory and inventories. */
@Mixin(value = EjectorBlockEntity.class, remap = false)
public abstract class CreatebulkEjectorMixin {
    @Inject(method = "getWindUpSpeed", at = @At("RETURN"), cancellable = true)
    private void manufacturing$charge(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BulkNative.launcherCharge((EjectorBlockEntity) (Object) this, callback.getReturnValueF()));
    }
}
