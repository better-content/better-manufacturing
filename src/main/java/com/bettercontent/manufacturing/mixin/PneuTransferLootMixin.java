package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettingsHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.loot.LootFunc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native PNCR loot serializer keeps the contents/upgrades; append the separate crafted setting only. */
@Mixin(value = LootFunc.BlockEntitySerializerFunction.class, remap = false)
public abstract class PneuTransferLootMixin {
    @Inject(method = "applyBEdata", at = @At("RETURN"))
    private void betterManufacturing$preserve(ItemStack stack, BlockEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (!(entity instanceof PneuTransferSettingsHolder holder) || holder.betterManufacturing$transferSettings() == null) return;
        var result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;
        var settings = holder.betterManufacturing$transferSettings();
        SettingsNbt.write(result.getOrCreateTag(), settings);
        SettingsNbt.write(result.getOrCreateTagElement("BlockEntityTag"), settings);
    }
}
