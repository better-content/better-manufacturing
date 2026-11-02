package com.bettercontent.manufacturing.mixin.precisionquarry;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.precisionquarry.PrecisionQuarrySettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only platform-building cadence changes; native quarry radius, inventory and placement decisions stay intact. */
@Mixin(value = RatQuarryBlockEntity.class, remap = false)
public abstract class PrecisionQuarryRatBlockMixin implements PrecisionQuarrySettings.BlockReceiver {
    @Unique private MachineSettings manufacturing$quarrySettings;
    @Override public void manufacturing$install(MachineSettings settings) {
        manufacturing$quarrySettings = settings != null && settings.output().equals("rats:rat_quarry") ? settings : null;
        ((BlockEntity) (Object) this).setChanged();
    }
    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void manufacturing$load(CompoundTag tag, CallbackInfo ci) {
        manufacturing$quarrySettings = PrecisionQuarrySettings.read(tag, "rats:rat_quarry");
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void manufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$quarrySettings != null) SettingsNbt.write(tag, manufacturing$quarrySettings);
    }
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 20))
    private static int manufacturing$platformInterval(int nativeInterval, Level level, BlockPos pos, BlockState state, RatQuarryBlockEntity quarry) {
        MachineSettings settings = ((PrecisionQuarryRatBlockMixin) (Object) quarry).manufacturing$quarrySettings;
        return settings == null ? nativeInterval : Math.max(1, (int) Math.round(nativeInterval / settings.rate()));
    }
}
