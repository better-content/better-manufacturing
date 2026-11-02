package com.bettercontent.manufacturing.mixin.createfluids;

import com.bettercontent.manufacturing.integration.createfluids.FluidNativeRates;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

public final class FluidCapacityMixins {
    private FluidCapacityMixins() {}
    @Mixin(value = SmartFluidTankBehaviour.TankSegment.class, remap = false)
    public interface TankSegmentAccess {
        @Accessor("tank") SmartFluidTank manufacturing$fluidTank();
    }
    @Mixin(value = BasinBlockEntity.class, remap = false)
    public abstract static class BasinCapacity {
        @Inject(method = "read", at = @At("TAIL"))
        private void manufacturing$restoreCapacity(CompoundTag tag, boolean client, CallbackInfo ci) {
            FluidNativeRates.resizeBasin((BasinBlockEntity) (Object) this);
        }
    }
    @Mixin(value = FluidTankBlockEntity.class, remap = false)
    public abstract static class TankCapacity {
        @ModifyArg(method = {"read", "applyFluidTankSize"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/capability/templates/FluidTank;setCapacity(I)Lnet/minecraftforge/fluids/capability/templates/FluidTank;"), index = 0)
        private int manufacturing$nativeCapacity(int nativeCapacity) {
            double factor = FluidNativeRates.rate(this, "fluid_capacity");
            long capacity = Math.round(nativeCapacity * factor);
            return (int) Math.min(Integer.MAX_VALUE, Math.max(nativeCapacity, capacity));
        }
    }
}
