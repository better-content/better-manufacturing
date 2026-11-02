package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.sifter.ScreenAccess;
import com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native insertion/extraction, including automation, invalidates material evaluation at the mutation. */
@Mixin(targets = "com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity$1", remap = false)
public abstract class SifterMeshInventoryMixin {
    @Unique private ScreenAccess manufacturing$owner;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void manufacturing$owner(SifterBlockEntity owner, int slots, CallbackInfo callback) {
        manufacturing$owner = (ScreenAccess) owner;
    }
    @Inject(method = "onContentsChanged", at = @At("HEAD"))
    private void manufacturing$changed(int slot, CallbackInfo callback) {
        if (manufacturing$owner != null) manufacturing$owner.manufacturing$meshChanged();
    }
}
