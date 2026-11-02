package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessNative;
import com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.entity.AbstractPneumaticCraftBlockEntity;
import me.desht.pneumaticcraft.common.block.entity.IHeatExchangingTE;
import net.minecraft.core.Direction;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All PNCR subclasses invoke this base save/load; only the ten selected IDs accept settings. */
@Mixin(value = AbstractPneumaticCraftBlockEntity.class, remap = false)
public abstract class PneuProcessEntityMixin implements PneuProcessNative.Holder {
    @Unique private MachineSettings betterManufacturing$processSettings;
    @Unique private boolean betterManufacturing$heatApplied;
    @Unique private java.util.Map<me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic, Double> betterManufacturing$heatBaselines;
    @Override public MachineSettings betterManufacturing$processSettings() { return betterManufacturing$processSettings; }
    @Override public void betterManufacturing$setProcessSettings(MachineSettings settings) {
        var entity = (BlockEntity) (Object) this;
        if (!PneuProcessSettings.supports(PneuProcessNative.output(entity))) return;
        betterManufacturing$processSettings = settings;
        betterManufacturing$applyHeat(entity);
        entity.setChanged();
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void betterManufacturing$save(CompoundTag tag, CallbackInfo ci) {
        if (betterManufacturing$processSettings != null) SettingsNbt.write(tag, betterManufacturing$processSettings);
    }
    @Inject(method = "load", at = @At("HEAD"), remap = true)
    private void betterManufacturing$restoreHeatBeforeLoad(CompoundTag tag, CallbackInfo ci) {
        // Native heat NBT saves temperature, not thermal resistance. Loading new
        // settings into the same entity must never divide an already rated
        // exchanger's resistance again (nor leave one rated when settings vanish).
        if (betterManufacturing$heatBaselines == null) return;
        betterManufacturing$heatBaselines.forEach((exchanger, nativeResistance) -> exchanger.setThermalResistance(nativeResistance));
        betterManufacturing$heatBaselines.clear();
    }
    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void betterManufacturing$load(CompoundTag tag, CallbackInfo ci) {
        var entity = (BlockEntity) (Object) this;
        String id = PneuProcessNative.output(entity);
        betterManufacturing$processSettings = PneuProcessSettings.supports(id) ? SettingsNbt.read(tag, id) : null;
        betterManufacturing$heatApplied = false;
    }
    @Inject(method = "onLoad", at = @At("TAIL"), remap = false)
    private void betterManufacturing$initHeat(CallbackInfo ci) {
        betterManufacturing$applyHeat((BlockEntity) (Object) this);
    }
    @Unique private void betterManufacturing$applyHeat(BlockEntity entity) {
        if (betterManufacturing$heatApplied || betterManufacturing$processSettings == null || !(entity instanceof IHeatExchangingTE heated)) return;
        String output = PneuProcessNative.output(entity);
        double rate = PneuProcessNative.rate(entity, output.equals("pneumaticcraft:heat_pipe") || output.equals("pneumaticcraft:heat_sink")
                || output.equals("pneumaticcraft:vortex_tube") ? "thermal_transfer" : "process_rate");
        if (rate == 1) return;
        var seen = Collections.newSetFromMap(new IdentityHashMap<me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic, Boolean>());
        if (betterManufacturing$heatBaselines == null) betterManufacturing$heatBaselines = new IdentityHashMap<>();
        for (var side : Direction.values()) {
            var exchanger = heated.getHeatExchanger(side);
            if (exchanger != null && seen.add(exchanger)) {
                double nativeResistance = betterManufacturing$heatBaselines.getOrDefault(exchanger, exchanger.getThermalResistance());
                if (Double.isFinite(nativeResistance) && nativeResistance > 0) {
                    betterManufacturing$heatBaselines.putIfAbsent(exchanger, nativeResistance);
                    exchanger.setThermalResistance(nativeResistance / rate);
                }
            }
        }
        betterManufacturing$heatApplied = true;
    }
}
