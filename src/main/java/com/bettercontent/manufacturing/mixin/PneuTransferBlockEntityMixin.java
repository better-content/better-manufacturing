package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettingsHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AbstractPneumaticCraftBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Store only our bounded record on the native BE, without replacing any PneumaticCraft state. */
@Mixin(value = AbstractPneumaticCraftBlockEntity.class, remap = false)
public abstract class PneuTransferBlockEntityMixin implements PneuTransferSettingsHolder {
    @Unique private MachineSettings betterManufacturing$transferSettings;

    @Override public MachineSettings betterManufacturing$transferSettings() { return betterManufacturing$transferSettings; }
    @Override public void betterManufacturing$setTransferSettings(MachineSettings settings) {
        String output = betterManufacturing$output();
        betterManufacturing$transferSettings = settings != null && settings.output().equals(output) ? settings : null;
        ((BlockEntity) (Object) this).setChanged();
    }
    @Unique private String betterManufacturing$output() {
        var block = ((BlockEntity) (Object) this).getBlockState().getBlock();
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void betterManufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (betterManufacturing$transferSettings != null) SettingsNbt.write(tag, betterManufacturing$transferSettings);
    }
    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void betterManufacturing$load(CompoundTag tag, CallbackInfo ci) {
        String output = betterManufacturing$output();
        betterManufacturing$transferSettings = PneuTransferSettings.supports(output) ? SettingsNbt.read(tag, output) : null;
    }
}
