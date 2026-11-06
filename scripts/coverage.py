#!/usr/bin/env python3
"""Validate selection data separately from full-roster acceptance. Pending never means done."""
import argparse
import hashlib
import json
from pathlib import Path
import re

ID = re.compile(r'[a-z0-9_.-]+:[a-z0-9/._-]+\Z')
DISPOSITIONS = {'material_dependent', 'recipe_only', 'excluded'}
USER_DEFERRED = {'sophisticatedbackpacks:hopper_upgrade', 'sophisticatedbackpacks:advanced_hopper_upgrade',
                 'vs_clockwork:gas_tank', 'vs_clockwork:gas_vent', 'vs_clockwork:sealer',
                 'vs_clockwork:brass_redstone_resistor', 'vs_clockwork:combustion_engine',
                 'pneumaticcraft:electric_compressor'}
USER_DEFERRED_CLOSED = {'createsifter:advanced_brass_mesh'}
REQUIRED_NATIVE = {'two_reachable_builds', 'craft_install_operate', 'save_reload', 'recover_retool',
                   'inventory_safety', 'client_sync', 'data_reload', 'native_accounting', 'performance'}

def validate(data, complete=False, evidence_root=None):
    errors = []
    if data.get('schema') != 'better_manufacturing.coverage.v1': errors.append('unsupported schema')
    if data.get('completion_requires_full_roster') is not True: errors.append('full roster must remain mandatory')
    components = data.get('planned_component_ids', [])
    if len(components) != 7 or len(set(components)) != len(components): errors.append('expected seven distinct planned component IDs')
    if data.get('new_component_ceiling') != 12 or len(components) > 12: errors.append('component budget violation')
    if not all(isinstance(i, str) and ID.fullmatch(i) for i in components): errors.append('invalid component identity')
    seen = set()
    entries = data.get('entries', [])
    if not entries: errors.append('empty coverage manifest')
    for entry in entries:
        identity = entry.get('id', '')
        if not ID.fullmatch(identity) or identity in seen: errors.append('invalid/duplicate identity: ' + identity)
        seen.add(identity)
        disposition = entry.get('disposition')
        if disposition not in DISPOSITIONS: errors.append('invalid disposition: ' + identity)
        if disposition == 'excluded':
            reason = entry.get('exclusion_reason')
            if not reason: errors.append('unexplained exclusion: ' + identity)
            elif reason == 'user_deferred_unconfirmed_identity':
                if identity not in USER_DEFERRED: errors.append('outside authorized deferral: ' + identity)
            elif reason == 'user_deferred_closed_acquisition':
                if identity not in USER_DEFERRED_CLOSED: errors.append('outside authorized deferral: ' + identity)
            elif complete: errors.append('exclusion needs source-audit support: ' + identity)
        if not isinstance(entry.get('roles'), dict) or not entry['roles']: errors.append('missing roles: ' + identity)
        else:
            for role, parts in entry['roles'].items():
                if not re.fullmatch(r'[a-z][a-z0-9_]{0,47}', role): errors.append('invalid role: ' + identity)
                if not parts or not all(isinstance(p, str) and ID.fullmatch(p) for p in parts): errors.append('invalid parts: ' + identity)
        if disposition == 'material_dependent' and (not entry.get('adapter') or not entry.get('derived_properties')):
            errors.append('missing runtime contract: ' + identity)
        if disposition == 'recipe_only' and (entry.get('adapter') or entry.get('derived_properties')):
            errors.append('recipe-only item has runtime performance: ' + identity)
        if entry.get('status') not in {'pending', 'implemented', 'verified'}: errors.append('invalid status: ' + identity)
        for key in ['native_recipe_ids', 'pack_recipe_ids']:
            if not isinstance(entry.get(key), list) or not all(ID.fullmatch(i) for i in entry[key]): errors.append('invalid recipe IDs: ' + identity)
        source = data.get('sources', {}).get(identity.split(':')[0])
        if not source or not re.fullmatch(r'[a-f0-9]{64}', source.get('sha256', '')): errors.append('missing pinned source: ' + identity)
        if complete and disposition != 'excluded':
            if entry.get('status') != 'verified' or entry.get('acquisition_audit') != 'verified' or entry.get('native_reference_audit') != 'verified':
                errors.append('incomplete: ' + identity)
                continue
            required = REQUIRED_NATIVE if disposition == 'material_dependent' else {'craft_acquisition', 'preserved_function'}
            found = set()
            for record in entry.get('verification', []):
                if not evidence_root: errors.append('missing evidence root'); break
                path = (evidence_root / record.get('path', '')).resolve()
                if not path.is_relative_to(evidence_root.resolve()) or not path.is_file():
                    errors.append('missing/escaped evidence: ' + identity); continue
                payload = path.read_bytes()
                if hashlib.sha256(payload).hexdigest() != record.get('sha256'):
                    errors.append('evidence hash mismatch: ' + identity); continue
                receipt = json.loads(payload)
                if receipt.get('subject') != identity or receipt.get('status') != 'passed' or receipt.get('upstream_sha256') != source['sha256']:
                    errors.append('unbound/failed evidence: ' + identity); continue
                if not re.fullmatch(r'[a-f0-9]{64}', receipt.get('runtime_artifact_sha256', '')):
                    errors.append('missing runtime artifact identity: ' + identity); continue
                found.update(receipt.get('cases', []))
            if not required <= found: errors.append('missing verification cases: ' + identity)
    if complete and any(finding.get('status') not in {'resolved', 'user_deferred'} for finding in data.get('selection_findings', [])):
        errors.append('selection discrepancies remain unresolved')
    if complete and data.get('unexpanded_recipe_only_families'): errors.append('recipe-only families still unexpanded')
    return errors

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--require-complete', action='store_true')
    args = parser.parse_args()
    data = json.loads((args.pack / 'kubejs/data/better_manufacturing/coverage.json').read_text())
    errors = validate(data, args.require_complete, Path('build/evidence'))
    active = [e for e in data['entries'] if e['disposition'] != 'excluded']
    counts = {s: sum(e['status'] == s for e in active) for s in ['pending', 'implemented', 'verified']}
    print(json.dumps({'coverage_entries':len(data['entries']), 'selected_entries':len(active),
                      'user_deferred_entries':sum(e.get('exclusion_reason') in {
                          'user_deferred_unconfirmed_identity', 'user_deferred_closed_acquisition'} for e in data['entries']),
                      'status_counts':counts,
                      'full_roster_accepted':args.require_complete and not errors,
                      'errors':errors}, indent=2))
    return 1 if errors else 0

if __name__ == '__main__':
    raise SystemExit(main())
