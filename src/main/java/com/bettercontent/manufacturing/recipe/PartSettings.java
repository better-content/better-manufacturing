package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.MachineSettings;
import com.bettercontent.manufacturing.persistence.SettingsNbt;
import com.bettercontent.manufacturing.tconstruct.NativePartRates;
import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import java.util.*;

/** Used only during recipe calculation. Runtime machines never consult material data. */
public final class PartSettings {
    private PartSettings() {}
    public static MachineSettings bake(String output, String function, Map<String, ItemStack> inputs) {
        // Family recipes share the shaped-result codec; each owner validates its own
        // output, part roles and native property envelope. No runtime material lookup.
        switch (function) {
            case "better_manufacturing:sifter/machine": return com.bettercontent.manufacturing.integration.sifter.SifterFrameSettings.bake(output, function, inputs);
            case "better_manufacturing:create/field_work", "better_manufacturing:create/rolling": return com.bettercontent.manufacturing.integration.createactors.CreateActorsPartSettings.bake(output, function, inputs);
            case "better_manufacturing:createbulk/crushing_wheel", "better_manufacturing:createbulk/weighted_ejector", "better_manufacturing:createbulk/steam_engine": return com.bettercontent.manufacturing.integration.createbulk.BulkPartSettings.bake(output, function, inputs);
            case "better_manufacturing:create/fluid_envelope", "better_manufacturing:create/fluid_transfer": return com.bettercontent.manufacturing.integration.createfluids.FluidPartSettings.bake(output, function, inputs);
            case "better_manufacturing:industrialheat/thermal": return com.bettercontent.manufacturing.integration.industrialheat.IndustrialHeatSettings.bake(output, function, inputs);
            case "better_manufacturing:rail_beetle/drives", "better_manufacturing:rail_beetle/modules": return com.bettercontent.manufacturing.integration.railbeetle.RailBeetlePartSettings.bake(output, function, inputs);
            case "better_manufacturing:pneupressure/compression", "better_manufacturing:pneupressure/pressure", "better_manufacturing:pneupressure/rotation", "better_manufacturing:pneupressure/valve_blank": return com.bettercontent.manufacturing.integration.pneupressure.PneupressureSettings.bake(output, function, inputs);
            case "better_manufacturing:pneuprocess/chemistry", "better_manufacturing:pneuprocess/vacuum_thermal": return com.bettercontent.manufacturing.integration.pneuprocess.PneuProcessSettings.bake(output, function, inputs);
            case "better_manufacturing:pneumaticcraft/drilling": return com.bettercontent.manufacturing.integration.pneumatictools.PneumatictoolsSettings.bake(output, function, inputs);
            case "better_manufacturing:pneumaticcraft/fluid_transfer": return com.bettercontent.manufacturing.integration.pneutransfer.PneuTransferSettings.bake(output, function, inputs);
            case "better_manufacturing:compressedcreativity/blower", "better_manufacturing:compressedcreativity/mesh", "better_manufacturing:compressedcreativity/conversion", "better_manufacturing:compressedcreativity/pressure_tube": return com.bettercontent.manufacturing.integration.compressed.CompressedSettings.bake(output, function, inputs);
            case "better_manufacturing:createdieselgenerators/engines": return com.bettercontent.manufacturing.integration.diesel.DieselSettings.bake(output, function, inputs);
            case "better_manufacturing:powergrid/heating", "better_manufacturing:powergrid/motors", "better_manufacturing:powergrid/generation": return com.bettercontent.manufacturing.integration.powergrid.PowergridPartSettings.bake(output, function, inputs);
            case "better_manufacturing:ae2/precision", "better_manufacturing:alexscaves/quarry_work", "better_manufacturing:rats/quarry_work": return com.bettercontent.manufacturing.integration.precisionquarry.PrecisionQuarrySettings.bake(output, function, inputs);
            case "better_manufacturing:bees/separation", "better_manufacturing:bees/gyrofuge/speed", "better_manufacturing:bees/gyrofuge/efficiency", "better_manufacturing:bees/gyrofuge/extraction", "better_manufacturing:bees/gyrofuge/processing": return com.bettercontent.manufacturing.integration.bees.BeesPartSettings.bake(output, function, inputs);
            case "better_manufacturing:sophisticatedstorage/hopper": return com.bettercontent.manufacturing.integration.sophisticatedstorage.HopperEngineering.bake(output, function, inputs);
            case "better_manufacturing:prettypipes/extraction", "better_manufacturing:prettypipes/speed", "better_manufacturing:prettypipes/pressurizer": return com.bettercontent.manufacturing.recipe.PrettyPipesSettings.bake(output, function, inputs);
            case "better_manufacturing:littlelogistics/propulsion", "better_manufacturing:littlelogistics/transfer": return com.bettercontent.manufacturing.integration.littlelogistics.LittleLogisticsSettings.bake(output, function, inputs);
            case "better_manufacturing:trackwork/running_gear": return com.bettercontent.manufacturing.integration.trackwork.TrackworkSettings.bake(output, function, inputs);
            case "better_manufacturing:clockwork/gas_thermal", "better_manufacturing:clockwork/flight_actuators",
                "better_manufacturing:clockwork/reactionwheel_prep": return com.bettercontent.manufacturing.integration.clockwork.ClockworkPartSettings.bake(output, function, inputs);
            case "better_manufacturing:eureka/engine": return com.bettercontent.manufacturing.integration.eureka.EurekaEngineSettings.bake(output, function, inputs);
            default: break;
        }
        boolean cutter = function.equals("better_manufacturing:create/cutting") || function.equals("better_manufacturing:create/sawing");
        boolean screen = function.equals("better_manufacturing:sifter/frame");
        boolean airflow = function.equals("better_manufacturing:create/airflow");
        boolean fourParts = screen || (airflow && output.equals("create:propeller"));
        // Native head mining speed describes working plates and blades; handle mining speed
        // describes motion throughput, while handle durability supplies pump pressure.
        Measure measure = switch (function) {
            case "better_manufacturing:sifter/frame", "better_manufacturing:create/mixing" -> Measure.HANDLE_MINING_SPEED;
            case "better_manufacturing:create/pumping" -> Measure.HANDLE_DURABILITY;
            case "better_manufacturing:create/pressing", "better_manufacturing:create/milling",
                "better_manufacturing:create/airflow" -> Measure.HEAD_MINING_SPEED;
            default -> null;
        };
        if (!cutter && measure == null) throw new IllegalArgumentException("Unsupported variant function");
        if (!MaterialRegistry.isFullyLoaded()) throw new IllegalArgumentException("Material data not loaded");
        if ((!fourParts && inputs.size() != 1) || (fourParts && !inputs.keySet().equals(Set.of("north", "east", "south", "west"))))
            throw new IllegalArgumentException("Wrong variant parts");
        var labels = new LinkedHashMap<String, String>(); double rate = 1.5; String tier = null;
        var registry = MaterialRegistry.getInstance();
        for (var entry : inputs.entrySet()) {
            var stack = entry.getValue();
            var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) throw new IllegalArgumentException("Unknown ingredient");
            // A configured native component contributes its already baked properties, never its original parts.
            if (!(stack.getItem() instanceof ToolPartItem part)) {
                var tag = stack.getTag();
                var settings = SettingsNbt.read(tag, itemId.toString());
                if (!(function.equals("better_manufacturing:create/mixing") && itemId.toString().equals("create:whisk"))
                    && !(airflow && itemId.toString().equals("create:propeller")))
                    throw new IllegalArgumentException("Not a compatible configured component");
                // Only a genuinely unconfigured native part uses the baseline. A malformed,
                // oversized or wrong-output record cannot be laundered into a newly baked machine.
                if (settings == null && tag != null && tag.contains(SettingsNbt.KEY))
                    throw new IllegalArgumentException("Invalid configured component settings");
                // A configured whisk/propeller must carry its own native work property;
                // otherwise a valid v1 envelope for another function could be laundered into a new craft.
                if (settings != null && !settings.properties().keySet().equals(Set.of(airflow ? "airflow" : "work_rate")))
                    throw new IllegalArgumentException("Wrong configured component rating");
                rate = Math.min(rate, settings == null ? 1 : settings.rate());
                if (settings == null) labels.put(entry.getKey(), itemId.toString());
                else settings.materials().forEach((role, label) -> labels.put(entry.getKey() + "_" + role, label));
                continue;
            }
            var material = part.getMaterial(stack);
            if (!part.canUseMaterial(material.getId())) throw new IllegalArgumentException("Part cannot use material");
            String materialId = material.toString(); labels.put(entry.getKey(), materialId);
            if (cutter) {
                var required = function.endsWith("sawing") ? Set.of("tconstruct:small_blade", "tconstruct:broad_blade") : Set.of("tconstruct:pick_head");
                if (!required.contains(itemId.toString())) throw new IllegalArgumentException("Wrong cutter shape");
                var stats = registry.<HeadMaterialStats>getMaterialStats(material.getId(), HeadMaterialStats.ID).orElseThrow(() -> new IllegalArgumentException("No head stats"));
                rate = Math.min(rate, NativePartRates.relative(part, stack, Measure.HEAD_MINING_SPEED));
                var name = TierSortingRegistry.getName(stats.tier());
                if (name == null) throw new IllegalArgumentException("Unknown native tier");
                tier = name.toString();
            } else {
                String required = airflow ? "tconstruct:small_blade" : screen || function.endsWith("mixing") || function.endsWith("pumping") ? "tconstruct:tool_handle" : "tconstruct:large_plate";
                if (!itemId.toString().equals(required)) throw new IllegalArgumentException("Wrong working part shape");
                rate = Math.min(rate, NativePartRates.relative(part, stack, measure));
            }
        }
        return new MachineSettings(output, Map.of(airflow ? "airflow" : function.endsWith("pumping") ? "pressure" : "work_rate", rate), labels, tier);
    }
}
