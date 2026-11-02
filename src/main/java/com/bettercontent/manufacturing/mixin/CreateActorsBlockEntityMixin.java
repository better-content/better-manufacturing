package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native actor block entities retain exactly the crafted settings alongside their original NBT. */
@Mixin(BlockEntity.class)
public abstract class CreateActorsBlockEntityMixin {
    @Unique private Tag manufacturing$actorSettings;

    @Inject(method = "load", at = @At("RETURN"))
    private void manufacturing$readActor(CompoundTag tag, CallbackInfo ci) {
        if (!manufacturing$isActor()) return;
        manufacturing$actorSettings = tag.contains(SettingsNbt.KEY) ? tag.get(SettingsNbt.KEY).copy() : null;
    }

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void manufacturing$writeActor(CompoundTag tag, CallbackInfo ci) {
        if (manufacturing$isActor() && manufacturing$actorSettings != null)
            tag.put(SettingsNbt.KEY, manufacturing$actorSettings.copy());
    }

    @Unique private boolean manufacturing$isActor() {
        return (Object) this instanceof RollerBlockEntity;
    }
}
