package com.bettercontent.manufacturing.integration.createbulk;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.BoundedNbt;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

/** Persistent engine settings; boiler heat/water, engine direction and shaft ownership remain native. */
public final class SteamVariantBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<SteamVariantBehaviour> TYPE = new BehaviourType<>("better_manufacturing:steam_variant");
    private Tag raw;
    private MachineSettings settings;
    public SteamVariantBehaviour(SteamEngineBlockEntity engine) { super(engine); }
    @Override public BehaviourType<?> getType() { return TYPE; }
    @Override public void read(CompoundTag tag, boolean client) {
        raw = null;
        if (tag.contains(SettingsNbt.KEY)) {
            var offered = tag.get(SettingsNbt.KEY);
            try {
                BoundedNbt.check(offered);
                raw = offered.copy(); // Keep bounded malformed settings for native drops.
            } catch (IllegalArgumentException oversized) {
                // Never copy an oversized untrusted record into a save or packet.
            }
        }
        settings = SettingsNbt.read(tag, "create:steam_engine");
        // Only a complete baked engine envelope may change supplied native
        // boiler capacity; wrong-family tags remain inert.
        if (settings != null && (!settings.properties().keySet().equals(java.util.Set.of("conversion", "mechanical_limit"))
            || settings.properties().values().stream().anyMatch(value -> !Double.isFinite(value)
                || value < .75 || value > 1.5))) settings = null;
    }
    @Override public void write(CompoundTag tag, boolean client) { if (raw != null) tag.put(SettingsNbt.KEY, raw.copy()); }
    @Override public boolean isSafeNBT() { return true; }
    @Override public void writeSafe(CompoundTag tag) { if (raw != null) tag.put(SettingsNbt.KEY, raw.copy()); }
    public MachineSettings settings() { return settings; }
    public ItemStack item() {
        var stack = new ItemStack(blockEntity.getBlockState().getBlock());
        writeSafe(stack.getOrCreateTag()); return stack;
    }
    public void install(CompoundTag tag) {
        read(tag, false); blockEntity.setChanged(); blockEntity.sendData();
        var engine = (SteamEngineBlockEntity) blockEntity;
        if (engine.getLevel() != null && !engine.getLevel().isClientSide) {
            var shaft = engine.getShaft();
            if (shaft != null && shaft.hasNetwork())
                shaft.getOrCreateNetwork().updateCapacityFor(shaft, shaft.calculateAddedStressCapacity());
        }
    }
    public static SteamVariantBehaviour get(Object block) {
        return block instanceof SteamEngineBlockEntity engine ? BlockEntityBehaviour.get(engine, TYPE) : null;
    }
    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(SteamEngineBlockEntity.class,
            (BlockEntityBehaviourEvent<SteamEngineBlockEntity> event) -> {
                if (get(event.getBlockEntity()) == null) event.attach(new SteamVariantBehaviour(event.getBlockEntity()));
            });
    }
}
