package com.bettercontent.manufacturing.integration.create;

import com.bettercontent.manufacturing.persistence.BuildNbt;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import net.minecraft.nbt.*;

/** Create's native behaviour lifecycle owns save, client packets and contraption assembly NBT. */
public final class DrillBuildBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<DrillBuildBehaviour> TYPE = new BehaviourType<>("better_manufacturing:drill");
    private Tag raw;
    private DrillEngineering.State cached;
    private long epoch = -1;
    public DrillBuildBehaviour(DrillBlockEntity blockEntity) { super(blockEntity); CreateManufacturing.track(this); }
    @Override public BehaviourType<?> getType() { return TYPE; }
    @Override public void read(CompoundTag nbt, boolean clientPacket) {
        raw = nbt.contains(BuildNbt.KEY) ? nbt.get(BuildNbt.KEY).copy() : null;
        cached = null;
    }
    @Override public void write(CompoundTag nbt, boolean clientPacket) { if (raw != null) nbt.put(BuildNbt.KEY, raw.copy()); }
    @Override public boolean isSafeNBT() { return true; }
    @Override public void writeSafe(CompoundTag nbt) { write(nbt, false); }
    public boolean hasRecord() { return raw != null; }
    public CompoundTag record() { var tag = new CompoundTag(); write(tag, false); return tag; }
    public DrillEngineering.State state() {
        if (cached == null || epoch != DrillEngineering.epoch()) {
            cached = DrillEngineering.evaluate(record()); epoch = DrillEngineering.epoch();
        }
        return cached;
    }
    public void install(CompoundTag itemTag) {
        read(itemTag, false);
        blockEntity.setChanged();
        refreshKinetics();
    }
    public void refreshKinetics() {
        cached = null;
        blockEntity.sendData();
        if (blockEntity instanceof DrillBlockEntity drill && !drill.getLevel().isClientSide && drill.hasNetwork())
            drill.getOrCreateNetwork().updateStressFor(drill, drill.calculateStressApplied());
    }
}
