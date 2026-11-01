package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemBuildMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private static void manufacturing$install(Level level, Player player, BlockPos pos, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        CreateManufacturing.install(level, pos, stack);
    }
}
