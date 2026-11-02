package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.AbstractPneumaticCraftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Placement copies crafted item settings after PNCR's normal name and exchanger setup. */
@Mixin(value = AbstractPneumaticCraftBlock.class, remap = false)
public abstract class PneuProcessPlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("TAIL"), remap = true)
    private void betterManufacturing$placed(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PneuProcessNative.Holder holder && PneuProcessSettings.supports(PneuProcessNative.output(blockEntity))) {
            var settings = SettingsNbt.read(stack.getTag(), PneuProcessNative.output(blockEntity));
            if (settings != null) holder.betterManufacturing$setProcessSettings(settings);
        }
    }
}
