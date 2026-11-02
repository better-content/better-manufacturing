package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.trackwork.TrackworkSettings;
import com.bettercontent.manufacturing.integration.trackwork.TrackworkVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit item-to-entity installation, including blank settings fallback. */
@Mixin(BlockItem.class)
public abstract class TrackworkPlacementMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private static void manufacturing$install(Level level, Player player, BlockPos pos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof TrackworkVariant variant) {
            TrackworkSettings.install(variant, stack);
        }
    }
}
