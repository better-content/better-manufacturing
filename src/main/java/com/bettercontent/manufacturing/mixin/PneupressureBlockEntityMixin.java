package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AbstractPneumaticCraftBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractPneumaticCraftBlockEntity.class, remap = false)
public abstract class PneupressureBlockEntityMixin implements PneupressureBuild {
    @Unique private Tag pneupressure$settings;
    @Override public Tag pneupressure$raw() { return pneupressure$settings; }
    @Override public void pneupressure$install(Tag raw) {
        pneupressure$settings = raw == null ? null : raw.copy();
        ((BlockEntity) (Object) this).setChanged();
    }
    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void pneupressure$load(CompoundTag tag, CallbackInfo ci) {
        pneupressure$settings = tag.contains(SettingsNbt.KEY, Tag.TAG_COMPOUND) ? tag.get(SettingsNbt.KEY).copy() : null;
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void pneupressure$save(CompoundTag tag, CallbackInfo ci) {
        if (pneupressure$settings != null) tag.put(SettingsNbt.KEY, pneupressure$settings.copy());
    }
    @Inject(method = "serializeExtraItemData", at = @At("TAIL"))
    private void pneupressure$itemData(CompoundTag tag, boolean preserve, CallbackInfo ci) {
        if (pneupressure$settings != null) tag.put(SettingsNbt.KEY, pneupressure$settings.copy());
    }
}
