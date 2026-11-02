package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.trackwork.TrackworkSettings;
import com.bettercontent.manufacturing.integration.trackwork.TrackworkVariant;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import edn.stratodonut.trackwork.tracks.blocks.OleoWheelBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OleoWheelBlockEntity.class, remap = false)
public abstract class TrackworkOleoMixin implements TrackworkVariant {
    @Shadow private double suspensionScale;
    @Unique private MachineSettings manufacturing$variant;
    @Override public String manufacturing$outputId() {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(((BlockEntity)(Object)this).getBlockState().getBlock()));
    }
    @Override public MachineSettings manufacturing$settings() { return manufacturing$variant; }
    @Override public void manufacturing$setSettings(MachineSettings settings) { manufacturing$variant = settings; ((BlockEntity)(Object)this).setChanged(); }
    @Inject(method = "write", at = @At("TAIL"))
    private void manufacturing$write(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (manufacturing$variant != null) SettingsNbt.write(tag, manufacturing$variant);
    }
    @Inject(method = "read", at = @At("TAIL"))
    private void manufacturing$read(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        manufacturing$variant = SettingsNbt.read(tag, manufacturing$outputId());
    }
    /** An unpowered oleo has no Create stress; its rated hub tunes the native suspension scale only. */
    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Ledn/stratodonut/trackwork/tracks/blocks/OleoWheelBlockEntity;suspensionScale:D", opcode = 180))
    private double manufacturing$suspension(OleoWheelBlockEntity wheel) {
        double response = TrackworkSettings.property(this, "suspension_response");
        double load = TrackworkSettings.property(this, "mechanical_load");
        return suspensionScale * Math.max(.75, Math.min(1.5, response * load));
    }
}
