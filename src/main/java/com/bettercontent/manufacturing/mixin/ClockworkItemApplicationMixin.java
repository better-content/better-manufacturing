package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkItemApplication;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/** Native Create deployer retains ingredient consumption, overflow and selection before attaching baked settings. */
@Mixin(value = com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks.class, remap = false)
public abstract class ClockworkItemApplicationMixin {
    @Redirect(method = "activate", at = @At(value = "INVOKE", target =
        "Lcom/simibubi/create/foundation/recipe/RecipeApplier;applyRecipeOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/Recipe;Z)Ljava/util/List;"))
    private static List<ItemStack> manufacturing$inheritDuct(Level level, ItemStack processed,
        Recipe<?> recipe, boolean preserveRemainder, TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, Recipe<?> selectedRecipe) {
        ItemStack held = deployer.getPlayer().getMainHandItem().copy();
        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, processed, recipe, preserveRemainder);
        return ClockworkItemApplication.inherit(recipe, processed, held, results);
    }
}
