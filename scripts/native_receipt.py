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
    return {'schema': 'better_manufacturing.core_fixture.v1', 'status': 'passed',
            'launch_mode': 'forge_userdev', 'scope': 'core_and_create_drill_userdev' if expected > 9 else 'core_contracts_and_tinkers_crafting_only',
            'compiled_inputs': inputs,
            'create_fixture_identity': json.loads((fixture / 'create-fixture-identity.json').read_text()) if expected > 9 else None,
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
