package com.bettercontent.manufacturing.integration.create;

import com.bettercontent.manufacturing.persistence.BoundedNbt;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared native Create persistence for fixed machine settings. No material evaluation at runtime. */
public final class KineticVariantBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<KineticVariantBehaviour> TYPE = new BehaviourType<>("better_manufacturing:variant");
    private Tag raw;
    private MachineSettings settings;
    private double fraction;
    public KineticVariantBehaviour(KineticBlockEntity block) { super(block); }
    @Override public BehaviourType<?> getType() { return TYPE; }
    public String outputId() { return ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString(); }
    @Override public void read(CompoundTag tag, boolean client) {
        raw = null;
        if (tag.contains(SettingsNbt.KEY)) {
            var offered = tag.get(SettingsNbt.KEY);
            try {
                BoundedNbt.check(offered);
                raw = offered.copy(); // Preserve bounded malformed/unknown versions for native drops.
            } catch (IllegalArgumentException oversized) {
                // Never recursively copy an oversized untrusted tag into native save/packet data.
            }
        }
        settings = SettingsNbt.read(tag, outputId());
        // A valid v1 envelope for another machine must never adjust this machine's native work.
        // Keep the original tag for drops and schematics; only its runtime interpretation falls back.
        var required = switch (outputId()) {
            case "create:encased_fan" -> java.util.Set.of("airflow");
            case "create:mechanical_pump" -> java.util.Set.of("pressure");
            case "create:crushing_wheel" -> java.util.Set.of("work_rate", "load");
            case "create:weighted_ejector" -> java.util.Set.of("recharge_rate", "launch_envelope");
            case "create:mechanical_saw", "create:mechanical_press", "create:millstone",
                 "create:mechanical_mixer", "createsifter:sifter", "createsifter:brass_sifter" -> java.util.Set.of("work_rate");
            default -> java.util.Set.<String>of();
        };
        if (settings != null && !required.isEmpty() && !settings.properties().keySet().equals(required)) settings = null;
        if (settings != null && required.stream().anyMatch(key -> key.equals("load") || key.equals("recharge_rate")
            || key.equals("launch_envelope")) && required.stream().anyMatch(key -> {
                double value = settings.properties().get(key);
                return value < .75 || value > 1.5;
            })) settings = null;
        double saved = tag.getDouble("better_manufacturing:work_fraction");
        fraction = Double.isFinite(saved) && saved >= 0 && saved < 1 ? saved : 0;
    }
    @Override public void write(CompoundTag tag, boolean client) {
        if (raw != null) tag.put(SettingsNbt.KEY, raw.copy());
        tag.putDouble("better_manufacturing:work_fraction", fraction);
    }
    @Override public boolean isSafeNBT() { return true; }
    @Override public void writeSafe(CompoundTag tag) { if (raw != null) tag.put(SettingsNbt.KEY, raw.copy()); }
    public MachineSettings settings() { return settings; }
    public double rate() { return settings == null ? 1 : settings.rate(); }
    public DrillEngineering.State cutter() { return DrillEngineering.fromSettings(settings); }
    public int advance(int nativeWork) {
        if (rate() == 1) return nativeWork;
        double work = nativeWork * rate() + fraction;
        int whole = (int) work; fraction = work - whole; blockEntity.setChanged(); return whole;
    }
    public ItemStack item() {
        var stack = new ItemStack(blockEntity.getBlockState().getBlock()); writeSafe(stack.getOrCreateTag()); return stack;
    }
    public void install(CompoundTag tag) {
        read(tag, false); blockEntity.setChanged(); blockEntity.sendData();
        var kinetic = (KineticBlockEntity) blockEntity;
        if (!kinetic.getLevel().isClientSide && kinetic.hasNetwork()) kinetic.getOrCreateNetwork().updateStressFor(kinetic, kinetic.calculateStressApplied());
        if (kinetic instanceof com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity fan) fan.blockInFrontChanged();
        if (kinetic instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity pump) pump.updatePressureChange();
    }
    public static KineticVariantBehaviour get(Object block) {
        return block instanceof KineticBlockEntity kinetic ? BlockEntityBehaviour.get(kinetic, TYPE) : null;
    }
    public static <T extends KineticBlockEntity> void attach(BlockEntityBehaviourEvent<T> event) {
        if (get(event.getBlockEntity()) == null) event.attach(new KineticVariantBehaviour(event.getBlockEntity()));
    }
    public static boolean supports(Object block) {
        return block instanceof com.simibubi.create.content.kinetics.saw.SawBlockEntity
            || block instanceof com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity
            || block instanceof com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity
            || block instanceof com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity
            || block instanceof com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity
            || block instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity
            || block instanceof com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity
            || block instanceof com.simibubi.create.content.logistics.depot.EjectorBlockEntity
            || block instanceof KineticBlockEntity kinetic && kinetic.getBlockState().getBlock().asItem() != net.minecraft.world.item.Items.AIR
                && java.util.Set.of("createsifter:sifter", "createsifter:brass_sifter").contains(String.valueOf(ForgeRegistries.ITEMS.getKey(kinetic.getBlockState().getBlock().asItem())));
    }
    /** Called only when the optional pinned Create Sifting integration is present. */
    public static void registerSifter() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addGenericListener(com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity.class,
            (BlockEntityBehaviourEvent<com.oierbravo.createsifter.content.contraptions.components.sifter.SifterBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.oierbravo.createsifter.content.contraptions.components.brasss_sifter.BrassSifterBlockEntity.class,
            (BlockEntityBehaviourEvent<com.oierbravo.createsifter.content.contraptions.components.brasss_sifter.BrassSifterBlockEntity> event) -> attach(event));
    }
    public static void register() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addGenericListener(com.simibubi.create.content.fluids.pump.PumpBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.fluids.pump.PumpBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.logistics.depot.EjectorBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.logistics.depot.EjectorBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.kinetics.saw.SawBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.kinetics.saw.SawBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity> event) -> attach(event));
        bus.addGenericListener(com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity.class, (BlockEntityBehaviourEvent<com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity> event) -> attach(event));
    }
}
