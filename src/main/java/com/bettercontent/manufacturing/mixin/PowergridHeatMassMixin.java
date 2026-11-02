package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.powergrid.PowergridRatings;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.config.ThermalValues;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Same electrical input and native heat thresholds; rated plate changes only thermal inertia. */
@Mixin(targets = {
    "org.patryk3211.powergrid.electricity.basinheater.BasinHeaterBlockEntity",
    "org.patryk3211.powergrid.electricity.heater.HeaterBlockEntity"
}, remap = false)
public abstract class PowergridHeatMassMixin {
    @Unique private boolean manufacturing$loadedThermalRating;
    /** ForgeData is loaded after construction; reconcile once on first powered native tick. */
    @Inject(method = "electricalTick", at = @At("HEAD"))
    private void manufacturing$afterLoad(CallbackInfo ci) {
        if (manufacturing$loadedThermalRating) return;
        manufacturing$loadedThermalRating = true;
        var be = (BlockEntity) (Object) this;
        var thermal = BlockEntityBehaviour.get((SmartBlockEntity) be, ThermalBehaviour.TYPE);
        if (thermal == null) return;
        var id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        float mass = id != null && id.getPath().equals("basin_heater") ? 2f : ThermalValues.getMass(be.getBlockState().getBlock());
        thermal.setThermalMass((float) (mass / PowergridRatings.ratio(be, "thermal_mass_ratio")));
    }
    @ModifyArg(method = "specifyThermalBehaviour", at = @At(value = "INVOKE", target = "Lorg/patryk3211/powergrid/electricity/base/ThermalBehaviour;always(Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;FFF)Lorg/patryk3211/powergrid/electricity/base/ThermalBehaviour;"), index = 1)
    private float manufacturing$thermalMass(float nativeMass) {
        return (float) (nativeMass / PowergridRatings.ratio((BlockEntity) (Object) this, "thermal_mass_ratio"));
    }
}
