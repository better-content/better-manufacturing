package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkItemApplication;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject unpaid Clockwork item applications before Create starts or consumes the deployer work. */
@Mixin(value = DeployerBlockEntity.class, remap = false)
public abstract class ClockworkDeployerRecipeMixin {
    @Inject(method = "getRecipe", at = @At("RETURN"), cancellable = true)
    private void manufacturing$requireClockworkWorkingInput(ItemStack processed,
        CallbackInfoReturnable<Recipe<? extends Container>> cir) {
        Recipe<?> recipe = cir.getReturnValue();
        if (recipe == null) return;
        var player = ((DeployerBlockEntity) (Object) this).getPlayer();
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (!ClockworkItemApplication.canApply(recipe, processed, held)) cir.setReturnValue(null);
    }
}
