package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneupressure.PneupressureBuild;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.AbstractPneumaticCraftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PNCR's native setPlacedBy runs before the stack is consumed, including tube and chamber subclasses. */
@Mixin(value = AbstractPneumaticCraftBlock.class, remap = false)
public abstract class PneupressurePlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("TAIL"), remap = true)
    private void pneupressure$place(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide) return;
        var block = level.getBlockEntity(pos);
        if (!(block instanceof PneupressureBuild build)) return;
        var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        var blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (itemId == null || blockId == null || !itemId.equals(blockId)) return;
        if (SettingsNbt.read(stack.getTag(), itemId.toString()) != null)
            build.pneupressure$install(stack.getTag().get(SettingsNbt.KEY));
    }
}
