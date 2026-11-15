#!/usr/bin/env python3
"""Locate direct implementations of Q25-verified messaging model interfaces.

Read ONLY installed Google Messages APK DEX metadata. Class relationships
and defined method signatures are examined; no method body is decoded, no
private message DB or content is read, and Google Messages is not modified.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys
from tempfile import TemporaryDirectory
import zipfile

from inspect_rcs_classes import DexMetadata, leb, u16
from inventory_google_messages_dex import (
    DexFormatError, MAX_DEX_BYTES, check_table, installed_apk_paths, u32,
)

# All three types were verified as class definitions in the user's Q25 DEX.
INTERFACES = (
    "Lcom/google/android/apps/messaging/home/dataservice/HomeDataService;",
    "Lcom/google/android/apps/messaging/shared/datamodel/data/common/MessageCoreData;",
    "Lcom/google/android/apps/messaging/shared/datamodel/data/message/part/MessagePartCoreData;",
)
# Interface lists are DEX type_list tables. Class-specific hard limits such
# as 96 can reject valid metadata. Bound by the actual DEX byte length
# instead, and cap the aggregate entries inspected per DEX.
MAX_INTERFACE_ENTRIES_PER_DEX = 1_000_000
MAX_MATCHES_PER_INTERFACE = 256
MAX_METHODS_PER_MATCH = 12
MAX_MEMBERS_PER_CLASS = 100000
NO_INDEX = 0xffffffff


def relationships(dex: DexMetadata, wanted: tuple[str, ...] = INTERFACES
                  ) -> dict[str, list[tuple[str, str, tuple[str, ...]]]]:
    """Return (owner, relationship, bounded defined method signatures).

    A DEX class_def explicitly lists direct interfaces and superclass;
    it does not prove what an implementation does at runtime. This is a
    direct relationship search, not a claim of indirect inheritance.
    """
    result: dict[str, list[tuple[str, str, tuple[str, ...]]]] = {
        name: [] for name in wanted
    }
    inspected_interface_entries = 0
    for i in range(dex.cls_count):
        off = dex.cls_off + i * 32
        class_idx = u32(dex.data, off)
        owner = dex.typ(class_idx)
        parent_idx = u32(dex.data, off + 8)
        parent = None if parent_idx == NO_INDEX else dex.typ(parent_idx)
        interface_off = u32(dex.data, off + 12)
        # Search matching interface *indices* without decoding an unrelated
        # class's entire type list to names. Validate size before any reads.
        direct = set()
        if interface_off:
            count = u32(dex.data, interface_off)
            check_table(dex.data, interface_off + 4, count, 2)
            inspected_interface_entries += count
            if inspected_interface_entries > MAX_INTERFACE_ENTRIES_PER_DEX:
                raise DexFormatError("Interface metadata scan budget exceeded")
            for j in range(count):
                interface_type = dex.typ(u16(
                    dex.data, interface_off + 4 + 2 * j
                ))
                if interface_type in wanted:
                    direct.add(interface_type)
        matched = [
            (name, "implements" if name in direct else "extends")
            for name in wanted
            if owner != name and (name in direct or name == parent)
        ]
        if not matched:
            continue
        methods = tuple(defined_methods(dex, class_idx, u32(dex.data, off + 24)))
        for name, relationship in matched:
            rows = result[name]
            if len(rows) >= MAX_MATCHES_PER_INTERFACE:
                raise DexFormatError("Exceeded bounded interface matches")
            rows.append((owner, relationship, methods))
    return result


def defined_methods(dex: DexMetadata, owner_idx: int, class_data_off: int
                    ) -> list[str]:
    """Extract up to 12 defined method signatures, never code or values."""
    if class_data_off == 0:
        return []
    if class_data_off >= len(dex.data):
        raise DexFormatError("Class data offset outside DEX")
    pos = class_data_off
    stat, pos = leb(dex.data, pos)
    inst, pos = leb(dex.data, pos)
    direct, pos = leb(dex.data, pos)
    virtual, pos = leb(dex.data, pos)
    if max(stat, inst, direct, virtual) > MAX_MEMBERS_PER_CLASS:
        raise DexFormatError("Excessive DEX members")
    for count in (stat, inst):
        for _ in range(count):
            _, pos = leb(dex.data, pos)
            _, pos = leb(dex.data, pos)
    seen = []
    for count in (direct, virtual):
        current = 0
        for _ in range(count):
            delta, pos = leb(dex.data, pos)
            current += delta
            _, pos = leb(dex.data, pos)  # access flags
            _, pos = leb(dex.data, pos)  # code pointer (never followed)
            if current >= dex.method_count:
                raise DexFormatError("Invalid encoded method index")
            method_off = dex.method_off + current * 8
            if u16(dex.data, method_off) != owner_idx:
                raise DexFormatError("Encoded method owner mismatch")
            if len(seen) >= MAX_METHODS_PER_MATCH:
                continue
            name, ret, params = dex.methods(current)
            if name in ("<init>", "<clinit>"):
                continue
            seen.append(f"{name}({', '.join(params)}):{ret}")
    return seen


def from_apk(path: Path) -> tuple[dict[str, list[tuple]], int]:
    found: dict[str, list[tuple]] = {name: [] for name in INTERFACES}
    count = 0
    with zipfile.ZipFile(path) as archive:
        for meta in archive.infolist():
            if not re.fullmatch(r"classes(?:[0-9]+)?\.dex", meta.filename):
                continue
            if meta.file_size > MAX_DEX_BYTES:
                raise DexFormatError("DEX exceeds safe input size")
            count += 1
            for name, rows in relationships(
                DexMetadata(archive.read(meta))
            ).items():
                found[name].extend(rows)
                if len(found[name]) > MAX_MATCHES_PER_INTERFACE:
                    raise DexFormatError("Too many implementations")
    return found, count


def report(all_matches: dict[str, list[tuple]], apks: int, dexes: int) -> str:
    lines = [
        f"APK files={apks}; DEX files={dexes}",
        "DIRECT DEX class relationships only; no RCS history access verified.",
    ]
    for interface in INTERFACES:
        matches = sorted(set(all_matches.get(interface, [])))
        lines.append(f"\n[interface] {interface} directMatches={len(matches)}")
        for owner, relation, signatures in matches[:20]:
            lines.append(f"  {owner} {relation}")
            for method in signatures[:MAX_METHODS_PER_MATCH]:
                lines.append(f"    {method}")
        if len(matches) > 20:
            lines.append(f"  ... {len(matches) - 20} additional direct matches omitted")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    input_source = parser.add_mutually_exclusive_group(required=True)
    input_source.add_argument("--serial", help="ADB serial for installed APK scan")
    input_source.add_argument("--apk", type=Path, help="Local APK for offline scan")
    args = parser.parse_args(argv)
    try:
        merged: dict[str, list[tuple]] = {name: [] for name in INTERFACES}
        apks, dexes = 0, 0
        if args.apk:
            parts, dexes = from_apk(args.apk)
            apks = 1
            for name, matches in parts.items():
                merged[name].extend(matches)
        else:
            with TemporaryDirectory(prefix="bbmessages-interfaces-") as temp:
                paths = installed_apk_paths(args.serial)
                apks = len(paths)
                for idx, remote in enumerate(paths):
                    local = Path(temp) / f"code-{idx}.apk"
                    subprocess.run(
                        ["adb", "-s", args.serial, "pull", remote, str(local)],
                        capture_output=True, check=True,
                    )
                    parts, count = from_apk(local)
                    dexes += count
                    for name, matches in parts.items():
                        merged[name].extend(matches)
                        if len(merged[name]) > MAX_MATCHES_PER_INTERFACE:
                            raise DexFormatError("Too many interface matches")
                    local.unlink()
        if not dexes:
            raise DexFormatError("No code DEX files found")
        print(report(merged, apks, dexes))
    except (OSError, RuntimeError, ValueError, zipfile.BadZipFile,
            subprocess.CalledProcessError) as problem:
        message = str(problem) if isinstance(problem, DexFormatError) \
            else type(problem).__name__
        print(f"Interface scan unavailable: {message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
