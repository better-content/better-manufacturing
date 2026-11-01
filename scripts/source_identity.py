#!/usr/bin/env python3
"""Match the pack's production-input fingerprint; never mistake dirty bytes for HEAD."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys

EXCLUDED = {'build', '.gradle', '.git', 'docs', 'generated', 'logs', 'run', 'runs',
            'native-evidence', 'test', 'tests', 'gametest', 'gameTest', 'testFixtures'}
TEST_TREES = {'test', 'tests', 'gametest', 'gameTest', 'testFixtures'}

def production(path):
    parts = path.split('/')
    if parts[0] == 'src':
        return len(parts) < 2 or parts[1] not in TEST_TREES
    return (parts[0] not in EXCLUDED and not path.lower().endswith('.md')
            and not path.endswith('.log') and path not in {'.gitignore', '.gitattributes', 'LICENSE', 'LICENSE.txt'})

def fingerprint(root):
    if (root / '.git').exists():
        raw = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root)
        paths = set(raw.decode().split('\0'))
    else:
        paths = set()
        for directory, dirs, files in os.walk(root):
            rel = Path(directory).relative_to(root)
            dirs[:] = [d for d in dirs if production((rel / d).as_posix() + '/')]
            paths.update((rel / file).as_posix() for file in files)
    digest = hashlib.sha256()
    for relative in sorted(p for p in paths if p and production(p)):
        path = root / relative
        digest.update(relative.encode()); digest.update(b'\0')
        digest.update(b'\1' if os.access(path, os.X_OK) else b'\0')
        if path.is_symlink():
            digest.update(os.readlink(path).encode())
        if path.is_file():
            digest.update(b'\1')
            with path.open('rb') as source:
                for block in iter(lambda: source.read(65536), b''):
                    digest.update(block)
        else:
            digest.update(b'\2')
        digest.update(b'\0')
    return digest.hexdigest()

def metadata(root):
    hashed = fingerprint(root)
    head = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=root, capture_output=True, text=True) if (root / '.git').exists() else None
    commit = head.stdout.strip() if head and head.returncode == 0 else 'source-snapshot-sha256:' + hashed
    return ('schema=bc.custom_mod_source.v1\nrepository=better-manufacturing\n'
            'mod_id=better_manufacturing\ncommit=' + commit + '\nproduction_fingerprint=' + hashed + '\n')

if __name__ == '__main__':
    root, output = map(Path, sys.argv[1:])
    target = output / 'META-INF/better-content-source.properties'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(metadata(root.resolve()))
