package com.bettercontent.manufacturing.mixin;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.integration.diesel.DieselEngineData;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CDG engines keep their native tank, upgrade, redstone and orientation data. */
@Mixin(targets = {
    "com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlockEntity",
    "com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity",
    "com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlockEntity"
}, remap = false)
public abstract class DieselEngineDataMixin extends BlockEntity implements DieselEngineData {
    @Unique private MachineSettings manufacturing$engineSettings;
    @Unique private MachineSettings manufacturing$turboSettings;
    protected DieselEngineDataMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override public MachineSettings manufacturing$engine() { return manufacturing$engineSettings; }
    @Override public MachineSettings manufacturing$turbo() { return manufacturing$turboSettings; }
    @Override public void manufacturing$setEngine(MachineSettings settings) {
        manufacturing$engineSettings = settings;
        setChanged();
    }
    @Override public void manufacturing$setTurbo(MachineSettings settings) {
        manufacturing$turboSettings = settings;
        setChanged();
    }
    @Inject(method = "read", at = @At("TAIL"))
    private void manufacturing$read(CompoundTag tag, boolean client, CallbackInfo ci) {
        var id = ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock());
        manufacturing$engineSettings = id == null ? null : SettingsNbt.read(tag, id.toString());
        manufacturing$turboSettings = tag.contains(TURBO_KEY, Tag.TAG_COMPOUND)
            ? SettingsNbt.read(tag.getCompound(TURBO_KEY), "createdieselgenerators:engine_turbocharger") : null;
    }
    @Inject(method = "write", at = @At("TAIL"))
    private void manufacturing$write(CompoundTag tag, boolean client, CallbackInfo ci) {
        if (manufacturing$engineSettings != null) SettingsNbt.write(tag, manufacturing$engineSettings);
        if (manufacturing$turboSettings != null) tag.put(TURBO_KEY, DieselEngineData.addonTag(manufacturing$turboSettings));
    }
}
