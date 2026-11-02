package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.sifter.*;
import com.bettercontent.manufacturing.integration.create.KineticVariantBehaviour;
import com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adjust native work advance only. Recipe rolls, overflow and inventory handling are untouched. */
@Mixin(value = SifterBlockEntity.class, remap = false)
public abstract class SifterFrameMixin implements ScreenAccess {
    @Shadow public ItemStackHandler meshInv;
    @Unique private MeshEngineering.State manufacturing$state;
    @Unique private double manufacturing$fraction;
    @Unique private SifterBlockEntity manufacturing$self() { return (SifterBlockEntity) (Object) this; }
    @Override public MeshEngineering.State manufacturing$screenState() {
        if (manufacturing$state == null) manufacturing$state = MeshEngineering.evaluate(meshInv.getStackInSlot(0), false);
        var frame = KineticVariantBehaviour.get(this);
        if (frame == null || frame.settings() == null) return manufacturing$state;
        double rate = SifterFrameSettings.combined(frame.rate(), manufacturing$state.rate(), manufacturing$state.configured());
        return new MeshEngineering.State(rate, true);
    }
    @Override public void manufacturing$refreshScreen() {
        manufacturing$state = null;
        var self = manufacturing$self();
        if (self.getLevel() != null && !self.getLevel().isClientSide && self.hasNetwork())
            self.getOrCreateNetwork().updateStressFor(self, self.calculateStressApplied());
    }
    @Override public void manufacturing$meshChanged() {
        manufacturing$fraction = 0; manufacturing$refreshScreen(); manufacturing$self().setChanged();
    }
    @Inject(method = "read", at = @At("RETURN"))
    private void manufacturing$read(CompoundTag tag, boolean client, CallbackInfo callback) {
        manufacturing$refreshScreen();
        double fraction = tag.getDouble("better_manufacturing:screen_fraction");
        manufacturing$fraction = Double.isFinite(fraction) && fraction >= 0 && fraction < 1 ? fraction : 0;
    }
    @Inject(method = "write", at = @At("RETURN"))
    private void manufacturing$write(CompoundTag tag, boolean client, CallbackInfo callback) {
        tag.putDouble("better_manufacturing:screen_fraction", manufacturing$fraction);
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/oierbravo/createsifter/content/contraptions/components/sifter/SifterBlockEntity;getProcessingSpeed()I"))
    private int manufacturing$advance(SifterBlockEntity self) {
        double rate = manufacturing$screenState().rate();
        if (rate == 1) return self.getProcessingSpeed();
        double work = self.getProcessingSpeed() * rate + manufacturing$fraction;
        int whole = (int) work; manufacturing$fraction = work - whole; self.setChanged();
        return whole;
    }
}
