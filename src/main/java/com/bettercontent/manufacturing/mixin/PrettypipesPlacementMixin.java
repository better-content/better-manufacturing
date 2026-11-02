package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.prettypipes.PrettypipesPressurizerSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Install craft-time settings while the placed item still holds its NBT. */
@Mixin(BlockItem.class)
public abstract class PrettypipesPlacementMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private static void betterManufacturing$install(Level level, Player player, BlockPos pos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PrettypipesPressurizerSettings pressurizer)
            pressurizer.betterManufacturing$setSettings(SettingsNbt.read(stack.getTag(), "prettypipes:pressurizer"));
    }
}
