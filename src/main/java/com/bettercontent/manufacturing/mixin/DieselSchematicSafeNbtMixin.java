package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.jesz.createdieselgenerators.content.diesel_engine.IEngine;
import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlockEntity;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve only final engine settings and CDG's own upgrade ID across Create safe schematic NBT. */
@Mixin(value = SmartBlockEntity.class, remap = false)
public abstract class DieselSchematicSafeNbtMixin {
    @Inject(method = "writeSafe", at = @At("RETURN"))
    private void manufacturing$saveDieselSchematic(CompoundTag tag, CallbackInfo ci) {
        Object be = this;
        if (!(be instanceof IEngine engine) || !(be instanceof DieselEngineData)) return;
        if (be instanceof DieselEngineBlockEntity || be instanceof ModularDieselEngineBlockEntity
            || be instanceof HugeDieselEngineBlockEntity)
            DieselEngineData.writeSchematic(tag, engine);
    }
}
