#!/usr/bin/env python3
"""Bind the native Sifter fixture to the pack's public archive, not a filename guess."""
import hashlib
import json
from pathlib import Path
import sys
import tomllib
import zipfile


def verify(pin_path, cache):
    pin = tomllib.loads(pin_path.read_text())
    archive = cache / pin['filename']
    algorithm = pin['download']['hash-format']
    digest = hashlib.new(algorithm, archive.read_bytes()).hexdigest()
    if digest != pin['download']['hash']:
        raise ValueError('Sifter archive differs from pack pin')
    if pin['filename'] != 'createsifter-1.20.1-1.8.6-6.0.6.jar':
        raise ValueError('Unsupported Sifter pin')
    prefix = 'com/oierbravo/createsifter/'
    classes = ['content/contraptions/components/sifter/SifterBlockEntity.class',
               'content/contraptions/components/sifter/SifterBlockEntity$1.class',
               'content/contraptions/components/sifter/SifterBlock.class',
               'content/contraptions/components/sifter/SiftingRecipe.class',
               'content/contraptions/components/brasss_sifter/BrassSifterBlockEntity.class']
    with zipfile.ZipFile(archive) as jar:
        hooks = {prefix + name: hashlib.sha256(jar.read(prefix + name)).hexdigest() for name in classes}
    return {'schema': 'better_manufacturing.sifter_fixture.v1', 'scope': 'pinned_native_archive',
            'archive': str(archive.resolve()), 'algorithm': algorithm, 'digest': digest,
            'pin_sha256': hashlib.sha256(pin_path.read_bytes()).hexdigest(), 'hook_classes': hooks}


if __name__ == '__main__':
    pin, cache, output = map(Path, sys.argv[1:])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(verify(pin, cache), indent=2, sort_keys=True) + '\n')
