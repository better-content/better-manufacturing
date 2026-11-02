package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.clockwork.ClockworkSettingsStore;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Match kinetic consumption to native throughput on Clockwork's actual powered machines. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class ClockworkKineticCostMixin {
    @Shadow protected float lastStressApplied;
    @Inject(method = "calculateStressApplied", at = @At("RETURN"), cancellable = true)
    private void manufacturing$kineticCost(CallbackInfoReturnable<Float> callback) {
        var kinetic = (KineticBlockEntity) (Object) this;
        var id = ForgeRegistries.BLOCKS.getKey(kinetic.getBlockState().getBlock());
        if (id == null || !id.getNamespace().equals("vs_clockwork")) return;
        String property = switch (id.getPath()) {
            case "air_compressor", "pump_duct", "valve_duct", "gas_nozzle" -> "gas_transfer";
            case "andesite_flap_bearing", "smart_flap_bearing", "gyro", "reactionwheel" -> "actuator_response";
            default -> null;
        };
        if (property == null) return;
        double rate = ClockworkSettingsStore.rate(this, property);
        lastStressApplied = (float) (callback.getReturnValueF() * rate);
        callback.setReturnValue(lastStressApplied);
    }
}
