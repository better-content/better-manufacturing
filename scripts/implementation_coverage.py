#!/usr/bin/env python3
"""Source/local qualification only. This gate can never grant combined-pack acceptance."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import struct
import os
import tomllib
from coverage import validate as validate_selection
from source_identity import fingerprint

SCHEMA = 'better_manufacturing.implementation.v1'
SHA = re.compile(r'[a-f0-9]{64}\Z')
AUDITS = {'registration', 'native_behavior', 'acquisition', 'policy', 'reference'}
NATIVE = {'two_fixture_builds', 'craft_install_operate', 'save_reload', 'recover_retool',
          'inventory_safety', 'client_sync', 'changed_data_reload', 'native_accounting', 'performance', 'teaching',
          'stock_invalid_data', 'partial_batches', 'wear_affix_preservation', 'optional_absence'}
RECIPE = {'craft_acquisition', 'preserved_function', 'metadata_safety', 'teaching'}
LIFECYCLES = {'placement_drops', 'wrench_removal', 'conversion', 'multiblock', 'contraption', 'vehicle_module'}
CONTRACT_FIELDS = {'roles', 'quantities', 'reference', 'properties', 'accounting_hook', 'lifecycle'}
FEATURE_CASES = {
    'profiles': {'server_authority', 'atomic_reload', 'client_sync', 'unknown_properties', 'bounded_cache'},
    'transfers': {'explicit_source_roles', 'all_native_transfers', 'conservation', 'metadata_safety'},
    'process_transactions': {'catalyst_retention', 'output_reservation', 'vacuum_loss', 'containment_loss', 'interruption_reload'},
    'pla': {'fermentation', 'lactide_water_recovery', 'ring_opening', 'native_accounting'},
    'pp': {'petroleum_root', 'fractionation', 'dehydrogenation_hydrogen', 'solid_polymerisation', 'native_accounting'},
    'shaping': {'part_builder', 'casting', 'shape_eligibility', 'stock_melt_part_conservation'},
    'recovery': {'clean_remelt', 'composite_disassembly', 'no_monomer_regeneration', 'no_cross_polymer_conversion'},
    'plastic_retirement': {'no_generic_production', 'consumer_routes', 'hardcoded_consumers', 'decorative_pp'},
    'bootstrap': {'acyclic_bootstrap', 'two_material_routes'},
    'gas_identity': {'propylene_identity', 'stored_state', 'escape', 'native_units', 'chemlib_regression'},
    **{'ponder_' + name: {'teaching'} for name in ['pla', 'pp', 'shaping', 'assembly', 'compatibility', 'recovery']},
}


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def bound_file(reference, root):
    if not isinstance(reference, dict) or not isinstance(reference.get('path'), str) or not SHA.fullmatch(reference.get('sha256', '')):
        raise ValueError('invalid file binding')
    relative = Path(reference['path'])
    path = (root / relative).resolve()
    if relative.is_absolute() or not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError('missing/escaped file binding')
    if digest(path) != reference['sha256']: raise ValueError('file binding changed')
    return path


def validate(selection, ledger, workspace):
    errors = validate_selection(selection)
    if ledger.get('schema') != SCHEMA: errors.append('unsupported implementation ledger')
    if ledger.get('full_pack_accepted') is not False: errors.append('source ledger cannot claim full-pack acceptance')
    records = ledger.get('entries', {})
    if not isinstance(records, dict): return errors + ['invalid implementation entries']
    selected = {e['id']: e for e in selection['entries'] if e['disposition'] != 'excluded'}
    for entry in selection['entries']:
        if entry['disposition'] != 'excluded' or entry.get('exclusion_reason') == 'user_deferred_unconfirmed_identity': continue
        try:
            if entry.get('exclusion_reason') not in selection.get('excluded_families', []): raise ValueError('not an approved exclusion')
            for kind in ['source_evidence', 'policy_evidence']:
                references = entry.get('exclusion_audit', {}).get(kind, [])
                if not references: raise ValueError('missing exclusion audit')
                for reference in references: bound_file(reference, workspace)
        except (ValueError, TypeError, OSError) as failure: errors.append(entry['id'] + ': ' + str(failure))
    feature_digest = hashlib.sha256(json.dumps(selection['sources'], sort_keys=True).encode()).hexdigest()
    features = ledger.get('features', {})
    if not isinstance(features, dict): features = {}; errors.append('invalid feature ledger')
    for name, required in FEATURE_CASES.items():
        feature = features.get(name, {})
        if feature.get('implementation') != 'complete' or feature.get('owner_local') != 'passed':
            errors.append('feature incomplete: ' + name); continue
        try:
            if feature.get('pack_acceptance') != 'pending': raise ValueError('source feature cannot promote pack acceptance')
            if not feature.get('implementation_files'): raise ValueError('missing feature implementation')
            for reference in feature['implementation_files']: bound_file(reference, workspace)
            verify_receipts('better_manufacturing:feature/' + name, feature, required, workspace, feature_digest)
        except (ValueError, KeyError, TypeError, OSError) as failure: errors.append(name + ': ' + str(failure))
    for identity in records.keys() - selected.keys(): errors.append('unselected implementation entry: ' + identity)
    if selection.get('unexpanded_recipe_only_families'): errors.append('recipe-only families still unexpanded')
    for finding in selection.get('selection_findings', []):
        if finding.get('status') not in {'resolved', 'user_deferred'}: errors.append('selection discrepancy unresolved')
    for identity, entry in selected.items():
        record = records.get(identity, {})
        if record.get('implementation') != 'complete' or record.get('owner_local') != 'passed':
            errors.append('source/local incomplete: ' + identity)
            continue
        if record.get('pack_acceptance') != 'pending': errors.append('source gate cannot promote pack acceptance: ' + identity)
        try:
            contract = record.get('contract', {})
            if not CONTRACT_FIELDS <= contract.keys(): raise ValueError('incomplete mechanism contract')
            if contract['roles'] != entry['roles'] or set(contract['properties']) != set(entry['derived_properties']):
                raise ValueError('mechanism differs from selected scope')
            if set(contract['quantities']) != set(entry['roles']) or any(type(q) is not int or q <= 0 for q in contract['quantities'].values()):
                raise ValueError('missing exact role quantities')
            if not contract['reference'] or not contract['accounting_hook']: raise ValueError('missing reference/accounting hook')
            lifecycle = contract['lifecycle']
            if set(lifecycle) != LIFECYCLES: raise ValueError('unreviewed lifecycle paths')
            for key, decision in lifecycle.items():
                if not isinstance(decision, dict) or type(decision.get('applicable')) is not bool or not decision.get('reason'):
                    raise ValueError('unexplained lifecycle applicability: ' + key)
            audits = record.get('source_audit', {})
            if set(audits) != AUDITS: raise ValueError('incomplete source audit')
            for category, references in audits.items():
                if not isinstance(references, list) or not references: raise ValueError('missing audit source: ' + category)
                for reference in references: bound_file(reference, workspace)
            for kind in ['implementation_files', 'teaching_files']:
                if not record.get(kind): raise ValueError('missing ' + kind)
                for reference in record[kind]: bound_file(reference, workspace)
            required = set(NATIVE if entry['disposition'] == 'material_dependent' else RECIPE)
            required.update(key for key, value in lifecycle.items() if value['applicable'])
            verify_receipts(identity, record, required, workspace, selection['sources'][identity.split(':')[0]]['sha256'])
        except (ValueError, KeyError, TypeError, OSError) as failure:
            errors.append(identity + ': ' + str(failure))
    return errors


def verify_receipts(identity, record, required, workspace, upstream_hash):
    found = set()
    if not record.get('evidence'): raise ValueError('missing owner-local evidence')
    for reference in record['evidence']:
        receipt = json.loads(bound_file(reference, workspace).read_text())
        if (receipt.get('schema') != 'better_manufacturing.owner_local.v1' or receipt.get('status') != 'passed'
            or receipt.get('full_pack_accepted') is not False or receipt.get('subject') != identity
            or receipt.get('upstream_sha256') != upstream_hash):
            raise ValueError('unbound owner-local receipt')
        owners = receipt.get('production_sources', {})
        if not owners: raise ValueError('missing production source bindings')
        for name, owner in owners.items():
            relative = Path(owner['path']); root = (workspace / relative).resolve()
            if relative.is_absolute() or not root.is_relative_to((workspace / 'mod_source').resolve()) or not root.is_dir():
                raise ValueError('invalid source root: ' + name)
            if not SHA.fullmatch(owner.get('fingerprint', '')) or fingerprint(root) != owner['fingerprint']:
                raise ValueError('production source changed: ' + name)
        execution = json.loads(bound_file(receipt['execution'], workspace).read_text())
        if execution.get('status') != 'passed' or execution.get('launch_mode') not in {'forge_userdev', 'owner_local_runtime_jar'}:
            raise ValueError('not a native owner-local execution')
        if execution.get('full_pack_accepted') is not False: raise ValueError('execution scope is not owner-local')
        if execution.get('production_sources') != owners: raise ValueError('execution/source binding mismatch')
        for kind in ['compiled_inputs', 'runtime_dependencies', 'logs']:
            bindings = execution.get(kind)
            if not isinstance(bindings, list) or not bindings: raise ValueError('missing executed ' + kind)
            for binding in bindings: bound_file(binding, workspace)
        logs = '\n'.join(bound_file(binding, workspace).read_text() for binding in execution['logs'])
        observed = {}
        for line in logs.splitlines():
            if 'MANUFACTURING_CASE_PASS ' not in line: continue
            event = json.loads(line.split('MANUFACTURING_CASE_PASS ', 1)[1])
            observed.setdefault(event['subject'], set()).add(event['case'])
        declared = {subject: set(cases) for subject, cases in execution.get('subject_cases', {}).items()}
        if not observed or observed != declared: raise ValueError('subject cases differ from execution log')
        if execution.get('fixture_kind') == 'gametest':
            completed = re.findall(r'(\d+) GAME TESTS COMPLETE', logs)
            passed = re.findall(r'All (\d+) required tests passed', logs)
            if len(completed) != 1 or completed != passed or int(completed[0]) < 1 or 'Game test server shutting down' not in logs:
                raise ValueError('incomplete native test run')
        elif execution.get('fixture_kind') == 'client':
            if logs.count('MANUFACTURING_CLIENT_COMPLETE') != 1 or 'Stopping!' not in logs: raise ValueError('incomplete client run')
        else: raise ValueError('unsupported fixture kind')
        cases = receipt.get('cases')
        executed = execution.get('subject_cases', {}).get(identity, [])
        if not isinstance(cases, list) or not cases or not set(cases) <= set(executed): raise ValueError('unexecuted subject cases')
        if 'teaching' in cases:
            if execution.get('fixture_kind') != 'client': raise ValueError('teaching requires real-client execution')
            review = json.loads(bound_file(receipt['visual_review'], workspace).read_text())
            if review.get('subject') != identity or review.get('status') != 'passed' or not review.get('images'):
                raise ValueError('missing bounded visual review')
            for image in review['images']:
                pixels = bound_file(image, workspace).read_bytes()
                if len(pixels) < 5000 or pixels[:8] != b'\x89PNG\r\n\x1a\n' or pixels[12:16] != b'IHDR':
                    raise ValueError('missing real-size PNG capture')
                width, height = struct.unpack('>II', pixels[16:24])
                if not (320 <= width <= 16384 and 180 <= height <= 16384): raise ValueError('placeholder-sized capture')
        found.update(cases)
    if not required <= found: raise ValueError('missing cases: ' + ','.join(sorted(required - found)))


def validate_pinned_sources(selection, pack, cache):
    errors = []
    pins = {}
    for path in (pack / 'mods').glob('*.pw.toml'):
        pin = tomllib.loads(path.read_text())
        if 'filename' in pin: pins[pin['filename']] = pin
    for namespace, source in selection['sources'].items():
        try:
            artifact = source['artifact']
            if Path(artifact).name != artifact: raise ValueError('invalid artifact filename')
            if namespace in {'better_rail_beetle', 'better_industrial_heat'}:
                archive = pack / 'mods' / artifact
            else:
                pin = pins.get(artifact)
                if pin is None: raise ValueError('artifact is no longer pinned')
                archive = cache / artifact
                expected = pin['download']
                if hashlib.new(expected['hash-format'], archive.read_bytes()).hexdigest() != expected['hash']:
                    raise ValueError('cached artifact differs from current pin')
            if digest(archive) != source['sha256']: raise ValueError('coverage source changed')
        except (KeyError, ValueError, OSError) as failure: errors.append(namespace + ': ' + str(failure))
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--initialize', action='store_true', help='Create a pending ledger; refuse to overwrite one')
    args = parser.parse_args()
    directory = args.pack / 'kubejs/data/better_manufacturing'
    selection = json.loads((directory / 'coverage.json').read_text())
    target = directory / 'implementation.json'
    if args.initialize:
        ledger = {'schema': SCHEMA, 'full_pack_accepted': False,
            'features': {name: {'implementation': 'pending', 'owner_local': 'pending', 'pack_acceptance': 'pending'} for name in FEATURE_CASES}, 'entries': {
            e['id']: {'implementation': 'pending', 'owner_local': 'pending', 'pack_acceptance': 'pending'}
            for e in selection['entries'] if e['disposition'] != 'excluded'}}
        with target.open('x') as output: output.write(json.dumps(ledger, indent=2) + '\n')
        print('Initialized pending source ledger; no implementation or acceptance credit granted.')
        return 0
    cache = Path(os.environ.get('BC_UPSTREAM_MOD_DIR', str(args.workspace.resolve() / '.cache/bc/packwiz-downloads/mods')))
    errors = validate_pinned_sources(selection, args.pack, cache)
    errors.extend(validate(selection, json.loads(target.read_text()), args.workspace.resolve()))
    print(json.dumps({'source_implementation_complete': not errors, 'full_pack_accepted': False, 'errors': errors}, indent=2))
    return 1 if errors else 0

if __name__ == '__main__': sys.exit(main())
