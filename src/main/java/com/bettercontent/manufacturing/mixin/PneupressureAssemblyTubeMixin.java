package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AssemblyLaserBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/** The native laser consumes twenty configured valves and produces its usual eight tubes. */
@Mixin(value = AssemblyLaserBlockEntity.class, remap = false)
public abstract class PneupressureAssemblyTubeMixin {
    @Inject(method = "getLaseredOutputForItem", at = @At("RETURN"), cancellable = true)
    private void pneupressure$inheritValve(ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        var output = cir.getReturnValue();
        if (output.isEmpty()) return;
        var inId = ForgeRegistries.ITEMS.getKey(input.getItem());
        var outId = ForgeRegistries.ITEMS.getKey(output.getItem());
        if (inId == null || outId == null || !inId.toString().equals("pneumaticcraft:pressure_chamber_valve")
            || !outId.toString().equals("pneumaticcraft:advanced_pressure_tube")) return;
        var settings = SettingsNbt.read(input.getTag(), inId.toString());
        if (settings == null || !settings.properties().keySet().equals(java.util.Set.of("pressure_envelope"))) return;
        double envelope = settings.properties().get("pressure_envelope");
        if (!Double.isFinite(envelope) || envelope < .75 || envelope > 1) return;
        var result = output.copy();
        SettingsNbt.write(result.getOrCreateTag(), SettingsNbt.inherit(outId.toString(), "pressure_envelope", settings));
        cir.setReturnValue(result);
    }
}
