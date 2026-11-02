package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.integration.createactors.CreateActorsPloughBlockEntity;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Transfers a valid baked item setting to the native actor BE, including roller offset placements. */
@Mixin(Block.class)
public abstract class CreateActorsPlacementMixin {
    @Inject(method = "setPlacedBy", at = @At("RETURN"))
    private void manufacturing$actorSettings(Level level, BlockPos pos, BlockState state,
                                                LivingEntity placer, ItemStack item, CallbackInfo ci) {
        if (level.isClientSide || item.isEmpty()) return;
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return;
        String name = id.toString();
        if (!name.equals("create:mechanical_plough") && !name.equals("create:mechanical_roller")) return;
        var settings = SettingsNbt.read(item.getTag(), name);
        if (settings == null) return;
        var be = level.getBlockEntity(pos);
        if (be instanceof CreateActorsPloughBlockEntity plough) {
            plough.install(item.getTag());
        } else if (be instanceof RollerBlockEntity) {
            var tag = be.saveWithFullMetadata();
            tag.put(SettingsNbt.KEY, item.getTag().get(SettingsNbt.KEY).copy());
            be.load(tag);
            be.setChanged();
        }
    }
}
