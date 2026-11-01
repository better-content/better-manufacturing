#!/usr/bin/env python3
"""Freeze the selected roster against pinned archive assets/recipes, NOT a runtime audit.
Never marks acquisition, adapters or player acceptance complete. No recipe mutation.
"""
import hashlib
import json
from pathlib import Path
import sys
import tomllib
from zipfile import ZipFile

DEFERRED_IDS = {'sophisticatedbackpacks:hopper_upgrade', 'sophisticatedbackpacks:advanced_hopper_upgrade',
                'vs_clockwork:gas_tank', 'vs_clockwork:gas_vent', 'vs_clockwork:sealer'}
GROUPS = []
def group(namespace, family, ids, role, parts, effects, milestone=4, disposition='material_dependent'):
    GROUPS.append((namespace, family, ids.split(), role, parts.split(), effects.split(), milestone, disposition))

group('create', 'cutting', 'mechanical_drill', 'head', 'pick_head', 'cutting_rate hardness_limit', 2)
group('create', 'sawing', 'mechanical_saw', 'blade', 'broad_blade small_blade', 'cutting_rate hardness_limit')
group('create', 'bulk_work', 'millstone crushing_wheel mechanical_roller mechanical_press', 'working_surface', 'hammer_head large_plate', 'work_rate load')
group('create', 'field_work', 'mechanical_harvester mechanical_plough', 'working_surface', 'small_blade small_axe_head', 'work_rate')
group('create', 'mixing', 'mechanical_mixer whisk', 'whisk', 'tool_handle tough_handle', 'mixing_rate load')
group('create', 'airflow', 'encased_fan propeller', 'rotor', 'small_blade broad_blade', 'airflow load')
group('create', 'fluid_transfer', 'mechanical_pump hose_pulley portable_fluid_interface', 'wetted_fitting', 'large_plate', 'transfer_rate chemical_compatibility')
group('create', 'fluid_envelope', 'basin spout item_drain fluid_pipe encased_fluid_pipe glass_fluid_pipe smart_fluid_pipe fluid_valve fluid_tank', 'liner', 'large_plate', 'chemical_compatibility thermal_envelope', 2)
group('create', 'steam_engine', 'steam_engine', 'piston_rod', 'tough_handle', 'conversion mechanical_limit')
group('create', 'launcher', 'weighted_ejector', 'spring', 'bow_limb', 'recharge_rate launch_envelope')
group('createsifter', 'sifting', 'sifter brass_sifter string_mesh andesite_mesh zinc_mesh brass_mesh advanced_brass_mesh', 'screen_frame', 'tool_handle tough_handle', 'sifting_rate', 2)
group('compressedcreativity', 'blowers', 'air_blower industrial_air_blower mesh_empty mesh_dense mesh_woven mesh_splashing mesh_splashing_frozen mesh_haunted', 'working_assembly', 'tool_handle broad_blade', 'airflow load')
group('compressedcreativity', 'conversion', 'rotational_compressor compressed_air_engine engine_rotor heater', 'working_assembly', 'tough_handle large_plate', 'conversion load thermal_envelope')
group('compressedcreativity', 'pressure_tubes', 'bracketed_pressure_tube bracketed_advanced_pressure_tube bracketed_reinforced_pressure_tube', 'pressure_body', 'large_plate', 'pressure_envelope')
group('pneumaticcraft', 'compressors', 'manual_compressor air_compressor liquid_compressor advanced_air_compressor advanced_liquid_compressor flux_compressor electric_compressor thermal_compressor solar_compressor electrostatic_compressor', 'working_assembly', 'tough_handle large_plate', 'compression_rate pressure_envelope resource_rate')
group('pneumaticcraft', 'pressure_network', 'pressure_tube advanced_pressure_tube reinforced_pressure_tube tube_junction pressure_chamber_wall pressure_chamber_glass pressure_chamber_valve pressure_chamber_interface', 'pressure_body', 'large_plate', 'pressure_envelope')
group('pneumaticcraft', 'chemistry', 'fluid_mixer thermopneumatic_processing_plant refinery refinery_output etching_tank', 'liner', 'large_plate', 'process_rate chemical_compatibility thermal_envelope')
group('pneumaticcraft', 'vacuum_thermal', 'vacuum_pump vacuum_trap vortex_tube heat_pipe heat_sink', 'working_assembly', 'tough_handle large_plate', 'transfer_rate thermal_envelope')
group('pneumaticcraft', 'fluid_transfer', 'small_tank medium_tank large_tank huge_tank liquid_hopper omnidirectional_hopper gas_lift', 'wetted_fitting', 'large_plate', 'chemical_compatibility transfer_rate')
group('pneumaticcraft', 'drilling', 'assembly_drill jackhammer drill_bit_iron drill_bit_compressed_iron drill_bit_diamond drill_bit_netherite', 'head', 'pick_head', 'cutting_rate hardness_limit')
group('pneumaticcraft', 'rotating_parts', 'turbine_blade turbine_rotor pneumatic_cylinder', 'working_assembly', 'small_blade tough_handle', 'provenance_transfer')
group('createdieselgenerators', 'engines', 'diesel_engine huge_diesel_engine large_diesel_engine engine_piston engine_turbocharger', 'working_assembly', 'tough_handle large_plate', 'conversion load thermal_envelope')
group('better_industrial_heat', 'thermal', 'heat_pipe coolant_exchanger thermal_firebox boiler_heater', 'thermal_surface', 'large_plate', 'heat_transfer thermal_envelope')
group('powergrid', 'generation', 'generator_housing vertical_generator_housing generator_induction_rotor generator_clutch generator_commutator generator_vertical_commutator winding copper_coil resistive_coil', 'working_assembly', 'tough_handle large_plate', 'conversion load thermal_envelope')
group('powergrid', 'motors', 'electric_motor constant_speed_motor servo electric_fan', 'rotor', 'tough_handle', 'conversion load')
group('powergrid', 'heating', 'heating_coil basin_heater', 'thermal_support', 'large_plate', 'heat_transfer thermal_envelope')
group('ae2', 'precision', 'inscriber', 'press_tooling', 'hammer_head large_plate', 'process_rate')
group('complicated_bees', 'separation', 'centrifuge', 'rotor', 'tough_handle', 'process_rate energy_rate', 5)
for family in ['processing', 'speed', 'efficiency', 'extraction']:
    group('complicated_bees', 'gyrofuge_' + family,
          ' '.join('gyrofuge_' + prefix + family + '_unit' for prefix in ['basic_', '', 'advanced_']),
          'working_assembly', 'tough_handle large_plate', 'process_rate energy_rate', 5)
group('complicated_bees', 'environment_control', 'mellarium_temp_unit mellarium_hydroregulator', 'working_assembly', 'large_plate', 'control_rate energy_rate', 5)
for family in ['speed', 'extraction']:
    group('prettypipes', family, ' '.join(grade + '_' + family + '_module' for grade in ['low', 'medium', 'high']), 'actuator', 'tool_handle', 'transfer_rate', 5)
group('prettypipes', 'pressurizer', 'pressurizer', 'working_assembly', 'tough_handle', 'transfer_rate resource_rate', 5)
group('littlelogistics', 'transfer', 'rapid_hopper', 'actuator', 'tool_handle', 'transfer_rate', 5)
for ns in ['sophisticatedstorage', 'sophisticatedbackpacks']:
    group(ns, 'transfer', 'hopper_upgrade advanced_hopper_upgrade', 'actuator', 'tool_handle', 'transfer_rate', 5)
group('rats', 'quarry_work', 'rat_quarry rat_upgrade_quarry', 'head', 'pick_head', 'work_rate hardness_limit', 5)
group('alexscaves', 'quarry_work', 'quarry_smasher', 'impact_head', 'hammer_head', 'work_rate', 5)
for family, effects in [('high_speed_governor', 'speed_limit'), ('exhaust_recuperator', 'recovery'), ('brake_manifold', 'deceleration'), ('adhesion_sanders', 'adhesion_delivery'), ('compound_torque_clutch', 'torque_transfer'), ('reinforced_drawgear', 'towing_limit'), ('trestle_erector', 'work_rate')]:
    group('better_rail_beetle', family, family + '_1 ' + family + '_2', 'working_assembly', 'tough_handle large_plate', effects, 2 if family == 'brake_manifold' else 5)
group('better_rail_beetle', 'drives', 'better_rail_beetle steam_drive flux_traction_motor source_impeller lifeforce_ram pneumatic_expansion_motor soul_combustor spirit_warping_engine', 'working_assembly', 'tough_handle large_plate', 'drive_performance', 5)
group('littlelogistics', 'propulsion', 'tug energy_tug steam_locomotive energy_locomotive', 'working_assembly', 'tough_handle', 'drive_performance consumption', 5)
group('trackwork', 'running_gear', 'simple_wheel med_simple_wheel large_simple_wheel small_simple_wheel suspension_track med_suspension_track large_suspension_track phys_track med_phys_track large_phys_track oleo_wheel', 'hub_suspension', 'tough_handle bow_limb', 'mechanical_load suspension_response', 6)
group('vs_clockwork', 'flight_actuators', 'brass_propeller_bearing copter_bearing juryrigged_propeller_bearing propeller_blade wide_propeller_blade reactionwheel andesite_flap_bearing smart_flap_bearing blade_controller gyro', 'working_assembly', 'tough_handle broad_blade', 'mechanical_limit actuator_response', 6)
group('vs_clockwork', 'gas_thermal', 'air_compressor duct pump_duct valve_duct gas_nozzle gas_vent gas_tank gas_heater gas_engine gas_thruster combustion_engine coal_burner steam_generator exhaust sealer', 'working_assembly', 'large_plate tough_handle', 'transfer_rate conversion thermal_envelope', 6)
group('vs_eureka', 'engine', 'engine', 'working_assembly', 'tough_handle', 'drive_performance consumption', 6)

# Recipe-only neighbors. They must not grow material-derived information capacity or control behavior.
def recipe(ns, family, ids, parts='tool_handle large_plate'):
    group(ns, family, ids, 'construction', parts, '', 6, 'recipe_only')
recipe('create', 'transmission', 'shaft cogwheel large_cogwheel andesite_encased_shaft brass_encased_shaft andesite_encased_cogwheel brass_encased_cogwheel andesite_encased_large_cogwheel brass_encased_large_cogwheel metal_girder_encased_shaft gearbox vertical_gearbox clutch gearshift encased_chain_drive adjustable_chain_gearshift rotation_speed_controller sequenced_gearshift')
recipe('create', 'mechanisms', 'mechanical_bearing clockwork_bearing mechanical_piston sticky_mechanical_piston piston_extension_pole gantry_carriage gantry_shaft rope_pulley elevator_pulley linear_chassis secondary_linear_chassis radial_chassis')
recipe('create', 'primitive_power', 'hand_crank water_wheel large_water_wheel windmill_bearing sail_frame', 'tool_handle tool_binding')
recipe('create', 'automation', 'deployer brass_hand mechanical_arm mechanical_crafter schematicannon schematic_table')
recipe('quark', 'automation', 'crafter')
recipe('create', 'logistics', 'belt_connector andesite_funnel brass_funnel andesite_tunnel brass_tunnel chute smart_chute chain_conveyor item_vault portable_storage_interface packager repackager package_frogport stock_link stock_ticker factory_gauge redstone_requester')
recipe('create', 'rail', 'small_bogey large_bogey controls cart_assembler minecart_coupling track_station track_signal track_observer contraption_controls')
recipe('pneumaticcraft', 'assembly_control', 'assembly_controller assembly_io_unit_import assembly_io_unit_export assembly_platform assembly_laser uv_light_box')
recipe('pneumaticcraft', 'control_logistics', 'programmer programmable_controller universal_sensor charging_station security_station drone_interface aerial_interface logistics_frame_active_provider logistics_frame_default_storage logistics_frame_passive_provider logistics_frame_requester logistics_frame_storage logistics_module drone collector_drone guard_drone harvesting_drone logistics_drone upgrade_matrix logistics_core')
recipe('powergrid', 'electrical_control', 'battery portable_battery capacitor transformer_small transformer_medium transformer_core variac lv_button lv_switch mv_switch hv_switch hv_breaker current_gauge voltage_gauge power_gauge thermometer circuit_design_table circuit_board integrated_circuit empty_circuit electrical_gizmo plotter punch_card_reader crt andesite_encased_crt conductive_casing')
recipe('morered', 'workstation', 'soldering_table')
recipe('ae2', 'apparatus', 'charger growth_accelerator molecular_assembler crafting_unit crafting_accelerator crafting_monitor 1k_crafting_storage 4k_crafting_storage 16k_crafting_storage 64k_crafting_storage 256k_crafting_storage drive chest io_port controller energy_acceptor energy_cell dense_energy_cell interface pattern_provider cell_workbench condenser quantum_ring quantum_link spatial_io_port spatial_pylon wireless_access_point')
recipe('oc2r', 'apparatus', 'computer monitor network_connector network_hub redstone_interface disk_drive flash_memory_flasher charger projector keyboard internet_gateway')
recipe('prettypipes', 'logistics', 'pipe blank_module pipe_frame item_terminal crafting_terminal ' + ' '.join(grade + '_' + kind + '_module' for grade in ['low','medium','high'] for kind in ['crafting','filter','retrieval','high_priority','low_priority']))
recipe('sophisticatedstorage', 'frames', 'upgrade_base controller storage_io storage_link')
recipe('sophisticatedbackpacks', 'frames', 'upgrade_base')
recipe('complicated_bees', 'frames_analysis', 'frame waxed_frame honeyed_frame twisting_frame soothing_frame cold_frame hot_frame dry_frame wet_frame deadly_frame restrictive_frame mellarium_panel gyrofuge_panel mellarium_base mellarium_frame_housing_1 mellarium_frame_housing_2 mellarium_frame_housing_3 mellarium_rain_shield mellarium_mutator mellarium_energy_cell mellarium_skybox mellarium_temporal_simulator mellarium_output_hatch gyrofuge_base gyrofuge_energy_cell gyrofuge_input_hatch gyrofuge_output_hatch autolyzer microscope bee_sorter')
recipe('createdieselgenerators', 'fermentation', 'bulk_fermenter basin_lid')
recipe('littlelogistics', 'cargo_infrastructure', 'barge barrel_barge vacuum_barge seater_barge fishing_barge fluid_barge fluid_car chest_car barrel_car seater_car tug_dock barge_dock vessel_charger locomotive_dock_rail car_dock_rail spring')
recipe('vs_eureka', 'control', 'anchor ' + ' '.join(wood + '_ship_helm' for wood in ['oak','spruce','birch','jungle','acacia','dark_oak']))
recipe('vs_clockwork', 'control', 'command_seat distance_sensor gyroscopic_sensor impact_sensor alt_meter redstone_resistor brass_redstone_resistor redstone_duct')
recipe('better_rail_beetle', 'control', 'engine_cradle compact_module_frame telescopic_survey_array_1 telescopic_survey_array_2 dispatch_receiver_1 dispatch_receiver_2 dispatch_remote route_beacon caged_searchlight')
recipe('better_industrial_heat', 'housing', 'impossible_matter_transducer')

PINS = {'create':'create', 'createsifter':'create-sifting', 'compressedcreativity':'compressedcreativity',
'pneumaticcraft':'pneumaticcraft-repressurized', 'createdieselgenerators':'create-diesel-generators', 'powergrid':'power-grid',
'ae2':'applied-energistics-2', 'complicated_bees':'complicated-bees', 'prettypipes':'pretty-pipes', 'littlelogistics':'little-logistics',
'sophisticatedstorage':'sophisticated-storage', 'sophisticatedbackpacks':'sophisticated-backpacks', 'rats':'rats', 'alexscaves':'alexs-caves',
'vs_clockwork':'create-clockwork', 'vs_eureka':'eureka-ships', 'trackwork':'trackwork', 'quark':'quark', 'morered':'more-red', 'oc2r':'oc2r'}
CUSTOM = {'better_rail_beetle':'better-rail-beetle-0.1.0.jar', 'better_industrial_heat':'better-industrial-heat-0.1.0.jar'}

def recipe_outputs(value):
    result = []
    if isinstance(value, str): return [value]
    if isinstance(value, list):
        for entry in value: result.extend(recipe_outputs(entry))
    if isinstance(value, dict):
        item = value.get('item', value.get('id'))
        if isinstance(item, str): result.append(item)
    return result

def generate(pack, cache):
    archives, sources, recipes = {}, {}, {}
    for ns in sorted({g[0] for g in GROUPS}):
        if ns in CUSTOM:
            path = pack / 'mods' / CUSTOM[ns]
        else:
            pin = tomllib.loads((pack / 'mods' / (PINS[ns] + '.pw.toml')).read_text())
            path = cache / pin['filename']
            expected = pin['download']
            if hashlib.new(expected['hash-format'], path.read_bytes()).hexdigest() != expected['hash']:
                raise ValueError('Cached artifact does not match Packwiz pin: ' + str(path))
        with ZipFile(path) as jar:
            names = set(jar.namelist()); archives[ns] = names
            for name in names:
                if not name.startswith('data/') or '/recipes/' not in name or not name.endswith('.json'): continue
                data = json.loads(jar.read(name))
                for field in ['result', 'results', 'output', 'outputs']:
                    for item in recipe_outputs(data.get(field)):
                        pieces = name.split('/'); recipe_id = pieces[1] + ':' + '/'.join(pieces[3:])[:-5]
                        recipes.setdefault(item, set()).add(recipe_id)
        sources[ns] = {'artifact':path.name, 'sha256':hashlib.sha256(path.read_bytes()).hexdigest(), 'evidence':'archive_assets_and_base_recipes_only'}
    entries = []
    for ns, family, ids, role, parts, effects, milestone, disposition in GROUPS:
        for item in ids:
            identity = ns + ':' + item
            if any(e['id'] == identity for e in entries): raise ValueError('duplicate ' + identity)
            candidates = [f'assets/{ns}/models/item/{item}.json', f'assets/{ns}/blockstates/{item}.json']
            entries.append({'id':identity, 'family':ns + '/' + family, 'disposition':disposition, 'milestone':milestone,
                'roles':{role:['tconstruct:' + p for p in parts]}, 'derived_properties':effects,
                'native_recipe_ids':sorted(recipes.get(identity, [])), 'pack_recipe_ids':[],
                'asset_evidence':[p for p in candidates if p in archives[ns]],
                'adapter':('better_manufacturing:' + ns + '/' + family) if disposition == 'material_dependent' else None,
                'status':'pending', 'acquisition_audit':'pending', 'native_reference_audit':'pending',
                'verification':[]})
    for entry in entries:
        if entry['id'] in DEFERRED_IDS:
            entry['disposition'] = 'excluded'
            entry['exclusion_reason'] = 'user_deferred_unconfirmed_identity'
    return {'schema':'better_manufacturing.coverage.v1', 'completion_requires_full_roster':True,
        'new_component_ceiling':12, 'planned_component_ids':['better_manufacturing:' + p for p in
        ['pla_stock','pp_stock','lactide','propylene','tin_carboxylate_catalyst','supported_dehydrogenation_catalyst','coordination_polymerisation_catalyst']],
        'sources':sources, 'entries':sorted(entries, key=lambda e:e['id']),
        'selection_findings':[{'kind':'missing_asset_evidence', 'ids':[e['id'] for e in sorted(entries, key=lambda e:e['id']) if not e['asset_evidence']],
            'status':'user_deferred', 'note':'User explicitly deferred these five unconfirmed IDs and requested completion of the remaining roster. They are not implemented, verified, or replaced with other machines.'}],
        'excluded_families':['creative_and_internal_helpers','quarantined_content','tconstruct_bootstrap','primitive_survival',
        'cooking','decorative_construction','ordinary_storage_capacity','magic_progression','ecology_and_genetics',
        'weapons_and_armour','specialist_encounters','environmental_pumps','creatingspace','experimental_spaceflight'],
        'unexpanded_recipe_only_families':['createhorsepower:horse_crank','sophisticated_container_reinforcement_variants',
        'sophisticated_nonmechanical_upgrades','utility_tool_grips','remaining_prettypipes_modifiers','oc2r_robot_chassis']}

if __name__ == '__main__':
    pack = Path(sys.argv[1]).resolve(); cache = Path(sys.argv[2]).resolve()
    target = pack / 'kubejs/data/better_manufacturing/coverage.json'
    if target.exists(): raise SystemExit('Refusing to overwrite reviewed manifest; update explicitly')
    target.parent.mkdir(parents=True, exist_ok=True)
    data = generate(pack, cache); target.write_text(json.dumps(data, indent=2) + '\n')
    missing = [e['id'] for e in data['entries'] if not e['asset_evidence']]
    print(f"Frozen {len(data['entries'])} selected identities. Missing asset evidence: {missing}")
    print('This is not runtime registry, acquisition or feature acceptance.')
