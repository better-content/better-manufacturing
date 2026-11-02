package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings;
import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettingsHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.AbstractPneumaticCraftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native placement supplies the exact held stack, including creative and offhand placement. */
@Mixin(value = AbstractPneumaticCraftBlock.class, remap = false)
public abstract class PneuTransferPlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void betterManufacturing$place(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PneuTransferSettingsHolder holder)) return;
        String output = String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        if (!PneuTransferSettings.supports(output)) return;
        // A native BlockEntityTag may have just loaded on placement; the top-level crafted record is authoritative.
        var settings = SettingsNbt.read(stack.getTag(), output);
        if (settings != null) holder.betterManufacturing$setTransferSettings(settings);
    }
}
