package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BlockItem handles permission, placement and normal stack consumption; only copy the baked tag. */
@Mixin(BlockItem.class)
public abstract class LittleLogisticsHopperPlacementMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("RETURN"))
    private void manufacturing$place(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide) LittleLogisticsRuntime.apply(level.getBlockEntity(pos), stack);
    }
}
