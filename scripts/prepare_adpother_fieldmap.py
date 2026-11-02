#!/usr/bin/env python3
"""Fail-closed, LOCAL-ONLY AdPother userdev FIELD-domain correction.

This is NOT a general remapper, production artifact, or redistribution grant. It
copies the pinned raw archive under a new development-only Maven coordinate,
changing exactly one JavaScript expression. Original pack files and ForgeGradle
cache entries are read-only and rehashed after each operation.
"""
import argparse
from copy import copy
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import sys
from zipfile import ZipFile

RAW_NAME = 'AdPother-1.20.1-8.1.49.0-build.2294.jar'
RAW_SHA = '4d66ceb91116d2f1dbc55a125cf8b237a11e3eba7787908c066e3ee9d18f4de5'
MAPPED_SHA = '67b39780cd30addce4d6c80793ce952403e67c63cb6a2f876baec9df62ae54a9'
HOOK = 'coremods/create/block_breaking_kinetic_tile_hook.js'
HOOK_SHA = '90d9bf0c40bb268a90196b6b2f6634bdd7bab577aca50e81a65bd7d7c29ea3a6'
BEFORE = b"resolveName('f_58857_')"
AFTER = b"Java.type('net.minecraftforge.coremod.api.ASMAPI').mapField('f_58857_')"
GROUP = 'com.bettercontent.manufacturing.dev'
ARTIFACT = 'adpother-fieldmap-1.20.1'
VERSION = '8.1.49.0-fieldmap.1'
COORDINATE = f'{GROUP}:{ARTIFACT}:{VERSION}'
RESOURCE = 'META-INF/coremods.json'
METADATA = ('META-INF/mods.toml', 'META-INF/MANIFEST.MF', RESOURCE)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def checked(path, expected, label):
    if not path.is_file() or path.is_symlink():
        raise ValueError(f'{label} must be an existing regular non-symlink file: {path}')
    data = path.read_bytes()
    if digest(data) != expected:
        raise ValueError(f'{label} SHA-256 drift: expected {expected}, got {digest(data)}: {path}')
    return data


def inventory(data):
    with ZipFile(BytesIO(data)) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        if len(names) != 692 or len(set(names)) != 692 or HOOK not in names:
            raise ValueError(f'Pinned AdPother archive membership drift: {len(names)} entries, {len(set(names))} unique')
        if any(name.startswith('META-INF/') and name.upper().endswith(('.SF', '.RSA', '.DSA')) for name in names):
            raise ValueError('Unexpected signed AdPother archive; cannot rewrite resources safely')
        entries = {info.filename: archive.read(info) for info in infos}
        if sum(name.endswith('.class') for name in names) != 321:
            raise ValueError('Pinned AdPother 321-class inventory drift')
        return infos, entries, archive.comment


def derived(raw):
    infos, original, comment = inventory(raw)
    if digest(original[HOOK]) != HOOK_SHA or original[HOOK].count(BEFORE) != 1:
        raise ValueError('Pinned one-site AdPother hook identity drift')
    modified = original[HOOK].replace(BEFORE, AFTER)
    if modified.count(AFTER) != 1 or modified.count(BEFORE):
        raise ValueError('One-site FIELD remap replacement did not bind exactly once')
    output = BytesIO()
    with ZipFile(output, 'w', allowZip64=True) as archive:
        archive.comment = comment
        for info in infos:
            archive.writestr(copy(info), modified if info.filename == HOOK else original[info.filename])
    result = output.getvalue()
    _, revised, _ = inventory(result)
    changed = [name for name in original if original[name] != revised[name]]
    if changed != [HOOK] or any(original[name] != revised[name] for name in METADATA):
        raise ValueError(f'Dev artifact changed entries beyond the single hook: {changed}')
    if revised[HOOK] != modified:
        raise ValueError('Generated hook bytes changed during archive creation')
    return result, original, revised


def artifact_paths(root):
    directory = root / 'maven' / GROUP.replace('.', '/') / ARTIFACT / VERSION
    return (directory / f'{ARTIFACT}-{VERSION}.jar',
            directory / f'{ARTIFACT}-{VERSION}.pom', root / 'provenance.json')


def publish_once(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() or path.is_symlink():
        if path.is_symlink() or not path.is_file() or path.read_bytes() != data:
            raise ValueError(f'Refusing to replace or adopt a different derived artifact: {path}')
        return
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o644)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def verify_pinned(raw, mapped_cache):
    source = checked(raw, RAW_SHA, 'Pinned raw AdPother')
    cache = checked(mapped_cache, MAPPED_SHA, 'Existing ForgeGradle mapped AdPother')
    _, a, _ = inventory(source)
    _, b, _ = inventory(cache)
    if digest(b[HOOK]) != HOOK_SHA or a[HOOK] != b[HOOK]:
        raise ValueError('Existing mapped cache and pinned raw hook differ')
    return source


def prepare(raw, mapped_cache, root):
    if root.resolve() == raw.resolve() or root.resolve() == mapped_cache.resolve() or raw.resolve().is_relative_to(root.resolve()) or mapped_cache.resolve().is_relative_to(root.resolve()):
        raise ValueError('Derived root overlaps pinned originals/caches')
    source = verify_pinned(raw, mapped_cache)
    result, original, revised = derived(source)
    jar, pom, receipt = artifact_paths(root)
    xml = (f'<project xmlns="http://maven.apache.org/POM/4.0.0" '
           'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
           'xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">'
           f'<modelVersion>4.0.0</modelVersion><groupId>{GROUP}</groupId>'
           f'<artifactId>{ARTIFACT}</artifactId><version>{VERSION}</version>'
           '<packaging>jar</packaging></project>\n').encode()
    record = {
        'schema': 'better_manufacturing.dev_adpother_fieldmap.v1',
        'scope': 'LOCAL_ONLY_FORGE_USERDEV_NOT_PRODUCTION_OR_REDISTRIBUTION',
        'coordinate': COORDINATE,
        'original_raw': str(raw.resolve()), 'original_raw_sha256': RAW_SHA,
        'original_fg_mapped_cache': str(mapped_cache.resolve()), 'original_fg_mapped_cache_sha256': MAPPED_SHA,
        'hook_entry': HOOK, 'original_hook_sha256': HOOK_SHA,
        'derived_hook_sha256': digest(revised[HOOK]),
        'original_expression': BEFORE.decode(), 'derived_expression': AFTER.decode(),
        'api': 'Forge CoreMods 5.2.4 ASMAPI.mapField uses ModLauncher Domain.FIELD; MCPNamingService srg-to-mcp mapping is independently verified in runtime debug log. Actual transformed instruction/callback remain unproven until separately authorized native run.',
        'entry_count': 692, 'class_count': 321, 'entry_byte_changes': [HOOK],
        'preserved_entry_count': 691, 'coremods_json_sha256': digest(original[RESOURCE]),
        'mods_toml_sha256': digest(original['META-INF/mods.toml']),
        'manifest_sha256': digest(original['META-INF/MANIFEST.MF']),
        'derived_raw_artifact': str(jar.resolve()), 'derived_raw_sha256': digest(result),
        'maven_pom': str(pom.resolve()), 'maven_pom_sha256': digest(xml),
        'helper_sha256': digest(Path(__file__).read_bytes()),
        'release_status': 'NOT_NATIVE_VERIFIED', 'license': 'All rights reserved; no public redistribution authorized'
    }
    encoded = (json.dumps(record, indent=2, sort_keys=True) + '\n').encode()
    publish_once(jar, result)
    publish_once(pom, xml)
    publish_once(receipt, encoded)
    checked(jar, digest(result), 'Derived raw AdPother')
    checked(pom, digest(xml), 'Derived Maven POM')
    checked(receipt, digest(encoded), 'Immutable provenance')
    verify_pinned(raw, mapped_cache)
    print(f'DEV_ADPOTHER_RAW={jar} SHA256={digest(result)}')
    print(f'DEV_ADPOTHER_PROVENANCE={receipt} SHA256={digest(encoded)}')
    print(f'DEV_ADPOTHER_COORDINATE={COORDINATE} CHANGED_ENTRIES={HOOK} PRESERVED=691/692')


def verify_mapped(raw, mapped_cache, root, path):
    prepare(raw, mapped_cache, root)  # Recomputes and verifies immutable raw/POM/receipt; never overwrites.
    _, original, _ = derived(checked(raw, RAW_SHA, 'Pinned raw AdPother'))
    if not path.is_file() or path.is_symlink():
        raise ValueError(f'No ForgeGradle-derived dev artifact: {path}')
    mapped_bytes = path.read_bytes()
    _, entries, _ = inventory(mapped_bytes)
    if entries[HOOK] != original[HOOK].replace(BEFORE, AFTER):
        raise ValueError('ForgeGradle dev artifact lost or changed the one-site FIELD map hook')
    if entries[RESOURCE] != original[RESOURCE] or entries['META-INF/mods.toml'] != original['META-INF/mods.toml']:
        raise ValueError('ForgeGradle dev artifact changed AdPother registration/identity')
    print(f'DEV_ADPOTHER_FG_MAPPED={path} SHA256={digest(mapped_bytes)}')
    print('DEV_ADPOTHER_FG_FIELD_EXPR_PRESENT=true TRANSFORMED_GETFIELD_AND_POLLUTION_CALLBACK=UNPROVEN')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('raw', type=Path)
    parser.add_argument('mapped_cache', type=Path)
    parser.add_argument('output_root', type=Path)
    parser.add_argument('--verify-mapped', type=Path)
    args = parser.parse_args()
    if args.raw.name != RAW_NAME:
        raise ValueError('Wrong pinned raw artifact name')
    if args.verify_mapped is None:
        prepare(args.raw, args.mapped_cache, args.output_root)
    else:
        verify_mapped(args.raw, args.mapped_cache, args.output_root, args.verify_mapped)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        print('DEV_ADPOTHER_FIELD_MAP_REJECTED: ' + str(error), file=sys.stderr)
        sys.exit(1)
