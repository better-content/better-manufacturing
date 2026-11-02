package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsAssemblyHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AbstractPneumaticCraftBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native te_serializer invokes this hook for every dropped/wrenched PNCR block item. */
@Mixin(value = AbstractPneumaticCraftBlockEntity.class, remap = false)
public abstract class PneumatictoolsAssemblyLootMixin {
    @Inject(method = "serializeExtraItemData", at = @At("TAIL"), remap = false)
    private void betterManufacturing$loot(CompoundTag blockEntityTag, boolean preserveState, CallbackInfo ci) {
        if ((Object)this instanceof PneumatictoolsAssemblyHolder holder && holder.betterManufacturing$settings() != null)
            SettingsNbt.write(blockEntityTag, holder.betterManufacturing$settings());
    }
}
