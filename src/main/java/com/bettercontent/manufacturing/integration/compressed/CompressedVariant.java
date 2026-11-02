package com.bettercontent.manufacturing.integration.compressed;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.BoundedNbt;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Native Compressed Creativity block-entity persistence. Carries only the final crafted settings. */
public final class CompressedVariant extends BlockEntityBehaviour {
    public static final BehaviourType<CompressedVariant> TYPE = new BehaviourType<>("better_manufacturing:compressedcreativity_variant");
    private Tag raw;
    private MachineSettings settings;
    public CompressedVariant(SmartBlockEntity block) { super(block); }
    @Override public BehaviourType<?> getType() { return TYPE; }
    public String outputId() { return ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString(); }
    @Override public void read(CompoundTag tag, boolean client) {
        raw = null;
        if (tag.contains(SettingsNbt.KEY)) {
            var offered = tag.get(SettingsNbt.KEY);
            try {
                BoundedNbt.check(offered);
                raw = offered.copy(); // Bounded malformed/unknown versions remain drop-safe.
            } catch (IllegalArgumentException oversized) {
                // Never copy unbounded settings into native save, packet or drops.
            }
        }
        settings = SettingsNbt.read(tag, outputId());
        // A valid v1 record for the wrong native role is still inert. Keep bounded
        // raw data for drops, but never operate a blower/tube/engine from a foreign
        // property or let a bracket raise its native PneumaticCraft pressure tier.
        String required = switch (outputId()) {
            case "compressedcreativity:air_blower", "compressedcreativity:industrial_air_blower" -> "airflow";
            case "compressedcreativity:rotational_compressor", "compressedcreativity:compressed_air_engine",
                 "compressedcreativity:heater" -> "conversion";
            case "compressedcreativity:bracketed_pressure_tube", "compressedcreativity:bracketed_reinforced_pressure_tube",
                 "compressedcreativity:bracketed_advanced_pressure_tube" -> "pressure_envelope";
            default -> null;
        };
        if (settings != null && (required == null || !settings.properties().keySet().equals(java.util.Set.of(required))
            || settings.properties().get(required) < .75 || settings.properties().get(required) > (required.equals("pressure_envelope") ? 1 : 1.5)))
            settings = null;
    }
    @Override public void write(CompoundTag tag, boolean client) {
        if (raw != null) tag.put(SettingsNbt.KEY, raw.copy());
        // CC's bracket loot yields a *native PNCR tube*. Export a derived native-ID
        // record only for an actually rated bracket; loot copies this conditional
        // field instead of seeding an empty v1 record on every unconfigured drop.
        final String dropKey = "better_manufacturing:pressure_tube_drop";
        tag.remove(dropKey);
        String nativeTube = switch (outputId()) {
            case "compressedcreativity:bracketed_pressure_tube" -> "pneumaticcraft:pressure_tube";
            case "compressedcreativity:bracketed_advanced_pressure_tube" -> "pneumaticcraft:advanced_pressure_tube";
            case "compressedcreativity:bracketed_reinforced_pressure_tube" -> "pneumaticcraft:reinforced_pressure_tube";
            default -> null;
        };
        if (!client && nativeTube != null && settings != null && settings.properties().containsKey("pressure_envelope")) {
            var converted = new CompoundTag();
            SettingsNbt.write(converted, new MachineSettings(nativeTube, settings.properties(), settings.materials(), null));
            tag.put(dropKey, converted.getCompound(SettingsNbt.KEY));
        }
    }
    @Override public void writeSafe(CompoundTag tag) { if (raw != null) tag.put(SettingsNbt.KEY, raw.copy()); }
    @Override public boolean isSafeNBT() { return true; }
    public MachineSettings settings() { return settings; }
    public double property(String name) {
        if (settings == null) return 1;
        double value = settings.properties().getOrDefault(name, 1.0);
        return Double.isFinite(value) && value >= .75 && value <= 1.5 ? value : 1;
    }
    public ItemStack item() {
        var result = new ItemStack(blockEntity.getBlockState().getBlock());
        writeSafe(result.getOrCreateTag());
        return result;
    }
    public void install(CompoundTag tag) {
        read(tag, false); blockEntity.setChanged(); blockEntity.sendData();
        // AirCurrent and kinetically generated outputs have native update cycles; never inject free work here.
        if (blockEntity instanceof com.lgmrszd.compressedcreativity.blocks.air_blower.AirBlowerBlockEntity blower)
            blower.airCurrent.rebuild();
        if (blockEntity instanceof com.lgmrszd.compressedcreativity.blocks.rotational_compressor.RotationalCompressorBlockEntity compressor) {
            compressor.onSpeedChanged(compressor.getSpeed());
            if (compressor.getLevel() != null && !compressor.getLevel().isClientSide && compressor.hasNetwork())
                compressor.getOrCreateNetwork().updateStressFor(compressor, compressor.calculateStressApplied());
        }
        if (blockEntity instanceof com.lgmrszd.compressedcreativity.blocks.compressed_air_engine.CompressedAirEngineBlockEntity engine)
            engine.updateGeneratedRotation();
    }
    public static CompressedVariant get(Object object) {
        return object instanceof SmartBlockEntity block ? BlockEntityBehaviour.get(block, TYPE) : null;
    }
    private static <T extends SmartBlockEntity> void attach(BlockEntityBehaviourEvent<T> event) {
        if (get(event.getBlockEntity()) == null) event.attach(new CompressedVariant(event.getBlockEntity()));
    }
    /** Invoked only when Compressed Creativity is loaded; no hard dependency on absent mods. */
    public static void register() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.air_blower.AirBlowerBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.air_blower.AirBlowerBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.advanced_air_blower.AdvancedAirBlowerBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.advanced_air_blower.AdvancedAirBlowerBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedPressureTubeBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedPressureTubeBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedAdvancedPressureTubeBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedAdvancedPressureTubeBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedReinforcedPressureTubeBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.bracketed_pressure_tube.BracketedReinforcedPressureTubeBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.rotational_compressor.RotationalCompressorBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.rotational_compressor.RotationalCompressorBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.compressed_air_engine.CompressedAirEngineBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.compressed_air_engine.CompressedAirEngineBlockEntity> e) -> attach(e));
        bus.addGenericListener(com.lgmrszd.compressedcreativity.blocks.heater.HeaterBlockEntity.class,
            (BlockEntityBehaviourEvent<com.lgmrszd.compressedcreativity.blocks.heater.HeaterBlockEntity> e) -> attach(e));
    }
    public static void install(Level level, BlockPos pos, ItemStack source) {
        if (!source.hasTag() || !source.getTag().contains(SettingsNbt.KEY)) return;
        var block = level.getBlockEntity(pos);
        if (!(block instanceof SmartBlockEntity smart) || !outputIsSupported(block)) return;
        if (smart.getBlockState().getBlock().asItem() != source.getItem()) return;
        var behaviour = get(smart);
        if (behaviour == null) { behaviour = new CompressedVariant(smart); smart.attachBehaviourLate(behaviour); }
        behaviour.install(source.getTag());
    }
    private static boolean outputIsSupported(net.minecraft.world.level.block.entity.BlockEntity block) {
        var id = ForgeRegistries.BLOCKS.getKey(block.getBlockState().getBlock());
        return id != null && id.getNamespace().equals("compressedcreativity");
    }
}
