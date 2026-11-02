package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsRuntime;
import me.desht.pneumaticcraft.common.item.JackHammerItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native enum persists for old bits; full configured bit stack preserves its small baked settings. */
@Mixin(value = JackHammerItem.DrillBitHandler.class, remap = false)
public abstract class PneumatictoolsBitHandlerMixin {
    @Shadow @Final private ItemStack jackhammerStack;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void betterManufacturing$read(ItemStack stack, CallbackInfo ci) {
        if (PneumatictoolsRuntime.hasPhysicalHead(stack)) return;
        ItemStack bit = PneumatictoolsRuntime.readStoredBit(stack);
        if (!bit.isEmpty()) ((JackHammerItem.DrillBitHandler)(Object)this).setStackInSlot(0, bit);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void betterManufacturing$save(CallbackInfo ci) {
        ItemStack bit = ((JackHammerItem.DrillBitHandler)(Object)this).getStackInSlot(0);
        PneumatictoolsRuntime.saveStoredBit(jackhammerStack, bit);
    }
}
