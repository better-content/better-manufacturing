package com.bettercontent.manufacturing.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.function.BooleanSupplier;

/** Native private transfer entrypoint: retains all vanilla extraction/insertion and inventory checks. */
@Mixin(HopperBlockEntity.class)
public interface LittleLogisticsHopperInvoker {
    @Invoker("tryMoveItems")
    static boolean manufacturing$tryMoveItems(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, BooleanSupplier suck) {
        throw new AssertionError("Mixin invoker not applied");
    }
}
