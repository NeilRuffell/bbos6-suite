#!/usr/bin/env python3
"""Find defined Google Messages methods referencing verified message model types.

Read ONLY DEX metadata: class_def, encoded_method indices, proto/parameter
types, names. Does not read method bodies, message values or app databases.
Signatures are research candidates, not evidence of operational RCS APIs.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
import re
import subprocess
import sys
from tempfile import TemporaryDirectory
import zipfile

from inspect_rcs_classes import DexMetadata, leb, u16
from inventory_google_messages_dex import (
    DexFormatError, MAX_DEX_BYTES, installed_apk_paths, u32,
)

# Grounded in Q25 11/15 model/action signatures. No guessed class names.
WATCHES = (
    ("returns_core", "return", "Lcom/google/android/apps/messaging/shared/datamodel/data/common/MessageCoreData;"),
    ("returns_part", "return", "Lcom/google/android/apps/messaging/shared/datamodel/data/message/part/MessagePartCoreData;"),
    ("takes_conversation_id", "param", "Lcom/google/android/apps/messaging/shared/datamodel/data/datatypes/ConversationIdType;"),
    ("takes_message_id", "param", "Lcom/google/android/apps/messaging/shared/datamodel/data/datatypes/MessageIdType;"),
)
MAX_CLASS_MEMBERS = 200_000
MAX_TOTAL_HITS = 100_000
SAMPLE_PER_KIND = 10


def defined_method_hits(
    dex: DexMetadata,
    watches: tuple[tuple[str, str, str], ...] = WATCHES,
) -> dict[str, list[tuple[str, str, str, tuple[str, ...]]]]:
    """Only report encoded methods belonging to real class_def entries.

    DEX method_ids also contain *references* to external methods; those do
    not qualify. We intersect candidate method_ids with class_data_item's
    actually defined direct/virtual method index lists.
    """
    result: dict[str, list[tuple[str, str, str, tuple[str, ...]]]] = {
        key: [] for key, _, _ in watches
    }
    known = {}
    for key, kind, descriptor in watches:
        if kind not in ("return", "param"):
            raise ValueError("Unknown watch kind")
        known.setdefault(descriptor, []).append((key, kind))

    interested_types: dict[int, list[tuple[str, str]]] = {}
    for index in range(dex.typ_count):
        descriptor = dex.typ(index)
        if descriptor in known:
            interested_types[index] = known[descriptor]
    if not interested_types:
        return result

    interesting_protos: dict[int, tuple[str, ...]] = {}
    for index in range(dex.proto_count):
        offset = dex.proto_off + index * 12
        return_index = u32(dex.data, offset + 4)
        tags = {
            key for key, kind in interested_types.get(return_index, ())
            if kind == "return"
        }
        parameter_list_offset = u32(dex.data, offset + 8)
        if parameter_list_offset:
            count = u32(dex.data, parameter_list_offset)
            if count > 255 or count > (
                len(dex.data) - parameter_list_offset - 4
            ) // 2:
                raise DexFormatError("Invalid DEX prototype parameter list")
            for i in range(count):
                type_index = u16(
                    dex.data, parameter_list_offset + 4 + i * 2
                )
                tags.update(
                    key for key, kind in interested_types.get(type_index, ())
                    if kind == "param"
                )
        if tags:
            interesting_protos[index] = tuple(sorted(tags))
    if not interesting_protos:
        return result

    # Restrict detailed class_data parsing to class owners of potentially
    # relevant method IDs. DEX method IDs are not by themselves definitions.
    candidates: dict[int, dict[int, tuple[str, ...]]] = defaultdict(dict)
    for index in range(dex.method_count):
        offset = dex.method_off + index * 8
        proto_index = u16(dex.data, offset + 2)
        if proto_index >= dex.proto_count:
            raise DexFormatError("Method references invalid prototype")
        categories = interesting_protos.get(proto_index)
        if categories:
            class_type_index = u16(dex.data, offset)
            candidates[class_type_index][index] = categories

    for i in range(dex.cls_count):
        offset = dex.cls_off + 32 * i
        class_index = u32(dex.data, offset)
        wanted_methods = candidates.get(class_index)
        if not wanted_methods:
            continue
        class_data_offset = u32(dex.data, offset + 24)
        if not class_data_offset:
            continue
        if class_data_offset >= len(dex.data):
            raise DexFormatError("Invalid DEX class_data offset")

        position = class_data_offset
        static_count, position = leb(dex.data, position)
        instance_count, position = leb(dex.data, position)
        direct_count, position = leb(dex.data, position)
        virtual_count, position = leb(dex.data, position)
        if max(static_count, instance_count, direct_count, virtual_count) > MAX_CLASS_MEMBERS:
            raise DexFormatError("Excessive DEX class member count")

        for count in (static_count, instance_count):
            for _ in range(count):
                _, position = leb(dex.data, position)  # field_id delta
                _, position = leb(dex.data, position)  # access flags

        owner = dex.typ(class_index)
        for count in (direct_count, virtual_count):
            method_index = 0
            for _ in range(count):
                delta, position = leb(dex.data, position)
                method_index += delta
                _, position = leb(dex.data, position)  # access flags
                _, position = leb(dex.data, position)  # code offset, NEVER read
                categories = wanted_methods.get(method_index)
                if not categories:
                    continue
                if method_index >= dex.method_count:
                    raise DexFormatError("Invalid defined method index")
                method_owner_idx = u16(
                    dex.data, dex.method_off + method_index * 8
                )
                if method_owner_idx != class_index:
                    raise DexFormatError("Defined method owner mismatch")
                name, return_type, parameters = dex.methods(method_index)
                # Constructors are not conversation-history lookup APIs.
                if name in ("<init>", "<clinit>"):
                    continue
                signature = (owner, name, return_type, tuple(parameters))
                for key in categories:
                    result[key].append(signature)
                    if sum(len(v) for v in result.values()) > MAX_TOTAL_HITS:
                        raise DexFormatError("Too many candidate signatures")
    return result


def scan_apk(path: Path) -> tuple[dict[str, list[tuple]], int]:
    collected: dict[str, list[tuple]] = {name: [] for name, _, _ in WATCHES}
    dex_count = 0
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            if not re.fullmatch(r"classes(?:[0-9]+)?\.dex", item.filename):
                continue
            if item.file_size > MAX_DEX_BYTES:
                raise DexFormatError("DEX exceeds safe input size")
            dex_count += 1
            for key, rows in defined_method_hits(
                DexMetadata(archive.read(item))
            ).items():
                collected[key].extend(rows)
    return collected, dex_count


def output_summary(hits: dict[str, list[tuple]], apks: int, dexes: int) -> str:
    output = [
        f"APK files={apks}; DEX files={dexes}",
        "Only DEFINED method signatures; not code, data, or verified RCS APIs.",
    ]
    for name, _, _ in WATCHES:
        rows = sorted(set(hits.get(name, [])))
        readable = [
            r for r in rows if "/" in r[0][1:-1]
        ]
        obfuscated = [
            r for r in rows if "/" not in r[0][1:-1]
        ]
        output.append(
            f"\n[{name}] total={len(rows)} "
            f"named_owners={len(readable)} short_owners={len(obfuscated)}"
        )
        for kind, entries in (("named", readable), ("short", obfuscated)):
            for owner, method, ret, params in entries[:SAMPLE_PER_KIND]:
                output.append(
                    f"  {kind} {owner}.{method}({','.join(params)}):{ret}"
                )
            if len(entries) > SAMPLE_PER_KIND:
                output.append(
                    f"  ... {len(entries) - SAMPLE_PER_KIND} more {kind} matches omitted"
                )
    return "\n".join(output)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--serial", help="ADB serial for installed Google Messages")
    source.add_argument("--apk", type=Path, help="Offline local Google Messages APK")
    args = parser.parse_args(argv)
    try:
        merged: dict[str, list[tuple]] = {
            key: [] for key, _, _ in WATCHES
        }
        apk_count, dex_count = 0, 0
        if args.apk is not None:
            sources = [args.apk]
            apk_count = 1
            for path in sources:
                part, n = scan_apk(path)
                dex_count += n
                for key, rows in part.items():
                    merged[key].extend(rows)
        else:
            with TemporaryDirectory(prefix="bbmessages-model-owners-") as tmp:
                paths = installed_apk_paths(args.serial)
                apk_count = len(paths)
                for i, remote_path in enumerate(paths):
                    local = Path(tmp) / f"part-{i:02d}.apk"
                    subprocess.run(
                        ["adb", "-s", args.serial, "pull",
                         remote_path, str(local)],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.PIPE, text=True, check=True,
                    )
                    part, n = scan_apk(local)
                    dex_count += n
                    for key, rows in part.items():
                        merged[key].extend(rows)
                    local.unlink()
        if not dex_count:
            raise DexFormatError("No DEX definitions found")
        print(output_summary(merged, apk_count, dex_count))
    except (ValueError, OSError, RuntimeError, zipfile.BadZipFile,
            subprocess.CalledProcessError) as failure:
        reason = str(failure) if isinstance(failure, DexFormatError) else type(failure).__name__
        print(f"Method-owner discovery unavailable: {reason}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
