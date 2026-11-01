#!/usr/bin/env python3
"""Prove the slim development fixture's hook/API bytes match the pinned distribution.
This is not a substitute for pack runtime or full contraption acceptance.
"""
import hashlib
import json
from pathlib import Path
import sys
import tomllib
from zipfile import ZipFile

CLASSES = [
    'content/kinetics/base/BlockBreakingKineticBlockEntity',
    'content/kinetics/base/BlockBreakingMovementBehaviour', 'content/kinetics/base/KineticBlockEntity',
    'content/kinetics/drill/DrillBlock', 'content/kinetics/drill/DrillBlockEntity',
    'content/kinetics/drill/DrillMovementBehaviour', 'foundation/blockEntity/SmartBlockEntity',
    'foundation/blockEntity/behaviour/BlockEntityBehaviour', 'foundation/blockEntity/behaviour/BehaviourType',
    'api/event/BlockEntityBehaviourEvent', 'content/contraptions/behaviour/MovementContext',
    'content/schematics/requirement/ItemRequirement', 'content/schematics/requirement/ItemRequirement$StackRequirement']


def verify(pin_path, slim_path, cache):
    pin = tomllib.loads(pin_path.read_text()); public_path = cache / pin['filename']
    public = public_path.read_bytes(); slim = slim_path.read_bytes()
    if hashlib.new(pin['download']['hash-format'], public).hexdigest() != pin['download']['hash']:
        raise ValueError('Public Create JAR does not match Packwiz pin')
    with ZipFile(public_path) as distribution, ZipFile(slim_path) as fixture:
        def revision(jar):
            manifest = dict(line.split(': ', 1) for line in jar.read('META-INF/MANIFEST.MF').decode().splitlines() if ': ' in line)
            return manifest.get('Git-Hash')
        commit = revision(distribution)
        if not commit or revision(fixture) != commit: raise ValueError('Create fixture source revision differs')
        classes = {}
        for name in CLASSES:
            path = 'com/simibubi/create/' + name + '.class'
            if distribution.read(path) != fixture.read(path): raise ValueError('Create hook/API bytecode drift: ' + name)
            classes[name] = hashlib.sha256(distribution.read(path)).hexdigest()
    return {'schema': 'better_manufacturing.create_fixture_identity.v1', 'source_revision': commit,
            'public_artifact_sha256': hashlib.sha256(public).hexdigest(), 'slim_artifact_sha256': hashlib.sha256(slim).hexdigest(),
            'matching_classes': classes, 'pack_runtime_accepted': False}


if __name__ == '__main__':
    pin, slim, cache, output = map(Path, sys.argv[1:])
    result = verify(pin, slim, cache); output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print('Create fixture: source revision and all 13 selected hook/API classes match the pinned distribution')
