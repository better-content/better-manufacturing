package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.jesz.createdieselgenerators.content.diesel_engine.IEngine;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only an actual placed matching engine accepts its crafting-time scalar record. */
@Mixin(BlockItem.class)
public abstract class DieselPlacementMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private static void manufacturing$placeDiesel(Level level, Player player, BlockPos pos, ItemStack source, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof IEngine engine)
            || !(engine instanceof DieselEngineData data) || !(engine instanceof SmartBlockEntity be)) return;
        var id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        if (id == null || !source.is(be.getBlockState().getBlock().asItem())) return;
        var settings = SettingsNbt.read(source.getTag(), id.toString());
        if (settings != null) { data.manufacturing$setEngine(settings); be.sendData(); }
    }
}
