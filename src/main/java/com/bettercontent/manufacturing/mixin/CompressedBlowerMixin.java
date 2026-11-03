package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.compressed.CompressedBlowerRates;
import com.bettercontent.manufacturing.integration.compressed.CompressedBlowerRates.MeshAccess;
import com.lgmrszd.compressedcreativity.blocks.air_blower.AirBlowerBlockEntity;
import com.lgmrszd.compressedcreativity.blocks.advanced_air_blower.AdvancedAirBlowerBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mesh identity and processing type remain Compressed Creativity's; only work/air budgets scale. */
public final class CompressedBlowerMixin {
    private CompressedBlowerMixin() {}
    @Mixin(value = AirBlowerBlockEntity.class, remap = false)
    public abstract static class Basic {
        @Inject(method = "calculateProcessingSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$work(CallbackInfoReturnable<Float> result) {
            double rate = CompressedBlowerRates.rate(this);
            if (rate != 1) result.setReturnValue((float) Math.min(2, result.getReturnValueF() * rate));
        }
        @Inject(method = "calculateAirUsage", at = @At("RETURN"), cancellable = true)
        private void manufacturing$airCost(CallbackInfoReturnable<Float> result) {
            double rate = CompressedBlowerRates.rate(this);
            if (rate != 1) result.setReturnValue((float) Math.max(1, result.getReturnValueF() * rate));
        }
        @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$nativeFlow(CallbackInfoReturnable<Float> result) {
            double rate = CompressedBlowerRates.rate(this);
            if (rate != 1) result.setReturnValue((float) Math.min(256, result.getReturnValueF() * rate));
        }
    }
    @Mixin(value = AdvancedAirBlowerBlockEntity.class, remap = false)
    public abstract static class Industrial implements MeshAccess {
        @Invoker("getMesh") public abstract ItemStack manufacturing$getInstalledMesh();
        @Invoker("setMesh") public abstract void manufacturing$setInstalledMesh(ItemStack stack);
        @Redirect(method = "lazyTick", at = @At(value = "INVOKE", target = "Lcom/lgmrszd/compressedcreativity/blocks/advanced_air_blower/AdvancedAirBlowerBlockEntity;setMesh(Lnet/minecraft/world/item/ItemStack;)V"))
        private void manufacturing$keepMeshOnTemperatureChange(AdvancedAirBlowerBlockEntity blower, ItemStack replacement) {
            var original = manufacturing$getInstalledMesh();
            var oldId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(original.getItem());
            var newId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(replacement.getItem());
            if (oldId != null && newId != null && newId.getNamespace().equals("compressedcreativity")) {
                var settings = com.bettercontent.manufacturing.persistence.SettingsNbt.read(original.getTag(), oldId.toString());
                if (settings != null && settings.properties().keySet().equals(java.util.Set.of("airflow"))) {
                    double flow = settings.properties().get("airflow");
                    if (Double.isFinite(flow) && flow >= .75 && flow <= 1.5)
                        com.bettercontent.manufacturing.persistence.SettingsNbt.write(replacement.getOrCreateTag(),
                            new com.bettercontent.manufacturing.api.MachineSettings(newId.toString(), settings.properties(), settings.materials(), null));
                }
            }
            manufacturing$setInstalledMesh(replacement);
        }
        @Inject(method = "calculateProcessingSpeed", at = @At("RETURN"), cancellable = true)
        private void manufacturing$work(CallbackInfoReturnable<Float> result) {
            double rate = CompressedBlowerRates.rate(this);
            if (rate != 1) result.setReturnValue((float) Math.min(4, result.getReturnValueF() * rate));
        }
        @Inject(method = "calculateAirUsage", at = @At("RETURN"), cancellable = true)
        private void manufacturing$airCost(CallbackInfoReturnable<Float> result) {
            double rate = CompressedBlowerRates.rate(this);
            if (rate != 1) result.setReturnValue((float) Math.max(1, result.getReturnValueF() * rate));
        }
        @Inject(method = "getMaxDistance", at = @At("RETURN"), cancellable = true)
        private void manufacturing$nativeDistance(CallbackInfoReturnable<Float> result) {
            double rate = CompressedBlowerRates.rate(this);
            if (rate != 1) result.setReturnValue((float) Math.min(2.5, result.getReturnValueF() * rate));
        }
    }
}
