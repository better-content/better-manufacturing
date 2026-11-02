package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.loot.LootFunc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PNCR's existing te_serializer emits all its normal fluid/upgrade NBT; add only our settings. */
@Mixin(value = LootFunc.BlockEntitySerializerFunction.class, remap = false)
public abstract class PneuProcessLootMixin {
    @Inject(method = "applyBEdata", at = @At("RETURN"))
    private void betterManufacturing$drop(ItemStack stack, BlockEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (entity instanceof PneuProcessNative.Holder holder && holder.betterManufacturing$processSettings() != null)
            SettingsNbt.write(cir.getReturnValue().getOrCreateTag(), holder.betterManufacturing$processSettings());
    }
}
