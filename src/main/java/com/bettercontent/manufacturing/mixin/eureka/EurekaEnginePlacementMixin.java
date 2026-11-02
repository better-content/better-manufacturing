package com.bettercontent.manufacturing.mixin.eureka;

import com.bettercontent.manufacturing.integration.eureka.EurekaEngineData;
import com.bettercontent.manufacturing.integration.eureka.EurekaEngineSettings;
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
import org.valkyrienskies.eureka.blockentity.EngineBlockEntity;

/** Transfer the crafted record to the real Eureka block entity after BlockItem places it. */
@Mixin(BlockItem.class)
public abstract class EurekaEnginePlacementMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private static void manufacturing$installEureka(Level level, Player player, BlockPos pos, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof EngineBlockEntity engine)
            || !((Object) engine instanceof EurekaEngineData data)) return;
        var settings = SettingsNbt.read(stack.getTag(), EurekaEngineSettings.OUTPUT);
        data.manufacturing$setEngineSettings(EurekaEngineSettings.valid(settings) ? settings : null);
        engine.setChanged();
    }
}
