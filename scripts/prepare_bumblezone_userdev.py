#!/usr/bin/env python3
"""Create an isolated, fail-closed Bumblezone *userdev-only* selector remap.

ForgeGradle maps class/member bytecode to official names, but this pinned jar has
no mixin refmap and its annotation strings still name SRG methods and fields.
Never change the pinned pack jar, ForgeGradle's cache, mixin configs, or gameplay.
"""
from __future__ import annotations

from collections import Counter
from copy import copy
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import re
import struct
import sys
from zipfile import ZipFile

RAW_SHA = "04cff7be7d9dfbf775af1b6ef5818d1d296913a9705ba3fd38c71947c88050e1"
MAPPED_SHA = "75a61256ba1b41ca2e3f82a5d82774edc215d26cec4d80c18b48c50bfc848ce5"
MAPPING_SHA = "b211d4b0be560832747b378ca3490986e0662d9fd50888903be27b46b22df301"
EXPECTED_TOKENS = {"m": 117, "f": 67}
CONFIGS = ("the_bumblezone-common.mixins.json", "the_bumblezone-forge.mixins.json")
OUTPUT_NAME = "the_bumblezone-7.13.4+1.20.1-forge-userdev-selectors.jar"
TOKEN = re.compile(rb"(?<![A-Za-z0-9_$])([mf]_\d+_)(?![A-Za-z0-9_$])")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def checked(path: Path, expected: str) -> bytes:
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"Expected regular, non-symlink pinned input: {path}")
    data = path.read_bytes()
    if sha(data) != expected:
        raise ValueError(f"Pinned input drift: {path}, expected {expected}, found {sha(data)}")
    return data


def names(mapping: bytes) -> dict[bytes, bytes]:
    result: dict[bytes, bytes] = {}
    for line in mapping.decode("utf-8").splitlines():
        columns = line.split()
        if line.startswith("MD: "):
            original, official = columns[1].rsplit("/", 1)[-1], columns[3].rsplit("/", 1)[-1]
        elif line.startswith("FD: "):
            original, official = columns[1].rsplit("/", 1)[-1], columns[2].rsplit("/", 1)[-1]
        else:
            continue
        if TOKEN.fullmatch(original.encode()):
            key, value = original.encode(), official.encode()
            if key in result and result[key] != value:
                raise ValueError(f"Ambiguous srg->official mapping: {original}")
            result[key] = value
    return result


def remap_class(data: bytes, mapping: dict[bytes, bytes], seen: set[bytes]) -> bytes:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java classfile")
    count = struct.unpack_from(">H", data, 8)[0]
    parts = [data[:10]]
    offset = 10
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:  # CONSTANT_Utf8: rewrite only names present in official mapping.
            length = struct.unpack_from(">H", data, offset)[0]
            value = data[offset + 2:offset + 2 + length]
            def replace(match: re.Match[bytes]) -> bytes:
                key = match.group(1)
                if key not in mapping:
                    raise ValueError(f"Unmapped mixin selector token {key!r}")
                seen.add(key)
                return mapping[key]
            changed = TOKEN.sub(replace, value)
            if len(changed) > 65535:
                raise ValueError("Mapped UTF8 constant exceeds JVM limit")
            parts.append(b"\x01" + struct.pack(">H", len(changed)) + changed)
            offset += 2 + length
        else:
            size = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4,
                    10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}.get(tag)
            if size is None or offset + size > len(data):
                raise ValueError(f"Unsupported or truncated constant-pool tag: {tag}")
            parts.append(bytes([tag]) + data[offset:offset + size])
            offset += size
            if tag in (5, 6):
                index += 1
        index += 1
    parts.append(data[offset:])
    result = b"".join(parts)
    if TOKEN.search(result):
        # A raw byte scan can hit arbitrary method descriptors; all mapped
        # constants must at least have been rewritten before the run.
        raise ValueError("Mapped mixin class still contains an SRG selector token")
    return result


def publish(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() or path.is_symlink():
        if path.is_symlink() or not path.is_file() or path.read_bytes() != content:
            raise ValueError(f"Refusing different existing generated artifact: {path}")
        return
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o644)
    with os.fdopen(fd, "wb") as stream:
        stream.write(content)


def main(raw: Path, mapped: Path, srg: Path, root: Path) -> None:
    root = root.resolve()
    if root == raw.resolve() or root == mapped.resolve() or raw.resolve().is_relative_to(root) or mapped.resolve().is_relative_to(root):
        raise ValueError("Generated root overlaps pinned source/cache")
    checked(raw, RAW_SHA)
    source = checked(mapped, MAPPED_SHA)
    mapping = names(checked(srg, MAPPING_SHA))
    seen: set[bytes] = set()
    changed: list[str] = []
    with ZipFile(BytesIO(source)) as original:
        infos = original.infolist()
        if len(infos) != 6789 or len({i.filename for i in infos}) != len(infos) or sum(i.filename.endswith(".class") for i in infos) != 949:
            raise ValueError("Pinned mapped Bumblezone JAR inventory drift")
        if any(i.filename.startswith("META-INF/") and i.filename.upper().endswith((".SF", ".RSA", ".DSA")) for i in infos):
            raise ValueError("Signed jar must not be rewritten")
        for name in CONFIGS:
            if json.loads(original.read(name)).get("refmap") is not None:
                raise ValueError("Pinned mixin config gained a refmap; do not synthesize selectors")
        output = BytesIO()
        with ZipFile(output, "w", allowZip64=True) as target:
            target.comment = original.comment
            for info in infos:
                value = original.read(info)
                if info.filename.startswith("com/telepathicgrunt/the_bumblezone/mixin/") and info.filename.endswith(".class"):
                    revised = remap_class(value, mapping, seen)
                    if value != revised:
                        changed.append(info.filename)
                    value = revised
                target.writestr(copy(info), value)
    counts = Counter(key[:1].decode() for key in seen)
    if dict(counts) != EXPECTED_TOKENS or not changed or not any(name.endswith("/ServerWorldMixin.class") for name in changed):
        raise ValueError(f"Incomplete mapped mixin inventory: {dict(counts)}, {len(changed)} changed classes")
    derived = output.getvalue()
    with ZipFile(BytesIO(source)) as old, ZipFile(BytesIO(derived)) as new:
        for info in infos:
            name = info.filename
            if name not in changed and old.read(name) != new.read(name):
                raise ValueError(f"Non-mixin or config resource changed: {name}")
        for name in CONFIGS + ("META-INF/MANIFEST.MF", "META-INF/mods.toml"):
            if old.read(name) != new.read(name):
                raise ValueError(f"Bumblezone registration/config changed: {name}")
    artifact = root / OUTPUT_NAME
    receipt = root / "provenance.json"
    record = {"scope": "ISOLATED_OFFICIAL_MAPPED_USERDEV_ONLY_NO_PRODUCTION_JAR_CHANGE",
              "pinned_raw_sha256": RAW_SHA, "mapped_input_sha256": MAPPED_SHA,
              "mapping_sha256": MAPPING_SHA, "output_sha256": sha(derived),
              "changed_mixin_classes": changed, "distinct_selector_tokens": dict(counts),
              "helper_sha256": sha(Path(__file__).read_bytes())}
    publish(artifact, derived)
    publish(receipt, (json.dumps(record, indent=2, sort_keys=True) + "\n").encode())
    checked(raw, RAW_SHA)
    checked(mapped, MAPPED_SHA)
    print(f"DEV_BUMBLEZONE_USERDEV={artifact} SHA256={sha(derived)} MIXINS={len(changed)} SRG_SELECTORS={dict(counts)}")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 5:
            raise ValueError("Usage: prepare_bumblezone_userdev.py PINNED.jar FG_MAPPED.jar output.srg GENERATED_DIR")
        main(*(Path(arg) for arg in sys.argv[1:]))
    except (ValueError, OSError, KeyError, struct.error) as error:
        print("DEV_BUMBLEZONE_USERDEV_REJECTED: " + str(error), file=sys.stderr)
        sys.exit(1)
