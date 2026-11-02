package com.bettercontent.manufacturing.mixin.precisionquarry;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.precisionquarry.PrecisionQuarrySettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2 speed cards still set native steps. Scale energy-per-step with work, never the recipes or inventory. */
@Mixin(targets = "appeng.blockentity.misc.InscriberBlockEntity", remap = false)
public abstract class PrecisionQuarryInscriberMixin implements PrecisionQuarrySettings.BlockReceiver {
    @Unique private MachineSettings manufacturing$precisionSettings;

    @Override public void manufacturing$install(MachineSettings settings) {
        manufacturing$precisionSettings = settings != null && settings.output().equals("ae2:inscriber") ? settings : null;
        ((BlockEntity) (Object) this).setChanged();
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void manufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$precisionSettings != null) SettingsNbt.write(tag, manufacturing$precisionSettings);
    }
    @Inject(method = "loadTag", at = @At("TAIL"))
    private void manufacturing$load(CompoundTag tag, CallbackInfo ci) {
        manufacturing$precisionSettings = PrecisionQuarrySettings.read(tag, "ae2:inscriber");
    }
    // AE's wrench exports its own settings by replacing the dropped stack tag *after* loot generation.
    // Attach our independent record after native inventory/upgrades have been appended.
    @Inject(method = "addAdditionalDrops", at = @At("TAIL"))
    private void manufacturing$wrenchDrop(Level level, BlockPos pos, List<ItemStack> drops, CallbackInfo ci) {
        if (manufacturing$precisionSettings == null) return;
        for (ItemStack drop : drops) {
            var id = ForgeRegistries.ITEMS.getKey(drop.getItem());
            if (id != null && id.toString().equals("ae2:inscriber"))
                SettingsNbt.write(drop.getOrCreateTag(), manufacturing$precisionSettings);
        }
    }
    @Inject(method = "getMaxProcessingTime", at = @At("RETURN"), cancellable = true)
    private void manufacturing$duration(CallbackInfoReturnable<Integer> cir) {
        if (manufacturing$precisionSettings != null)
            cir.setReturnValue(Math.max(1, (int) Math.round(cir.getReturnValue() / manufacturing$precisionSettings.rate())));
    }
    @ModifyVariable(method = "lambda$tickingRequest$1", at = @At("STORE"), ordinal = 1)
    private int manufacturing$energyPerStep(int cost) {
        return manufacturing$precisionSettings == null ? cost : Math.max(1, (int) Math.round(cost * manufacturing$precisionSettings.rate()));
    }
}
