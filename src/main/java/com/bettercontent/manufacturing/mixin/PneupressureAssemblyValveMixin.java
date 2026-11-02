package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AssemblyDrillBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Native assembly robot replaces one physically consumed valve blank with its usual twenty valves. */
@Mixin(value = AssemblyDrillBlockEntity.class, remap = false)
public abstract class PneupressureAssemblyValveMixin {
    @Inject(method = "getDrilledOutputForItem", at = @At("RETURN"), cancellable = true)
    private void pneupressure$inheritBlank(ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack nativeOutput = cir.getReturnValue();
        if (nativeOutput.isEmpty() || !PneupressureSettings.VALVE_BLANK_ID.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(input.getItem())))
            || !"pneumaticcraft:pressure_chamber_valve".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(nativeOutput.getItem())))
            || nativeOutput.getCount() != 20) return;
        MachineSettings blank = SettingsNbt.read(input.getTag(), PneupressureSettings.VALVE_BLANK_ID);
        if (blank == null || !blank.properties().keySet().equals(java.util.Set.of("pressure_envelope"))) return;
        double envelope = blank.properties().get("pressure_envelope");
        if (!Double.isFinite(envelope) || envelope < .75 || envelope > 1) return;
        ItemStack result = nativeOutput.copy();
        SettingsNbt.write(result.getOrCreateTag(), new MachineSettings("pneumaticcraft:pressure_chamber_valve",
            Map.of("pressure_envelope", envelope), blank.materials(), null));
        cir.setReturnValue(result);
    }
}
