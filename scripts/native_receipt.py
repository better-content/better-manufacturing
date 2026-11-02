#!/usr/bin/env python3
"""Bind core userdev fixture results to actual classes/resources/dependencies, not a staged-JAR claim."""
import hashlib
import json
from pathlib import Path
import re
import sys
from source_identity import fingerprint


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


def tree(path):
    if not path.is_dir(): raise ValueError('Missing compiled input: ' + str(path))
    digest = hashlib.sha256()
    for file in sorted(p for p in path.rglob('*') if p.is_file()):
        digest.update(file.relative_to(path).as_posix().encode()); digest.update(b'\0')
        digest.update(bytes.fromhex(sha(file)))
    return digest.hexdigest()


def receipt(root, fixture, expected):
    log = fixture / 'logs/latest.log'
    text = log.read_text()
    if re.findall(r'(\d+) GAME TESTS COMPLETE', text) != [str(expected)]: raise ValueError('Wrong completed population')
    if re.findall(r'All (\d+) required tests passed', text) != [str(expected)]: raise ValueError('Not all required tests passed')
    if 'Game test server shutting down' not in text: raise ValueError('No normal shutdown')
    identity = dict(line.split('=', 1) for line in (root / 'build/resources/main/META-INF/better-content-source.properties').read_text().splitlines())
    current = fingerprint(root)
    if current != identity['production_fingerprint']: raise ValueError('Source changed after resource compilation')
    dependencies = []
    for name in (fixture / 'runtime-classpath.txt').read_text().splitlines():
        path = Path(name)
        if not path.is_file(): raise ValueError('Missing runtime dependency: ' + name)
        dependencies.append({'artifact': path.name, 'sha256': sha(path)})
    inputs = {Path(name).relative_to(root).as_posix(): tree(Path(name))
              for name in (fixture / 'fixture-inputs.txt').read_text().splitlines()}
    with_create = 'build/classes/java/createFixture' in inputs
    if with_create != (fixture / 'create-fixture-identity.json').is_file(): raise ValueError('Create fixture input/identity mismatch')
    with_sifter = any(dep['artifact'].startswith('createsifter-') for dep in dependencies)
    if with_sifter != (fixture / 'sifter-fixture-identity.json').is_file(): raise ValueError('Sifter fixture dependency/identity mismatch')
    pack_scripts = {}
    pack_data = {}
    owner_outputs = {}
    pack_root = root.parent.parent / 'better-content-modpack' / 'kubejs'
    pack_list = fixture / 'production-pack-inputs.txt'
    if pack_list.is_file():
        for line in pack_list.read_text().splitlines():
            relative, expected_hash = line.split('\t', 1)
            source = pack_root / relative
            if not source.is_file() or sha(source) != expected_hash:
                raise ValueError('Production pack data changed during native run: ' + relative)
            pack_data[relative] = expected_hash
        if not pack_data: raise ValueError('Empty positive-profile pack data selection')
    native_owners = fixture / 'native-owner-inputs.txt'
    dev_adpother = None
    if native_owners.is_file():
        for name in native_owners.read_text().splitlines():
            owner = Path(name)
            owner_outputs[owner.relative_to(root.parent.parent).as_posix()] = tree(owner)
        raw_receipt = fixture / 'dev-adpother-provenance.json'
        mapped_receipt = fixture / 'dev-adpother-mapped-provenance.txt'
        if not raw_receipt.is_file() or not mapped_receipt.is_file():
            raise ValueError('Fluids native fixture is missing dev AdPother provenance')
        raw = json.loads(raw_receipt.read_text())
        mapped = dict(line.split('=', 1) for line in mapped_receipt.read_text().splitlines())
        if raw.get('schema') != 'better_manufacturing.dev_adpother_fieldmap.v1' or raw.get('entry_byte_changes') != [
                'coremods/create/block_breaking_kinetic_tile_hook.js']:
            raise ValueError('Wrong one-site dev AdPother raw provenance')
        if mapped.get('coordinate') != raw.get('coordinate') or mapped.get('field_transform_and_pollution_callback') != 'PENDING_SEPARATE_NATIVE_AUTHORIZATION':
            raise ValueError('Wrong dev AdPother mapped provenance')
        if sha(Path(raw['original_raw'])) != raw['original_raw_sha256'] or sha(Path(raw['original_fg_mapped_cache'])) != raw['original_fg_mapped_cache_sha256']:
            raise ValueError('Pinned original AdPother was changed')
        if sha(Path(raw['derived_raw_artifact'])) != raw['derived_raw_sha256'] or sha(Path(mapped['artifact'])) != mapped['sha256']:
            raise ValueError('Derived AdPother artifact changed after provenance')
        selected = [dep for dep in dependencies if dep['artifact'].lower().startswith('adpother')]
        if len(selected) != 1 or selected[0]['artifact'] != Path(mapped['artifact']).name or selected[0]['sha256'] != mapped['sha256']:
            raise ValueError('Native classpath does not contain exactly the verified dev AdPother')
        dev_adpother = {'raw_provenance': raw, 'mapped_provenance': mapped,
                        'scope': 'LOCAL_ONLY_DEV_FIELD_MAPPING; NOT PRODUCTION_OR_REDISTRIBUTION'}
    script_list = fixture / 'production-scripts.txt'
    if script_list.is_file():
        for relative in script_list.read_text().splitlines():
            original, copied = pack_root / relative, fixture / 'kubejs' / relative
            if not original.is_file() or not copied.is_file() or sha(original) != sha(copied):
                raise ValueError('Production KubeJS script changed during native run: ' + relative)
            pack_scripts[relative] = sha(original)
        if not pack_scripts: raise ValueError('Empty positive-profile KubeJS script selection')
        if not any(dep['artifact'].startswith('kubejs-forge-') for dep in dependencies):
            raise ValueError('Positive profile is missing real KubeJS runtime')
    scope = 'selected_family_production_kubejs_userdev' if pack_scripts else ('core_create_drill_sifter_userdev' if with_sifter else ('core_and_create_drill_userdev' if with_create else 'core_contracts_and_tinkers_crafting_only'))
    return {'schema': 'better_manufacturing.core_fixture.v1', 'status': 'passed',
            'launch_mode': 'forge_userdev', 'scope': scope,
            'compiled_inputs': inputs, 'production_kubejs_scripts': pack_scripts,
            'production_pack_data': pack_data, 'native_owner_outputs': owner_outputs,
            'dev_adpother_provenance': dev_adpother,
            'create_fixture_identity': json.loads((fixture / 'create-fixture-identity.json').read_text()) if with_create else None,
            'sifter_fixture_identity': json.loads((fixture / 'sifter-fixture-identity.json').read_text()) if with_sifter else None,
            'full_roster_accepted': False, 'test_count': expected, 'fixture_id': fixture.name,
            'production_fingerprint': current, 'log_sha256': sha(log), 'runtime_dependencies': dependencies,
            'compiled_classes_sha256': tree(root / 'build/classes/java/main'),
            'resources_sha256': tree(root / 'build/resources/main')}


if __name__ == '__main__':
    root, fixture, expected = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), int(sys.argv[3])
    output = fixture / 'execution.json'
    if output.exists(): raise SystemExit('Refusing to replace existing fixture evidence')
    output.write_text(json.dumps(receipt(root, fixture, expected), indent=2) + '\n')
    print(output)
