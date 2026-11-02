package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

public final class WorkshopRateMixins {
    private WorkshopRateMixins() {}
    @Mixin(value = SawBlockEntity.class, remap = false)
    public abstract static class Saw {
        @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
        private float manufacturing$work(float nativeWork) {
            var settings = KineticVariantBehaviour.get(this);
            return settings == null ? nativeWork : (float) (nativeWork * settings.rate());
        }
    }
    @Mixin(value = MillstoneBlockEntity.class, remap = false)
    public abstract static class Mill {
        @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/millstone/MillstoneBlockEntity;getProcessingSpeed()I"))
        private int manufacturing$work(MillstoneBlockEntity mill) {
            var settings = KineticVariantBehaviour.get(mill);
            return settings == null ? mill.getProcessingSpeed() : settings.advance(mill.getProcessingSpeed());
        }
    }
    @Mixin(value = PressingBehaviour.class, remap = false)
    public abstract static class Press {
        @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/press/PressingBehaviour;getRunningTickSpeed()I"))
        private int manufacturing$work(PressingBehaviour press) {
            var settings = KineticVariantBehaviour.get(press.blockEntity);
            return settings == null ? press.getRunningTickSpeed() : settings.advance(press.getRunningTickSpeed());
        }
    }
    @Mixin(value = MechanicalMixerBlockEntity.class, remap = false)
    public abstract static class Mixer {
        @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/kinetics/mixer/MechanicalMixerBlockEntity;processingTicks:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD, ordinal = 1))
        private void manufacturing$work(MechanicalMixerBlockEntity mixer, int remaining) {
            var settings = KineticVariantBehaviour.get(mixer);
            mixer.processingTicks = settings == null ? remaining : Math.max(0, mixer.processingTicks - settings.advance(1));
        }
    }
}
