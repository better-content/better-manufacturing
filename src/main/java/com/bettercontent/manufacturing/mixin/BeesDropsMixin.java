package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.bees.BeesSettings;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Copy only an actual bee block's own drop; never rewrite native inventory/bonus drops. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BeesDropsMixin {
    @Inject(method = "getDrops", at = @At("RETURN"), cancellable = true)
    private void bees$dropSettings(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        var be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be == null || !BeesSettings.supported(BeesSettings.id(be)) || BeesSettings.read(be) == null) return;
        var drops = new ArrayList<ItemStack>(cir.getReturnValue().size());
        for (ItemStack stack : cir.getReturnValue()) {
            ItemStack copy = stack.copy();
            BeesSettings.copy(be, copy);
            drops.add(copy);
        }
        cir.setReturnValue(drops);
    }
}
