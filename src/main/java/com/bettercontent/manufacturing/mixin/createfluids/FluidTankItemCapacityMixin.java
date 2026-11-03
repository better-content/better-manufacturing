package com.bettercontent.manufacturing.mixin.createfluids;

import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.fluids.tank.FluidTankItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Create clamps stored TankContent on placement; use the crafted native tank capacity
 * for a configured item so carrying a full rated tank never deletes its fluid. */
@Mixin(value = FluidTankItem.class, remap = false)
public abstract class FluidTankItemCapacityMixin {
    @Redirect(method = "updateCustomBlockEntityTag", remap = true, at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false))
    private int manufacturing$preserveRatedTank(int nativeCapacity, int amount,
                                                 BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        var settings = SettingsNbt.read(stack.getTag(), "create:fluid_tank");
        double factor = settings == null ? 1 : settings.properties().getOrDefault("fluid_capacity", 1d);
        if (!Double.isFinite(factor) || factor < 1 || factor > 1.5) factor = 1;
        int rated = (int) Math.min(Integer.MAX_VALUE, Math.round(nativeCapacity * factor));
        return Math.min(rated, amount);
    }
}
