package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.CreateManufacturing;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SmartBlockEntity.class, remap = false)
public abstract class DrillSchematicMixin {
    @Inject(method = "getRequiredItems", at = @At("HEAD"), cancellable = true)
    private void manufacturing$exactBuild(BlockState state, CallbackInfoReturnable<ItemRequirement> callback) {
        var behaviour = CreateManufacturing.behaviour(this);
        if (behaviour != null && behaviour.hasRecord()) callback.setReturnValue(CreateManufacturing.requirement(behaviour));
    }
}
