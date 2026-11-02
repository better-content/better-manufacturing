package com.bettercontent.manufacturing.integration.industrialheat;

import com.bettercontent.betterindustrialheat.content.heat.ThermalSurfaceLoadedEvent;
import com.bettercontent.betterindustrialheat.content.heat.ThermalSurfaceMachine;
import com.bettercontent.betterindustrialheat.content.heat.ThermalSurfacePlacedEvent;
import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;

/** Registered only when better_industrial_heat is loaded. Native BEs never link Manufacturing. */
public final class IndustrialHeatBridge {
    private IndustrialHeatBridge() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(IndustrialHeatBridge::placed);
        MinecraftForge.EVENT_BUS.addListener(IndustrialHeatBridge::loaded);
    }

    private static void placed(ThermalSurfacePlacedEvent event) {
        CompoundTag itemTag = event.getStack().getTag();
        // Vanilla BlockItem may already have installed a BlockEntityTag (schematics/commands).
        // Prefer explicit item-root settings; otherwise retain a validated BE record.
        CompoundTag holder = itemTag != null && itemTag.contains(SettingsNbt.KEY)
            ? itemTag : event.getMachine().storedSurfaceTag();
        install(event.getMachine(), holder);
    }

    private static void loaded(ThermalSurfaceLoadedEvent event) {
        install(event.getMachine(), event.getMachine().storedSurfaceTag());
    }

    private static void install(ThermalSurfaceMachine machine, CompoundTag holder) {
        MachineSettings settings = SettingsNbt.read(holder, machine.getThermalOutput());
        if (settings == null) { machine.resetSurface(); return; }
        Double transfer = settings.properties().get("heat_transfer");
        Double envelope = settings.properties().get("thermal_envelope");
        if (transfer == null || envelope == null || transfer < .75 || transfer > 1.5 ||
            envelope < .75 || envelope > 1.5 || settings.harvestTier() != null) {
            machine.resetSurface();
            return;
        }
        // Pass only the validated codec record; no incidental item NBT or part provenance.
        CompoundTag encoded = new CompoundTag();
        SettingsNbt.write(encoded, settings);
        machine.applySurface(transfer, envelope, encoded);
    }
}
