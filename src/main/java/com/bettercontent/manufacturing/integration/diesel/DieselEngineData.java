package com.bettercontent.manufacturing.integration.diesel;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.jesz.createdieselgenerators.content.diesel_engine.EngineUpgrades;
import com.jesz.createdieselgenerators.content.diesel_engine.IEngine;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Only final settings live on the installed engine, never parts or a subassembly graph. */
public interface DieselEngineData {
    String TURBO_KEY = "better_manufacturing:diesel_turbo";
    MachineSettings manufacturing$engine();
    MachineSettings manufacturing$turbo();
    void manufacturing$setEngine(MachineSettings settings);
    void manufacturing$setTurbo(MachineSettings settings);

    static double capacity(IEngine engine) {
        if (!(engine instanceof DieselEngineData data)) return 1;
        var settings = data.manufacturing$engine();
        // Conversion, working load and thermal tolerance are independent bottlenecks on shaft load.
        // No geometry or native fuel type is changed; every increase in load costs proportionate fuel.
        double rated = Math.min(DieselSettings.value(settings, "conversion"),
            Math.min(DieselSettings.value(settings, "load"), DieselSettings.value(settings, "thermal_envelope")));
        if (engine.getUpgrade() == EngineUpgrades.TURBOCHARGER) {
            var turbo = data.manufacturing$turbo();
            rated = Math.min(1.5, rated * Math.min(DieselSettings.value(turbo, "conversion"),
                Math.min(DieselSettings.value(turbo, "load"), DieselSettings.value(turbo, "thermal_envelope"))));
        }
        return Math.max(.75, rated);
    }
    static double burn(IEngine engine) {
        // Fuel-specific native speed and burn coefficient remain authoritative. Rated capacity and fuel
        // consumption change in lockstep, so material selection cannot generate extra free shaft energy.
        return capacity(engine);
    }
    /** Called only by Create's safe-NBT schematic bridge; native upgrade identity is needed by CDG read(). */
    static void writeSchematic(CompoundTag tag, IEngine engine) {
        if (!(engine instanceof DieselEngineData data)) return;
        if (data.manufacturing$engine() != null) SettingsNbt.write(tag, data.manufacturing$engine());
        // CDG writes native Upgrade only on a modular controller, never on its segments.
        boolean writesUpgrade = !(engine instanceof ModularDieselEngineBlockEntity modular) || modular.isController();
        if (!writesUpgrade || engine.getUpgrade() == null) return;
        if (data.manufacturing$turbo() != null && engine.getUpgrade() == EngineUpgrades.TURBOCHARGER)
            tag.put(TURBO_KEY, addonTag(data.manufacturing$turbo()));
        tag.putString("Upgrade", engine.getUpgrade().getId().toString());
    }
    static MachineSettings addon(ItemStack stack) { return SettingsNbt.read(stack.getTag(), DieselSettings.TURBO); }
    static CompoundTag addonTag(MachineSettings settings) {
        var tag = new CompoundTag();
        if (settings != null) SettingsNbt.write(tag, settings);
        return tag;
    }
}
