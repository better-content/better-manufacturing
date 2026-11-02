package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clockwork BEs retain only baked settings, not part stacks or mutable material dependencies. */
@Mixin(BlockEntity.class)
public abstract class ClockworkBlockSettingsMixin implements ClockworkSettingsStore {
    @Unique private CompoundTag manufacturing$clockworkRaw;
    @Unique private MachineSettings manufacturing$clockworkSettings;
    @Unique private boolean manufacturing$clockworkBlock() {
        return ClockworkSettingsStore.output((BlockEntity) (Object) this).startsWith("vs_clockwork:");
    }
    @Override public MachineSettings manufacturing$settings() { return manufacturing$clockworkSettings; }
    @Override public CompoundTag manufacturing$settingsTag() { return manufacturing$clockworkRaw; }
    @Override public void manufacturing$install(CompoundTag tag) {
        if (!manufacturing$clockworkBlock()) return;
        manufacturing$clockworkRaw = tag != null && tag.contains(SettingsNbt.KEY) ? (CompoundTag) tag.getCompound(SettingsNbt.KEY).copy() : null;
        manufacturing$clockworkSettings = SettingsNbt.read(tag, ClockworkSettingsStore.output((BlockEntity) (Object) this));
        ((BlockEntity) (Object) this).setChanged();
    }
    @Inject(method = "load", at = @At("TAIL"))
    private void manufacturing$clockworkLoad(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$clockworkBlock()) manufacturing$install(tag);
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void manufacturing$clockworkSave(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$clockworkBlock() && manufacturing$clockworkRaw != null)
            tag.put(SettingsNbt.KEY, manufacturing$clockworkRaw.copy());
    }
}
