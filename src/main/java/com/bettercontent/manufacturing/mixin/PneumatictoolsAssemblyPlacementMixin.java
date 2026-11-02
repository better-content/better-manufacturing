package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsAssemblyHolder;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import me.desht.pneumaticcraft.common.block.AbstractPneumaticCraftBlock;
import me.desht.pneumaticcraft.common.block.AssemblyDrillBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native BlockEntityTag is handled by BlockItem; root crafting settings are copied on first placement. */
@Mixin(value = AbstractPneumaticCraftBlock.class, remap = false)
public abstract class PneumatictoolsAssemblyPlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("TAIL"), remap = true)
    private void betterManufacturing$place(Level level, BlockPos pos, BlockState state, LivingEntity placer,
                                            ItemStack item, CallbackInfo ci) {
        if ((Object)this instanceof AssemblyDrillBlock && level.getBlockEntity(pos) instanceof PneumatictoolsAssemblyHolder holder) {
            var settings = SettingsNbt.read(item.getTag(), "pneumaticcraft:assembly_drill");
            if (settings != null) holder.betterManufacturing$setSettings(settings);
        }
    }
}
