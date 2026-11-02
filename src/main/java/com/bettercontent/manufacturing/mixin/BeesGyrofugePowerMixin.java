package com.bettercontent.manufacturing.mixin;

import com.accbdd.complicated_bees.block.entity.gyrofuge.GyrofugeControllerBlockEntity;
import com.accbdd.complicated_bees.multiblock.GyrofugeLogic;
import com.bettercontent.manufacturing.integration.bees.BeesSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native setLogic recalculates from scratch on assembly. Charge matching active RF per adjusted work cycle. */
@Mixin(value = GyrofugeControllerBlockEntity.class, remap = false)
public abstract class BeesGyrofugePowerMixin {
    @Inject(method = "setLogic", at = @At("RETURN"))
    private void bees$assembledEnergy(GyrofugeLogic logic, CallbackInfo ci) {
        GyrofugeControllerBlockEntity controller = (GyrofugeControllerBlockEntity)(Object)this;
        if (logic == null || controller.getLevel() == null) return;
        double rate = 1;
        for (var pos : logic.getSpecialBlocks()) {
            BlockEntity unit = controller.getLevel().getBlockEntity(pos);
            if (unit != null && BeesSettings.supported(BeesSettings.id(unit))) rate *= BeesSettings.rate(unit);
        }
        if (Double.isFinite(rate) && rate != 1)
            controller.setActiveEnergyUsage(Math.max(1, (int)Math.ceil(controller.getActiveEnergyUsage() * rate)));
    }
}
