#!/usr/bin/env python3
"""Bind the isolated real-client witness to tested bytecode, resources, dependencies and images."""
import json
from pathlib import Path
import sys
from native_receipt import sha, tree
from source_identity import fingerprint

root, fixture = (Path(value).resolve() for value in sys.argv[1:])
if not fixture.is_relative_to(root / 'build/client-fixture'): raise SystemExit('Not an isolated client fixture')
witness = json.loads((fixture / 'client-witness.json').read_text())
if witness.get('status') != 'passed' or witness.get('pack_runtime_accepted') is not False:
    raise SystemExit('Missing or invalid local-client witness')
metadata = dict(line.split('=', 1) for line in (root / 'build/resources/main/META-INF/better-content-source.properties').read_text().splitlines())
current = fingerprint(root)
if metadata['production_fingerprint'] != current: raise SystemExit('Source changed after compilation')
images = {}
for name in witness['screenshots']:
    path = fixture / 'screenshots' / name
    if not path.resolve().is_relative_to(fixture) or path.stat().st_size < 5000: raise SystemExit('Missing/invalid image')
    if path.read_bytes()[:8] != b'\x89PNG\r\n\x1a\n': raise SystemExit('Invalid PNG signature')
    images[name] = sha(path)
inputs = {Path(name).relative_to(root).as_posix(): tree(Path(name)) for name in (fixture / 'fixture-inputs.txt').read_text().splitlines()}
deps = [{'artifact': Path(name).name, 'sha256': sha(Path(name))} for name in (fixture / 'runtime-classpath.txt').read_text().splitlines()]
output = fixture / 'execution.json'
if output.exists(): raise SystemExit('Refusing to overwrite client evidence')
output.write_text(json.dumps({'schema': 'better_manufacturing.client_fixture.v1', 'status': 'passed',
    'scope': 'isolated_userdev_drill_client', 'full_roster_accepted': False, 'pack_runtime_accepted': False,
    'visual_review_required': True, 'production_fingerprint': current, 'compiled_inputs': inputs,
    'runtime_dependencies': deps, 'screenshots': images, 'witness_sha256': sha(fixture / 'client-witness.json'),
    'log_sha256': sha(fixture / 'logs/latest.log'), 'world_input_sha256': sha(fixture / 'client-input.json'),
    'create_fixture_identity': json.loads((fixture / 'create-fixture-identity.json').read_text())}, indent=2) + '\n')
print(output)
