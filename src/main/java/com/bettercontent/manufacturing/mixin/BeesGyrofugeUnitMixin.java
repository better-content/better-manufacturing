package com.bettercontent.manufacturing.mixin;

import com.accbdd.complicated_bees.bees.MachineModifier;
import com.accbdd.complicated_bees.block.entity.gyrofuge.AbstractGyrofugePoweredModifierBlockEntity;
import com.bettercontent.manufacturing.integration.bees.BeesSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native gyrofuge assembly computes the modifier once. Preserve yield and processing identity. */
@Mixin(value = AbstractGyrofugePoweredModifierBlockEntity.class, remap = false)
public abstract class BeesGyrofugeUnitMixin {
    @Inject(method = "getMachineModifier", at = @At("RETURN"), cancellable = true)
    private void bees$workingRate(CallbackInfoReturnable<MachineModifier> cir) {
        double rate = BeesSettings.rate((BlockEntity)(Object)this);
        if (rate == 1) return;
        MachineModifier nativeModifier = cir.getReturnValue();
        cir.setReturnValue(new MachineModifier((float)(nativeModifier.getSpeedMod() * rate),
            nativeModifier.getEfficiencyMod(), nativeModifier.getOutputMod(), nativeModifier.getProcessingMod()));
    }
}
