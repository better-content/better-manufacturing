#!/usr/bin/env python3
"""Create an isolated local client fixture from a passed source GameTest world; never touch player worlds."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

root, source, target = (Path(value).resolve() for value in sys.argv[1:])
if not source.is_relative_to(root / 'build/gametest'): raise SystemExit('Source must be an owner-local GameTest fixture')
if not target.is_relative_to(root / 'build/client-fixture') or target.exists(): raise SystemExit('Client target must be fresh and owner-local')
receipt_path = source / 'execution.json'
receipt = json.loads(receipt_path.read_text())
if receipt['status'] != 'passed' or receipt['scope'] != 'core_create_drill_sifter_userdev': raise SystemExit('Need a passed Create/Sifter fixture')
if not (source / 'world/level.dat').is_file(): raise SystemExit('Native world is missing')
shutil.copytree(source / 'world', target / 'saves/manufacturing')
(target / 'options.txt').write_text('guiScale:2\nfullscreen:false\nrenderDistance:4\nsimulationDistance:4\nmaxFps:60\n')
(target / 'client-input.json').write_text(json.dumps({'source_fixture': source.name,
    'source_receipt_sha256': hashlib.sha256(receipt_path.read_bytes()).hexdigest(),
    'source_level_sha256': hashlib.sha256((source / 'world/level.dat').read_bytes()).hexdigest(),
    'scope': 'isolated_userdev_client_not_deployed_pack'}, indent=2) + '\n')
print(target)
