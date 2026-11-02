package com.bettercontent.manufacturing.mixin;

import java.util.Set;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The native connected series segment set; no new topology or inventory ownership. */
@Mixin(value = WindingBlockEntity.class, remap = false)
public interface PowergridWindingPartsAccess {
    @Accessor("collectedBEs") Set<WindingBlockEntity> manufacturing$collectedParts();
}
