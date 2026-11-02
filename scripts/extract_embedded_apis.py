#!/usr/bin/env python3
"""Extract only pinned compile APIs from the pinned, cached native mod archives."""
import hashlib
from pathlib import Path
import sys
from zipfile import ZipFile

APIS = (
    ("valkyrienskies-120-2.4.11.jar", "f99f24de62015451a047f90484cf9d25970cac350105b581c22b2d5247ebfd53",
     "META-INF/jars/api-1.1.0+cf208d8b56.jar", "e53d131bc7d0bcf9a5470c81b960a327aabfe51a467250d390d7fb35ac6f201e", "valkyrienskies-api-1.1.0.jar"),
    ("clockwork-0.5.6.jar", "a7a7f946eb9edbe63d281a47b710c8958cd4b6b8dbbb73165fcc73d53fa07093",
     "META-INF/jars/kelvin-forge-1.20.1-0.5.0+5c1c62c654.jar", "eec798ce7284accb890babb569736e23e44c023fd50fe2fe502dd85bd0addf12", "kelvin-forge-1.20.1-0.5.0.jar"),
)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def extract(cache: Path, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    for archive, expected_archive, member, expected_member, name in APIS:
        source = cache / archive
        raw = source.read_bytes()  # Missing cached native mods fail, never fall back to stale .tmp jars.
        if digest(raw) != expected_archive:
            raise ValueError(f"Pinned upstream archive changed: {source}")
        with ZipFile(source) as nested:
            data = nested.read(member)
        if digest(data) != expected_member:
            raise ValueError(f"Pinned embedded API changed: {archive}!/{member}")
        target = output / name
        if not target.is_file() or digest(target.read_bytes()) != expected_member:
            temp = output / (name + ".pending")
            temp.write_bytes(data)
            temp.replace(target)
        print(f"Pinned API: {source.name}!/{member} -> {target}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: extract_embedded_apis.py CACHE_DIR OUTPUT_DIR")
    extract(Path(sys.argv[1]), Path(sys.argv[2]))
