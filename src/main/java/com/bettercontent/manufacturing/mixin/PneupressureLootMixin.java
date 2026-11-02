package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import com.bettercontent.manufacturing.integration.pneupressure.PneupressureFuelCost;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.ArrayList;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Annotate the normal native loot, never replace its selection, counts, air or upgrade contents. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PneupressureLootMixin {
    @Inject(method = "getDrops", at = @At("RETURN"), cancellable = true)
    private void pneupressure$drops(LootParams.Builder loot, CallbackInfoReturnable<List<ItemStack>> cir) {
        var be = loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof PneupressureBuild build) || build.pneupressure$raw() == null) return;
        var blockId = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        if (blockId == null) return;
        var drops = new ArrayList<ItemStack>(cir.getReturnValue().size());
        for (var item : cir.getReturnValue()) {
            var copy = item.copy();
            var id = ForgeRegistries.ITEMS.getKey(copy.getItem());
            if (blockId.equals(id)) {
                copy.getOrCreateTag().put(SettingsNbt.KEY, build.pneupressure$raw().copy());
                if (be instanceof PneupressureFuelCost paid && paid.pneupressure$fuelRemainder() > 0
                    && paid.pneupressure$fuelRemainder() < 1)
                    copy.getOrCreateTagElement("BlockEntityTag")
                        .putDouble("better_manufacturing:fuel_remainder", paid.pneupressure$fuelRemainder());
            }
            drops.add(copy);
        }
        cir.setReturnValue(drops);
    }
}
