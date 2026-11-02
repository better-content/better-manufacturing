package com.bettercontent.manufacturing.integration.createfluids;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.BoundedNbt;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Native Create BE state; stores only final baked properties and a cosmetic material label. */
public final class FluidVariantBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<FluidVariantBehaviour> TYPE = new BehaviourType<>("better_manufacturing:create_fluids");
    private Tag raw;
    private MachineSettings settings;
    // Per native interface block, not per ephemeral Forge handler: both directions share a tick budget.
    private long transferTick = Long.MIN_VALUE;
    private int transferUsed;
    public FluidVariantBehaviour(SmartBlockEntity block) { super(block); }
    @Override public BehaviourType<?> getType() { return TYPE; }
    public String outputId() {
        String id = ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString();
        return id.equals("create:glass_fluid_pipe") || id.equals("create:encased_fluid_pipe") ? "create:fluid_pipe" : id;
    }
    @Override public void read(CompoundTag tag, boolean client) {
        raw = null;
        if (tag.contains(SettingsNbt.KEY)) {
            var offered = tag.get(SettingsNbt.KEY);
            try {
                BoundedNbt.check(offered);
                raw = offered.copy(); // Bounded malformed/unknown versions remain available to native drops.
            } catch (IllegalArgumentException oversized) {
                // Oversized untrusted metadata never reaches BE saves, packets or configured drops.
            }
        }
        settings = SettingsNbt.read(tag, outputId());
    }
    @Override public void write(CompoundTag tag, boolean client) {
        if (raw != null) tag.put(SettingsNbt.KEY, raw.copy());
    }
    @Override public boolean isSafeNBT() { return true; }
    @Override public void writeSafe(CompoundTag tag) { if (raw != null) tag.put(SettingsNbt.KEY, raw.copy()); }
    public MachineSettings settings() { return settings; }
    public int remainingTransfer(int nativeLimit) {
        if (nativeLimit == Integer.MAX_VALUE || blockEntity.getLevel() == null) return nativeLimit;
        long now = blockEntity.getLevel().getGameTime();
        if (transferTick != now) { transferTick = now; transferUsed = 0; }
        return Math.max(0, nativeLimit - transferUsed);
    }
    public void recordTransfer(int moved, int nativeLimit) {
        if (moved <= 0 || nativeLimit == Integer.MAX_VALUE || blockEntity.getLevel() == null) return;
        remainingTransfer(nativeLimit);
        transferUsed = (int) Math.min((long) nativeLimit, (long) transferUsed + moved);
    }
    public double rate(String property) {
        if (settings == null) return 1;
        double rate = settings.properties().getOrDefault(property, 1d);
        return Double.isFinite(rate) && rate >= .75 && rate <= 1.5 ? rate : 1;
    }
    public ItemStack item() {
        var item = outputId().equals("create:fluid_pipe")
            ? com.simibubi.create.AllBlocks.FLUID_PIPE.asStack()
            : new ItemStack(blockEntity.getBlockState().getBlock());
        writeSafe(item.getOrCreateTag()); return item;
    }
    public void install(CompoundTag source) {
        read(source, false);
        if (blockEntity instanceof BasinBlockEntity basin) FluidNativeRates.resizeBasin(basin);
        if (blockEntity instanceof FluidTankBlockEntity tank && tank.isController())
            tank.applyFluidTankSize(tank.getTotalTankSize());
        blockEntity.setChanged(); blockEntity.sendData();
        if (blockEntity instanceof KineticBlockEntity kinetic && kinetic.getLevel() != null
            && !kinetic.getLevel().isClientSide && kinetic.hasNetwork())
            kinetic.getOrCreateNetwork().updateStressFor(kinetic, kinetic.calculateStressApplied());
    }
    public static FluidVariantBehaviour get(Object block) {
        return block instanceof SmartBlockEntity smart ? BlockEntityBehaviour.get(smart, TYPE) : null;
    }
    public static boolean supports(Object block) {
        return block instanceof BasinBlockEntity || block instanceof FluidTankBlockEntity
            || block instanceof SpoutBlockEntity || block instanceof ItemDrainBlockEntity
            || block instanceof FluidPipeBlockEntity || block instanceof StraightPipeBlockEntity
            || block instanceof FluidValveBlockEntity || block instanceof HosePulleyBlockEntity
            || block instanceof PortableFluidInterfaceBlockEntity;
    }
    public static <T extends SmartBlockEntity> void attach(BlockEntityBehaviourEvent<T> event) {
        if (supports(event.getBlockEntity()) && get(event.getBlockEntity()) == null)
            event.attach(new FluidVariantBehaviour(event.getBlockEntity()));
    }
    public static void register() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addGenericListener(BasinBlockEntity.class, (BlockEntityBehaviourEvent<BasinBlockEntity> event) -> attach(event));
        bus.addGenericListener(FluidTankBlockEntity.class, (BlockEntityBehaviourEvent<FluidTankBlockEntity> event) -> attach(event));
        bus.addGenericListener(SpoutBlockEntity.class, (BlockEntityBehaviourEvent<SpoutBlockEntity> event) -> attach(event));
        bus.addGenericListener(ItemDrainBlockEntity.class, (BlockEntityBehaviourEvent<ItemDrainBlockEntity> event) -> attach(event));
        bus.addGenericListener(FluidPipeBlockEntity.class, (BlockEntityBehaviourEvent<FluidPipeBlockEntity> event) -> attach(event));
        bus.addGenericListener(StraightPipeBlockEntity.class, (BlockEntityBehaviourEvent<StraightPipeBlockEntity> event) -> attach(event));
        bus.addGenericListener(FluidValveBlockEntity.class, (BlockEntityBehaviourEvent<FluidValveBlockEntity> event) -> attach(event));
        bus.addGenericListener(HosePulleyBlockEntity.class, (BlockEntityBehaviourEvent<HosePulleyBlockEntity> event) -> attach(event));
        bus.addGenericListener(PortableFluidInterfaceBlockEntity.class, (BlockEntityBehaviourEvent<PortableFluidInterfaceBlockEntity> event) -> attach(event));
    }
}
