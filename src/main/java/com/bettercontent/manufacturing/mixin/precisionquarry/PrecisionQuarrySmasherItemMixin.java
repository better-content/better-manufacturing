package com.bettercontent.manufacturing.mixin.precisionquarry;

import com.bettercontent.manufacturing.integration.precisionquarry.PrecisionQuarrySettings;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Transfer the already-baked head rating to the native entity immediately before native spawning/stack consumption. */
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.item.QuarrySmasherItem", remap = false)
public abstract class PrecisionQuarrySmasherItemMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true), remap = true)
    private boolean manufacturing$spawn(Level level, Entity quarry, Level world, Player player, InteractionHand hand) {
        if (quarry instanceof PrecisionQuarrySettings.EntityReceiver receiver)
            receiver.manufacturing$installHead(PrecisionQuarrySettings.read(player.getItemInHand(hand).getTag(), "alexscaves:quarry_smasher"));
        return level.addFreshEntity(quarry);
    }
}
