package com.bettercontent.manufacturing.mixin.createfluids;

import com.bettercontent.manufacturing.integration.createfluids.FluidNativeRates;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hooks native transfer time and pressure usage; recipes, quantities, endpoints and geometry remain native. */
public final class FluidTransferMixins {
    private FluidTransferMixins() {}
    @Mixin(value = SpoutBlockEntity.class, remap = false)
    public abstract static class Spout {
        @ModifyConstant(method = {"whenItemHeld", "tick"}, constant = @Constant(intValue = 20))
        private int manufacturing$fillTime(int nativeTicks) {
            return FluidNativeRates.duration(this, nativeTicks);
        }
    }
    @Mixin(value = ItemDrainBlockEntity.class, remap = false)
    public abstract static class Drain {
        @ModifyConstant(method = {"tick", "continueProcessing"}, constant = @Constant(intValue = 20))
        private int manufacturing$emptyTime(int nativeTicks) {
            return FluidNativeRates.duration(this, nativeTicks);
        }
    }
    @Mixin(value = HosePulleyBlockEntity.class, remap = false)
    public abstract static class Hose {
        @Inject(method = "getMovementSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$hoseSpeed(CallbackInfoReturnable<Float> cir) {
            cir.setReturnValue((float) (cir.getReturnValue() * FluidNativeRates.rate(this, "fluid_rate")));
        }
    }
    @Mixin(value = FluidValveBlockEntity.class, remap = false)
    public abstract static class Valve {
        @Inject(method = "getChaseSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$valveSpeed(CallbackInfoReturnable<Float> cir) {
            cir.setReturnValue((float) Math.min(1, cir.getReturnValue() * FluidNativeRates.rate(this, "fluid_rate")));
        }
    }
    @Mixin(value = KineticBlockEntity.class, remap = false)
    public abstract static class MatchingStress {
        @Shadow protected float lastStressApplied;
        @Inject(method = "calculateStressApplied", at = @At("RETURN"), cancellable = true)
        private void manufacturing$fluidWorkStress(CallbackInfoReturnable<Float> cir) {
            if ((Object) this instanceof HosePulleyBlockEntity || (Object) this instanceof FluidValveBlockEntity) {
                lastStressApplied = (float) (cir.getReturnValueF() * FluidNativeRates.rate(this, "fluid_rate"));
                cir.setReturnValue(lastStressApplied);
            }
        }
    }
    @Mixin(value = PortableFluidInterfaceBlockEntity.InterfaceFluidHandler.class, remap = false)
    public abstract static class Portable {
        // Native requests stay authoritative. Both directions draw from one per-block tick budget;
        // SIMULATE observes the same remaining amount but only a successful EXECUTE spends it.
        @ModifyArg(method = "fill", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/fluids/capability/IFluidHandler;fill(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)I"), index = 0)
        private FluidStack manufacturing$fillLimit(FluidStack input) {
            return FluidNativeRates.capped(input, FluidNativeRates.interfaceLimit(this));
        }
        @ModifyArg(method = "drain(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fluids/capability/IFluidHandler;drain(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;"), index = 0)
        private FluidStack manufacturing$drainStackLimit(FluidStack input) {
            return FluidNativeRates.capped(input, FluidNativeRates.interfaceLimit(this));
        }
        @ModifyArg(method = "drain(ILnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fluids/capability/IFluidHandler;drain(ILnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;"), index = 0)
        private int manufacturing$drainAmountLimit(int nativeRequest) {
            return Math.min(nativeRequest, FluidNativeRates.interfaceLimit(this));
        }
        @Inject(method = "fill", at = @At("RETURN"))
        private void manufacturing$accountFill(FluidStack input, IFluidHandler.FluidAction action,
                                                CallbackInfoReturnable<Integer> cir) {
            if (action.execute()) FluidNativeRates.transferred(this, cir.getReturnValue());
        }
        @Inject(method = "drain(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;",
            at = @At("RETURN"))
        private void manufacturing$accountDrainStack(FluidStack input, IFluidHandler.FluidAction action,
                                                      CallbackInfoReturnable<FluidStack> cir) {
            if (action.execute()) FluidNativeRates.transferred(this, cir.getReturnValue().getAmount());
        }
        @Inject(method = "drain(ILnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;",
            at = @At("RETURN"))
        private void manufacturing$accountDrainAmount(int request, IFluidHandler.FluidAction action,
                                                       CallbackInfoReturnable<FluidStack> cir) {
            if (action.execute()) FluidNativeRates.transferred(this, cir.getReturnValue().getAmount());
        }
    }
}
