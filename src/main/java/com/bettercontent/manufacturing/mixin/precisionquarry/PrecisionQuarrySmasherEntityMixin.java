package com.bettercontent.manufacturing.mixin.precisionquarry;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.precisionquarry.PrecisionQuarrySettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps target acquisition, break, item pull and native quarry geometry untouched. */
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.item.QuarrySmasherEntity", remap = false)
public abstract class PrecisionQuarrySmasherEntityMixin implements PrecisionQuarrySettings.EntityReceiver {
    @Unique private MachineSettings manufacturing$quarryHead;
    @Override public void manufacturing$installHead(MachineSettings settings) {
        manufacturing$quarryHead = settings != null && settings.output().equals("alexscaves:quarry_smasher") ? settings : null;
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), remap = true)
    private void manufacturing$load(CompoundTag tag, CallbackInfo ci) {
        manufacturing$quarryHead = PrecisionQuarrySettings.read(tag, "alexscaves:quarry_smasher");
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), remap = true)
    private void manufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$quarryHead != null) SettingsNbt.write(tag, manufacturing$quarryHead);
    }
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 35), remap = true)
    private int manufacturing$nextImpact(int nativeCooldown) {
        return manufacturing$quarryHead == null ? nativeCooldown : Math.max(1, (int) Math.round(nativeCooldown / manufacturing$quarryHead.rate()));
    }
}
