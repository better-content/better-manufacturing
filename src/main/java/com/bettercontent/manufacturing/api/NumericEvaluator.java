package com.bettercontent.manufacturing.api;

import com.bettercontent.manufacturing.tconstruct.NativePartRates.Measure;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure replay of authored crafting formulas from captured native-stat pairs and component ratings. */
public final class NumericEvaluator {
    private NumericEvaluator() {}
    private static final Measure HS = Measure.HEAD_MINING_SPEED, HD = Measure.HEAD_DURABILITY;
    private static final Measure MS = Measure.HANDLE_MINING_SPEED, MD = Measure.HANDLE_DURABILITY;
    private static final String PREFIX = "better_manufacturing:";

    public static Map<String, Double> evaluate(String output, NumericInputs inputs, String harvestTier) {
        if (output == null || inputs == null) throw new IllegalArgumentException("Missing numeric build");
        String function = inputs.function();
        if (!function.startsWith(PREFIX)) throw new IllegalArgumentException("Unknown numeric function");
        Map<String, Double> result = switch (function.substring(PREFIX.length())) {
            case "sifter/machine" -> sifter(output, inputs);
            case "create/field_work", "create/rolling" -> actor(output, inputs);
            case "createbulk/crushing_wheel", "createbulk/weighted_ejector", "createbulk/steam_engine" -> bulk(output, inputs);
            case "create/fluid_envelope", "create/fluid_transfer" -> fluids(output, inputs);
            case "industrialheat/thermal" -> thermal(output, inputs);
            case "rail_beetle/drives", "rail_beetle/modules" -> rail(output, inputs);
            case "pneupressure/compression", "pneupressure/pressure", "pneupressure/rotation", "pneupressure/valve_blank" -> pressure(output, inputs);
            case "pneuprocess/chemistry", "pneuprocess/vacuum_thermal" -> process(output, inputs);
            case "pneumaticcraft/drilling" -> drilling(output, inputs);
            case "pneumaticcraft/fluid_transfer" -> single(output, inputs, "wetted_fitting", HS, "transfer_rate",
                Set.of("pneumaticcraft:gas_lift", "pneumaticcraft:small_tank", "pneumaticcraft:medium_tank", "pneumaticcraft:large_tank", "pneumaticcraft:huge_tank", "pneumaticcraft:liquid_hopper", "pneumaticcraft:omnidirectional_hopper"));
            case "compressedcreativity/blower", "compressedcreativity/mesh", "compressedcreativity/conversion", "compressedcreativity/pressure_tube" -> compressed(output, inputs);
            case "createdieselgenerators/engines" -> diesel(output, inputs);
            case "powergrid/heating", "powergrid/motors", "powergrid/generation" -> powergrid(output, inputs);
            case "ae2/precision", "alexscaves/quarry_work", "rats/quarry_work" -> precision(output, inputs);
            case "bees/separation", "bees/gyrofuge/speed", "bees/gyrofuge/efficiency", "bees/gyrofuge/extraction", "bees/gyrofuge/processing" -> bees(output, inputs);
            case "sophisticatedstorage/hopper" -> single(output, inputs, "actuator", MS, "transfer_rate", Set.of("sophisticatedstorage:hopper_upgrade", "sophisticatedstorage:advanced_hopper_upgrade"));
            case "prettypipes/extraction", "prettypipes/speed", "prettypipes/pressurizer" -> pretty(output, inputs);
            case "littlelogistics/propulsion", "littlelogistics/transfer" -> logistics(output, inputs);
            case "trackwork/running_gear" -> trackwork(output, inputs);
            case "clockwork/gas_thermal", "clockwork/flight_actuators", "clockwork/reactionwheel_prep" -> clockwork(output, inputs);
            case "clockwork/item_application_gas_engine", "clockwork/item_application_reactionwheel" -> clockworkCopy(output, inputs);
            case "inherit/pressure_envelope", "inherit/airflow", "inherit/compressor_upgrade", "inherit/actuator_response", "inherit/generator_ratio" -> inherit(output, inputs);
            case "eureka/engine" -> single(output, inputs, "working_assembly", MS, "drive_performance", Set.of("vs_eureka:engine"));
            case "create/cutting", "create/sawing", "sifter/frame", "create/airflow", "create/mixing", "create/pumping", "create/pressing", "create/milling" -> base(output, inputs);
            default -> throw new IllegalArgumentException("Unsupported numeric function: " + function);
        };
        if (output.equals("vs_eureka:engine")) result = Map.of("drive_performance", result.get("drive_performance"), "consumption", result.get("drive_performance"));
        boolean tier = function.equals(PREFIX + "pneumaticcraft/drilling") || function.equals(PREFIX + "rats/quarry_work")
            || function.equals(PREFIX + "create/cutting") || function.equals(PREFIX + "create/sawing");
        if (tier != (harvestTier != null) || tier && harvestTier.isBlank())
            throw new IllegalArgumentException("Wrong harvest-tier evidence");
        return result;
    }

    /** Native conversions preserve an already baked bounded scalar; blade assembly weights by native segment length. */
    private static Map<String,Double> inherit(String o, NumericInputs n) {
        return switch (n.function().substring((PREFIX + "inherit/").length())) {
            case "pressure_envelope" -> {
                output(o, Set.of("pneumaticcraft:pressure_chamber_valve", "pneumaticcraft:pressure_tube",
                    "pneumaticcraft:advanced_pressure_tube", "pneumaticcraft:reinforced_pressure_tube",
                    "compressedcreativity:bracketed_pressure_tube", "compressedcreativity:bracketed_reinforced_pressure_tube",
                    "compressedcreativity:bracketed_advanced_pressure_tube"));
                shape(n, Set.of(), Map.of("source", "pressure_envelope"));
                if (!n.components().get("source").keySet().equals(Set.of("pressure_envelope")))
                    throw new IllegalArgumentException("Missing configured pressure source");
                double envelope = component(n, "source", "pressure_envelope");
                if (envelope > 1) throw new IllegalArgumentException("Inherited pressure exceeds native tier");
                yield Map.of("pressure_envelope", envelope);
            }
            case "airflow" -> {
                output(o, Set.of("compressedcreativity:mesh_woven", "compressedcreativity:incomplete_mesh_splashing",
                    "compressedcreativity:mesh_splashing", "compressedcreativity:mesh_splashing_frozen"));
                shape(n, Set.of(), Map.of("source", "airflow"));
                if (!n.components().get("source").keySet().equals(Set.of("airflow")))
                    throw new IllegalArgumentException("Missing configured airflow source");
                yield Map.of("airflow", component(n, "source", "airflow"));
            }
            case "generator_ratio" -> {
                output(o, Set.of("powergrid:copper_coil"));
                shape(n, Set.of(), Map.of("source", "generator_ratio"));
                if (!n.components().get("source").keySet().equals(Set.of("generator_ratio")))
                    throw new IllegalArgumentException("Missing captured winding ratio");
                yield Map.of("generator_ratio", component(n, "source", "generator_ratio"));
            }
            case "compressor_upgrade" -> {
                output(o, Set.of("pneumaticcraft:advanced_air_compressor", "pneumaticcraft:advanced_liquid_compressor"));
                if (!n.parts().isEmpty() || !n.components().keySet().equals(Set.of("source"))
                    || !n.components().get("source").keySet().equals(Set.of("compression_rate", "pressure_envelope")))
                    throw new IllegalArgumentException("Wrong upgraded compressor source");
                double rate = component(n, "source", "compression_rate");
                double envelope = component(n, "source", "pressure_envelope");
                if (envelope > 1) throw new IllegalArgumentException("Inherited pressure exceeds native tier");
                yield Map.of("compression_rate", rate, "pressure_envelope", envelope);
            }
            case "actuator_response" -> {
                output(o, Set.of("vs_clockwork:propeller_blade", "vs_clockwork:wide_propeller_blade"));
                if (!n.parts().isEmpty() || n.components().isEmpty())
                    throw new IllegalArgumentException("Missing assembled blade segments");
                double weighted = 0, length = 0;
                for (var entry : n.components().entrySet()) {
                    String role = entry.getKey();
                    if (!role.matches("blade_[0-8]") || !entry.getValue().keySet().equals(Set.of("actuator_response", "blade_length")))
                        throw new IllegalArgumentException("Wrong assembled blade segment: " + role);
                    double segment = entry.getValue().get("blade_length");
                    if (!Double.isFinite(segment) || segment <= 0)
                        throw new IllegalArgumentException("Invalid native blade segment length");
                    weighted += segment * component(n, role, "actuator_response");
                    length += segment;
                }
                if (!Double.isFinite(weighted) || !Double.isFinite(length) || length <= 0)
                    throw new IllegalArgumentException("Invalid assembled blade length");
                yield Map.of("actuator_response", bound(weighted / length));
            }
            default -> throw new IllegalArgumentException("Unsupported numeric inheritance function");
        };
    }

    private static void shape(NumericInputs n, Set<String> parts, Map<String, String> components) {
        if (!n.parts().keySet().equals(parts) || !n.components().keySet().equals(components.keySet()))
            throw new IllegalArgumentException("Wrong numeric input roles");
        components.forEach((role, key) -> {
            if (!n.components().get(role).keySet().equals(Set.of(key)) && !n.components().get(role).equals(Map.of("baseline", 1.0)))
                throw new IllegalArgumentException("Wrong inherited component property: " + role);
            component(n, role, key);
        });
    }
    private static void shape(NumericInputs n, String... parts) { shape(n, Set.of(parts), Map.of()); }
    private static double r(NumericInputs n, String role, Measure measure) { return n.rate(role, measure); }
    private static double component(NumericInputs n, String role, String property) {
        double rate = n.component(role, property);
        if (!Double.isFinite(rate) || rate < .75 || rate > 1.5)
            throw new IllegalArgumentException("Invalid inherited rating");
        return rate;
    }
    private static double bound(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite computed rating");
        return Math.max(.75, Math.min(1.5, value));
    }
    private static void output(String output, Set<String> allowed) {
        if (!allowed.contains(output)) throw new IllegalArgumentException("Wrong output for numeric function: " + output);
    }
    private static Map<String,Double> single(String output, NumericInputs n, String role, Measure measure, String key, Set<String> allowed) {
        output(output, allowed); shape(n, role); return Map.of(key, r(n, role, measure));
    }
    private static Map<String,Double> sifter(String o, NumericInputs n) {
        output(o, Set.of("createsifter:sifter", "createsifter:brass_sifter"));
        boolean brass = o.equals("createsifter:brass_sifter");
        shape(n, Set.of("left", "right"), brass ? Map.of("core", "work_rate") : Map.of());
        double predecessor = brass ? component(n, "core", "work_rate") : 0;
        return Map.of("work_rate", bound((r(n,"left",MS) + r(n,"right",MS) + predecessor)/(brass ? 3 : 2)));
    }
    private static Map<String,Double> actor(String o, NumericInputs n) {
        output(o, n.function().endsWith("rolling") ? Set.of("create:mechanical_roller") : Set.of("create:mechanical_plough"));
        shape(n, "working_surface"); return Map.of("work_rate", r(n,"working_surface",HS));
    }
    private static Map<String,Double> bulk(String o, NumericInputs n) {
        if (n.function().endsWith("crushing_wheel")) {
            output(o, Set.of("create:crushing_wheel")); shape(n,"hammer","plate");
            double hammer=r(n,"hammer",HS), plate=r(n,"plate",HD);
            return Map.of("work_rate",Math.min(hammer,plate),"load",Math.max(hammer,plate));
        }
        if (n.function().endsWith("weighted_ejector")) {
            output(o, Set.of("create:weighted_ejector")); shape(n,"spring");
            return Map.of("recharge_rate",r(n,"spring",Measure.LIMB_DRAW_SPEED),"launch_envelope",r(n,"spring",Measure.LIMB_VELOCITY));
        }
        output(o, Set.of("create:steam_engine")); shape(n,"piston_rod");
        return Map.of("conversion",r(n,"piston_rod",MS),"mechanical_limit",r(n,"piston_rod",MD));
    }
    private static Map<String,Double> fluids(String o, NumericInputs n) {
        boolean envelope = n.function().endsWith("fluid_envelope");
        output(o,envelope ? Set.of("create:basin","create:fluid_pipe","create:fluid_tank","create:fluid_valve","create:item_drain","create:spout")
            : Set.of("create:hose_pulley","create:portable_fluid_interface"));
        String role=envelope ? "liner" : "wetted_fitting"; shape(n,role);
        boolean capacity=o.equals("create:basin") || o.equals("create:fluid_tank");
        double rate=r(n,role,capacity ? HD : HS);
        return Map.of(capacity ? "fluid_capacity" : "fluid_rate", capacity ? Math.max(1,rate) : o.equals("create:fluid_pipe") ? Math.min(1,rate) : rate);
    }
    private static Map<String,Double> thermal(String o, NumericInputs n) {
        output(o,Set.of("better_industrial_heat:boiler_heater","better_industrial_heat:coolant_exchanger","better_industrial_heat:heat_pipe","better_industrial_heat:thermal_firebox"));
        shape(n,"thermal_surface"); return Map.of("heat_transfer",r(n,"thermal_surface",HS),"thermal_envelope",r(n,"thermal_surface",HD));
    }
    private static Map<String,Double> rail(String o, NumericInputs n) {
        if (!o.startsWith("better_rail_beetle:")) throw new IllegalArgumentException("Wrong rail output");
        String item=o.substring("better_rail_beetle:".length()); String property;
        if (Set.of("better_rail_beetle","steam_drive","flux_traction_motor","source_impeller","lifeforce_ram","pneumatic_expansion_motor","soul_combustor","spirit_warping_engine").contains(item)) property="drive_performance";
        else {
            String stem=item.replaceAll("_[12]$", "");
            property=switch(stem) {
                case "high_speed_governor" -> "speed_limit";
                case "exhaust_recuperator" -> "recovery";
                case "brake_manifold" -> "deceleration";
                case "adhesion_sanders" -> "adhesion_delivery";
                case "compound_torque_clutch" -> "torque_transfer";
                case "reinforced_drawgear" -> "towing_limit";
                case "trestle_erector" -> "work_rate";
                default -> throw new IllegalArgumentException("Wrong rail module");
            };
            if (!item.matches(".*_[12]")) throw new IllegalArgumentException("Wrong rail module tier");
        }
        if (property.equals("drive_performance") != n.function().endsWith("/drives")) throw new IllegalArgumentException("Wrong rail family");
        shape(n,"drive_shaft","working_surface");
        boolean speed=Set.of("drive_performance","speed_limit","work_rate","recovery","adhesion_delivery").contains(property);
        return Map.of(property,bound(.45*r(n,"drive_shaft",speed ? MS : MD)+.55*r(n,"working_surface",speed ? HS : HD)));
    }
    private static Map<String,Double> pressure(String o, NumericInputs n) {
        String f=n.function();
        if (f.endsWith("valve_blank")) {
            output(o,Set.of("better_manufacturing:compressed_iron_valve_blank")); shape(n,"body");
            return Map.of("pressure_envelope",Math.min(1,r(n,"body",HD)));
        }
        if (!o.startsWith("pneumaticcraft:")) throw new IllegalArgumentException("Wrong pressure output");
        String item=o.substring("pneumaticcraft:".length());
        boolean compressor=f.endsWith("/compression"), body=f.endsWith("/pressure"), rotor=f.endsWith("/rotation");
        output(o, compressor ? prefix("pneumaticcraft:","air_compressor","manual_compressor","liquid_compressor","flux_compressor","electrostatic_compressor","solar_compressor","thermal_compressor")
            : body ? prefix("pneumaticcraft:","pressure_tube","tube_junction","pressure_chamber_wall","pressure_chamber_glass","pressure_chamber_valve","pressure_chamber_interface")
            : prefix("pneumaticcraft:","turbine_blade","turbine_rotor","pneumatic_cylinder"));
        if (body) { shape(n,"body"); return Map.of("pressure_envelope",Math.min(1,r(n,"body",HD))); }
        if (compressor) {
            boolean withRotor=item.equals("flux_compressor") || item.equals("electrostatic_compressor");
            shape(n,Set.of("handle","body"),withRotor ? Map.of("rotor","rotation_rate") : Map.of());
            double product=r(n,"body",HS)*r(n,"handle",MS);
            // A consumed stock rotor has only the captured baseline marker, not a
            // rated rotation setting. Native PNCR uses two-part sqrt in that case;
            // only a configured rotor supplies a third, cbrt-weighted response.
            boolean ratedRotor=withRotor && n.components().get("rotor").containsKey("rotation_rate");
            double rate=bound(ratedRotor ? Math.cbrt(product*component(n,"rotor","rotation_rate")) : Math.sqrt(product));
            if (item.equals("manual_compressor")) {
                int interval=Math.max(3,Math.min(5,(int)Math.round(4/rate)));
                rate=4.0/interval;
            }
            return Map.of("compression_rate",rate,"pressure_envelope",Math.min(1,r(n,"body",HD)));
        }
        if (!rotor) throw new IllegalArgumentException("Wrong pressure family");
        boolean turbine=item.equals("turbine_rotor");
        shape(n,Set.of("body","handle"),turbine ? Map.of("blade_south","rotation_rate","blade_east","rotation_rate") : Map.of());
        double product=r(n,"body",HS)*r(n,"handle",MS);
        double rate=turbine ? Math.sqrt(Math.sqrt(product*component(n,"blade_south","rotation_rate")*component(n,"blade_east","rotation_rate"))) : Math.sqrt(product);
        return Map.of("rotation_rate",bound(rate));
    }
    private static Map<String,Double> process(String o, NumericInputs n) {
        boolean chemistry=n.function().endsWith("/chemistry");
        output(o,chemistry ? Set.of("pneumaticcraft:etching_tank","pneumaticcraft:fluid_mixer","pneumaticcraft:refinery","pneumaticcraft:refinery_output","pneumaticcraft:thermopneumatic_processing_plant")
            : Set.of("pneumaticcraft:heat_pipe","pneumaticcraft:heat_sink","pneumaticcraft:vacuum_pump","pneumaticcraft:vacuum_trap","pneumaticcraft:vortex_tube"));
        boolean pump=o.equals("pneumaticcraft:vacuum_pump"), mixer=o.equals("pneumaticcraft:fluid_mixer");
        Set<String> parts=pump ? Set.of("plate","handle") : chemistry ? Set.of("liner") : Set.of("plate","handle");
        Map<String,String> components=pump ? Map.of("rotor_a","rotation_rate","rotor_b","rotation_rate") : mixer ? Map.of("rotor","rotation_rate") : Map.of();
        shape(n,parts,components);
        double rate=1.5;
        for (String role : parts) rate=Math.min(rate,r(n,role,role.equals("handle") ? pump ? MS : MD : HS));
        if (!components.isEmpty()) {
            double product=1;
            for (String role : components.keySet()) product*=component(n,role,"rotation_rate");
            rate=Math.sqrt(rate*Math.pow(product,1d/components.size()));
        }
        return Map.of(chemistry ? "process_rate" : pump || o.equals("pneumaticcraft:vacuum_trap") ? "transfer_rate" : "thermal_transfer",rate);
    }
    private static Map<String,Double> drilling(String o, NumericInputs n) {
        output(o,Set.of("pneumaticcraft:assembly_drill","pneumaticcraft:jackhammer","pneumaticcraft:drill_bit_iron","pneumaticcraft:drill_bit_compressed_iron","pneumaticcraft:drill_bit_diamond","pneumaticcraft:drill_bit_netherite"));
        boolean assembly=o.equals("pneumaticcraft:assembly_drill");
        shape(n,Set.of("head"),assembly ? Map.of("cylinder_a","rotation_rate","cylinder_b","rotation_rate","cylinder_c","rotation_rate") : Map.of());
        double rate=direct(n,"head",HS);
        if (assembly) for (String role : n.components().keySet()) rate=Math.min(rate,component(n,role,"rotation_rate"));
        return Map.of("work_rate",rate);
    }
    private static Map<String,Double> compressed(String o, NumericInputs n) {
        if (!o.startsWith("compressedcreativity:")) throw new IllegalArgumentException("Wrong compressed output");
        String f=n.function(); boolean mesh=f.endsWith("/mesh"), blower=f.endsWith("/blower"), conversion=f.endsWith("/conversion"), pressure=f.endsWith("/pressure_tube");
        String property=conversion ? "conversion" : pressure ? "pressure_envelope" : "airflow";
        boolean empty=o.equals("compressedcreativity:mesh_empty"), engine=o.equals("compressedcreativity:compressed_air_engine");
        Set<String> parts=pressure ? Set.of("plate") : mesh && !empty ? Set.of() : conversion ? Set.of("plate","handle") : Set.of("blade","handle");
        Map<String,String> components=mesh && !empty ? Map.of("frame","airflow","frame2","airflow") : conversion && engine ? Map.of("rotor","conversion") : Map.of();
        if (!mesh && !blower && !conversion && !pressure) throw new IllegalArgumentException("Wrong compressed function");
        shape(n,parts,components);
        double rate=1.5;
        for (String role : parts) rate=Math.min(rate,r(n,role,role.equals("blade") || role.equals("plate") && !pressure ? HS : role.equals("plate") ? HD : MS));
        for (String role : components.keySet()) rate=Math.min(rate,component(n,role,property));
        return Map.of(property,pressure ? Math.min(1,rate) : rate);
    }
    private static Map<String,Double> diesel(String o, NumericInputs n) {
        output(o,Set.of("createdieselgenerators:engine_piston","createdieselgenerators:engine_turbocharger","createdieselgenerators:diesel_engine","createdieselgenerators:large_diesel_engine","createdieselgenerators:huge_diesel_engine"));
        String property="conversion"; boolean piston=o.endsWith("engine_piston"), turbo=o.endsWith("engine_turbocharger");
        Map<String,String> children=piston || o.endsWith("huge_diesel_engine") ? Map.of()
            : turbo ? Map.of("impeller","airflow") : o.endsWith("large_diesel_engine")
                ? Map.of("engine","conversion") : Map.of("piston_left","conversion","piston_right","conversion");
        // Child engine/piston components must expose all three independently captured final properties.
        if (!n.parts().keySet().equals(Set.of("rod","chamber")) || !n.components().keySet().equals(children.keySet()))
            throw new IllegalArgumentException("Wrong diesel roles");
        double conversion=bound(.45*r(n,"rod",MS)+.55*r(n,"chamber",HS));
        double load=bound(.45*r(n,"rod",MD)+.55*r(n,"chamber",HD));
        double thermal=load;
        if (!children.isEmpty() && !turbo) {
            double cc=0,cl=0,ct=0;
            for (String role : children.keySet()) {
                if (!n.components().get(role).keySet().equals(Set.of("conversion","load","thermal_envelope"))
                    && !n.components().get(role).equals(Map.of("baseline",1.0)))
                    throw new IllegalArgumentException("Incomplete diesel child");
                cc+=component(n,role,"conversion"); cl+=component(n,role,"load"); ct+=component(n,role,"thermal_envelope");
            }
            conversion=.5*(conversion+cc/children.size());
            load=.5*(load+cl/children.size());
            thermal=.5*(thermal+ct/children.size());
        }
        if (turbo) {
            if (!n.components().get("impeller").keySet().equals(Set.of("airflow"))
                && !n.components().get("impeller").equals(Map.of("baseline",1.0))) throw new IllegalArgumentException("Wrong turbo impeller");
            load=.75*load+.25*component(n,"impeller","airflow");
        }
        return Map.of(property,bound(conversion),"load",bound(load),"thermal_envelope",bound(thermal));
    }
    private static Map<String,Double> powergrid(String o, NumericInputs n) {
        String f=n.function();
        if (f.endsWith("/heating")) {
            output(o,Set.of("powergrid:basin_heater","powergrid:heating_coil"));
            int count=o.equals("powergrid:basin_heater") ? 5 : 2;
            java.util.HashMap<String,String> coils=new java.util.HashMap<>();
            for(int i=0;i<count;i++) coils.put("coil_"+i,"generator_ratio");
            shape(n,Set.of("thermal_support"),coils);
            double weakest=1.5;
            for(String role:coils.keySet()) weakest=Math.min(weakest,component(n,role,"generator_ratio"));
            return Map.of("thermal_mass_ratio",r(n,"thermal_support",HD),"heater_coil_ratio",weakest);
        }
        if (f.endsWith("/motors")) return single(o,n,"rotor",MS,"drive_ratio",Set.of("powergrid:electric_motor","powergrid:constant_speed_motor","powergrid:servo","powergrid:electric_fan"));
        output(o,Set.of("powergrid:copper_coil","powergrid:resistive_coil","powergrid:generator_clutch","powergrid:generator_commutator","powergrid:generator_induction_rotor","powergrid:generator_vertical_commutator"));
        shape(n,"shaft","surface"); return Map.of("generator_ratio",bound((r(n,"shaft",MS)+r(n,"surface",HD))/2));
    }
    private static Map<String,Double> precision(String o, NumericInputs n) {
        String f=n.function(); boolean rat=f.endsWith("rats/quarry_work");
        output(o,rat ? Set.of("rats:rat_quarry","rats:rat_upgrade_quarry") : f.endsWith("ae2/precision") ? Set.of("ae2:inscriber") : Set.of("alexscaves:quarry_smasher"));
        boolean upgrade=o.equals("rats:rat_upgrade_quarry");
        shape(n,Set.of(rat ? "head" : "tooling"),upgrade ? Map.of("native_head","hardness_limit") : Map.of());
        double rate=direct(n,rat ? "head" : "tooling",rat ? HS : Measure.HEAD_ATTACK);
        if (upgrade) {
            double hardness=n.components().get("native_head").get("hardness_limit");
            if (!Double.isFinite(hardness) || hardness<0 || hardness>100 || hardness!=Math.rint(hardness))
                throw new IllegalArgumentException("Invalid native head tier level");
            return Map.of("work_rate",rate,"hardness_limit",hardness);
        }
        return Map.of("work_rate",rate);
    }
    private static Map<String,Double> bees(String o, NumericInputs n) {
        boolean separation=n.function().endsWith("/separation");
        if (separation) output(o,Set.of("complicated_bees:centrifuge"));
        else {
            String kind=n.function().substring(n.function().lastIndexOf('/')+1);
            output(o,Set.of("complicated_bees:gyrofuge_"+kind+"_unit","complicated_bees:gyrofuge_basic_"+kind+"_unit","complicated_bees:gyrofuge_advanced_"+kind+"_unit"));
        }
        if (separation) { shape(n,"rotor"); return Map.of("bees_rate",r(n,"rotor",MS)); }
        shape(n,"rotor","plate"); return Map.of("bees_rate",Math.min(r(n,"rotor",MS),r(n,"plate",HS)));
    }
    private static Map<String,Double> pretty(String o, NumericInputs n) {
        String f=n.function(); boolean press=f.endsWith("/pressurizer"), extraction=f.endsWith("/extraction");
        if (press) output(o,Set.of("prettypipes:pressurizer"));
        else output(o,prefix("prettypipes:","low_"+(extraction ? "extraction" : "speed")+"_module","medium_"+(extraction ? "extraction" : "speed")+"_module","high_"+(extraction ? "extraction" : "speed")+"_module"));
        shape(n,press ? "working_assembly" : "actuator");
        return Map.of(press ? "pressurizer_rate" : extraction ? "extraction_rate" : "speed_rate",r(n,press ? "working_assembly" : "actuator",MS));
    }
    private static Map<String,Double> logistics(String o, NumericInputs n) {
        boolean drive=n.function().endsWith("/propulsion");
        return single(o,n,drive ? "drive" : "actuator",MS,drive ? "drive_rate" : "transfer_rate",drive
            ? Set.of("littlelogistics:energy_locomotive","littlelogistics:steam_locomotive","littlelogistics:energy_tug","littlelogistics:tug") : Set.of("littlelogistics:rapid_hopper"));
    }
    private static Map<String,Double> trackwork(String o, NumericInputs n) {
        output(o,Set.of("trackwork:large_phys_track","trackwork:large_simple_wheel","trackwork:large_suspension_track",
            "trackwork:med_phys_track","trackwork:med_simple_wheel","trackwork:med_suspension_track",
            "trackwork:oleo_wheel","trackwork:phys_track","trackwork:simple_wheel",
            "trackwork:small_simple_wheel","trackwork:suspension_track"));
        shape(n,"hub_suspension");
        boolean limb=n.parts().get("hub_suspension").containsKey(Measure.LIMB_DRAW_SPEED);
        Measure load=limb ? Measure.LIMB_DURABILITY : MD, response=limb ? Measure.LIMB_DRAW_SPEED : Measure.HANDLE_ATTACK_SPEED;
        var stats=n.parts().get("hub_suspension");
        var a=stats.get(load); var b=stats.get(response);
        if (a == null || b == null) throw new IllegalArgumentException("Missing native hub stat pairs");
        double loadRate=limb ? Math.sqrt(a.selected()/a.reference())
            : (1+(float)a.selected())/(1+(float)a.reference());
        double responseRate=(1+(float)b.selected())/(1+(float)b.reference());
        if (!Double.isFinite(loadRate) || loadRate<=0 || !Double.isFinite(responseRate) || responseRate<=0)
            throw new IllegalArgumentException("Invalid hub native ratio");
        return Map.of("mechanical_load",bound(loadRate),"suspension_response",bound(responseRate));
    }
    private static Map<String,Double> clockwork(String o, NumericInputs n) {
        boolean gas=n.function().endsWith("/gas_thermal"), prep=n.function().endsWith("/reactionwheel_prep");
        output(o,prep ? Set.of("create:flywheel") : gas
            ? Set.of("vs_clockwork:air_compressor","vs_clockwork:coal_burner","vs_clockwork:duct","vs_clockwork:exhaust","vs_clockwork:gas_engine","vs_clockwork:gas_nozzle","vs_clockwork:gas_thruster","vs_clockwork:pump_duct","vs_clockwork:valve_duct")
            : Set.of("vs_clockwork:andesite_flap_bearing","vs_clockwork:brass_propeller_bearing","vs_clockwork:gyro","vs_clockwork:juryrigged_propeller_bearing","vs_clockwork:propeller_blade","vs_clockwork:reactionwheel","vs_clockwork:smart_flap_bearing","vs_clockwork:wide_propeller_blade"));
        shape(n,Set.of(gas ? "plate" : "blade","handle"),prep ? Map.of("blank","baseline") : Map.of());
        return Map.of(gas ? "gas_transfer" : "actuator_response",bound(.65*r(n,gas ? "plate" : "blade",HS)+.35*r(n,"handle",MS)));
    }
    private static Map<String,Double> clockworkCopy(String o, NumericInputs n) {
        boolean gas=n.function().endsWith("_gas_engine");
        output(o,Set.of(gas ? "vs_clockwork:gas_engine" : "vs_clockwork:reactionwheel"));
        String key=gas ? "gas_transfer" : "actuator_response";
        shape(n,Set.of(),Map.of(gas ? "duct" : "flywheel",key));
        return Map.of(key,component(n,gas ? "duct" : "flywheel",key));
    }
    private static Map<String,Double> base(String o, NumericInputs n) {
        String f=n.function(); boolean cutter=f.endsWith("/cutting") || f.endsWith("/sawing");
        boolean airflow=f.endsWith("/airflow"), four=f.endsWith("sifter/frame") || airflow && o.equals("create:propeller");
        if (four) {
            Set<String> roles=Set.of("north","east","south","west");
            var components=new java.util.HashMap<String,String>();
            for(String role:n.components().keySet()) {
                if (!roles.contains(role) || !airflow || !o.equals("create:propeller")) throw new IllegalArgumentException("Wrong base component");
                components.put(role,"airflow");
            }
            var parts=new HashSet<>(roles); parts.removeAll(components.keySet());
            shape(n,parts,components);
        } else if (n.parts().size()+n.components().size()==1) {
            var components=new java.util.HashMap<String,String>();
            for(String role:n.components().keySet()) {
                if (!(f.endsWith("/mixing") && o.equals("create:mechanical_mixer") && role.equals("whisk")
                    || airflow && o.equals("create:encased_fan") && role.equals("propeller")))
                    throw new IllegalArgumentException("Wrong base component");
                components.put(role,airflow ? "airflow" : "work_rate");
            }
            shape(n,n.parts().keySet(),components);
        } else throw new IllegalArgumentException("Wrong base role count");
        Measure measure=cutter ? HS : f.endsWith("sifter/frame") || f.endsWith("/mixing") ? MS
            : f.endsWith("/pumping") ? MD : HS;
        double rate=1.5;
        for(String role:n.parts().keySet()) rate=Math.min(rate,r(n,role,measure));
        for(String role:n.components().keySet()) rate=Math.min(rate,component(n,role,airflow ? "airflow" : "work_rate"));
        return Map.of(airflow ? "airflow" : f.endsWith("/pumping") ? "pressure" : "work_rate",rate);
    }
    private static double direct(NumericInputs n, String role, Measure measure) {
        var stat=n.parts().get(role).get(measure);
        if (stat==null || !Double.isFinite(stat.selected()) || !Double.isFinite(stat.reference())
            || stat.selected()<=0 || stat.reference()<=0) throw new IllegalArgumentException("Invalid direct native stat");
        // Original drilling/precision bakers divide two float TCon accessors, before promoting to double.
        return bound((float)stat.selected()/(float)stat.reference());
    }
    private static Set<String> prefix(String prefix,String... names) {
        var values=new HashSet<String>(); for(String name:names) values.add(prefix+name); return values;
    }
}
