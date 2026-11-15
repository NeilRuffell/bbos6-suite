#!/usr/bin/env python3
"""Offline, metadata-only inventory of installed Google Messages DEX classes.

Read *class definition names only* from APK classes*.dex files using Python's
standard library. Does not inspect method bodies, message data, permissions,
Android databases, contacts, accounts, credentials, or APK string constants.

APK copies obtained via ADB are deleted automatically after the scan.
No Google Messages app process is started/stopped or modified.
"""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import re
import struct
import subprocess
import sys
from tempfile import TemporaryDirectory
import zipfile

PACKAGE = "com.google.android.apps.messaging"
PREFIXES = (
    "com.google.android.apps.messaging.",
    "com.google.android.ims.",
    "com.google.android.rcs.",
    "com.google.android.apps.jibe.",
    "com.google.rcs.",
)
GROUPS = {
    "RCS / IMS / Jibe": ("rcs", "ims", "jibe"),
    "Conversations / participants": ("conversation", "participant", "thread"),
    "Messages / receipts": ("message", "receipt", "deliver", "readstate"),
    "Typing / presence / capabilities": ("typing", "presence", "capabilit"),
    "Attachments / file transfer": ("attachment", "filetransfer", "media"),
    "Persistence / synchronization": ("storage", "database", "repository", "sync"),
}
MAX_DEX_BYTES = 128 * 1024 * 1024
DEX_MAGIC = b"dex\n"
MAX_CLASSES_PER_DEX = 500_000


class DexFormatError(ValueError):
    """Malformed / unsupported DEX metadata (no fallback to raw strings)."""


def u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise DexFormatError("DEX metadata extends outside file")
    return struct.unpack_from("<I", data, offset)[0]


def check_table(data: bytes, offset: int, count: int, stride: int) -> None:
    if count and (offset == 0 or offset > len(data) or count > (len(data) - offset) // stride):
        raise DexFormatError("DEX metadata table out of bounds")


def read_uleb128(data: bytes, offset: int) -> int:
    value = 0
    for index in range(5):
        if offset + index >= len(data):
            raise DexFormatError("Truncated DEX uleb128")
        byte = data[offset + index]
        value |= (byte & 0x7F) << (7 * index)
        if byte & 0x80 == 0:
            return offset + index + 1
    raise DexFormatError("Overlong DEX uleb128")


def class_names_in_dex(data: bytes) -> list[str]:
    """Extract class_def names only; never decode general string constants.

    Supports ordinary dex versions 035–040. DEX v041 containers are deliberately
    rejected until a separate, verified container parser is implemented.
    """
    if len(data) < 0x70 or data[:4] != DEX_MAGIC or data[7] != 0:
        raise DexFormatError("Not a supported DEX header")
    version = data[4:7]
    if not version.isdigit() or not (35 <= int(version) <= 40):
        raise DexFormatError("Unsupported DEX version: " + version.decode("ascii", "replace"))
    if u32(data, 0x20) != len(data):
        raise DexFormatError("DEX file_size mismatch")
    if u32(data, 0x24) != 0x70 or u32(data, 0x28) != 0x12345678:
        raise DexFormatError("Unsupported DEX header / endianness")

    strings_size, strings_off = u32(data, 0x38), u32(data, 0x3C)
    types_size, types_off = u32(data, 0x40), u32(data, 0x44)
    classes_size, classes_off = u32(data, 0x60), u32(data, 0x64)
    if classes_size > MAX_CLASSES_PER_DEX:
        raise DexFormatError("Unreasonable DEX class count")
    check_table(data, strings_off, strings_size, 4)
    check_table(data, types_off, types_size, 4)
    check_table(data, classes_off, classes_size, 32)

    results: list[str] = []
    for index in range(classes_size):
        type_index = u32(data, classes_off + 32 * index)
        if type_index >= types_size:
            raise DexFormatError("Class index outside type table")
        string_index = u32(data, types_off + 4 * type_index)
        if string_index >= strings_size:
            raise DexFormatError("Type index outside string table")
        descriptor_offset = u32(data, strings_off + 4 * string_index)
        string_start = read_uleb128(data, descriptor_offset)
        # We decode only referenced *class descriptors*, never message strings.
        string_end = data.find(b"\0", string_start, min(len(data), string_start + 1024))
        if string_end < 0:
            raise DexFormatError("Invalid class descriptor length")
        descriptor = data[string_start:string_end].decode("utf-8", "replace")
        if descriptor.startswith("L") and descriptor.endswith(";"):
            results.append(descriptor[1:-1].replace("/", "."))
    return results


def class_names_in_apk(apk: Path) -> tuple[set[str], int]:
    names: set[str] = set()
    dex_files = 0
    with zipfile.ZipFile(apk) as archive:
        for meta in archive.infolist():
            if not re.fullmatch(r"classes(?:[0-9]+)?\.dex", meta.filename):
                continue
            if meta.file_size > MAX_DEX_BYTES:
                raise DexFormatError("DEX exceeds safe input size")
            dex_files += 1
            names.update(class_names_in_dex(archive.read(meta)))
    return names, dex_files


def installed_apk_paths(serial: str) -> list[str]:
    cmd = ["adb", "-s", serial, "shell", "pm", "path", PACKAGE]
    response = subprocess.run(cmd, capture_output=True, text=True, check=True)
    paths = [line.removeprefix("package:").strip()
             for line in response.stdout.splitlines() if line.startswith("package:")]
    if not paths:
        raise RuntimeError("No installed Google Messages APK paths returned by adb")
    if len(paths) > 64:
        raise RuntimeError("Unexpected number of APK paths")
    return paths


def report(names: set[str], dex_count: int, apk_count: int, sample: int) -> str:
    eligible = sorted(name for name in names if name.startswith(PREFIXES))
    obfuscated = sum(1 for name in eligible
                     if re.fullmatch(r"[a-z]{1,4}", name.rsplit(".", 1)[-1]))
    output = [
        f"APK files inspected: {apk_count}; DEX files: {dex_count}",
        f"Total defined classes: {len(names)}",
        f"Google Messages / IMS / RCS package classes: {len(eligible)}",
        f"Short lowercase class names in those packages: {obfuscated}",
        "Class-name matches are candidates ONLY, not evidence of usable RCS APIs.",
    ]
    for title, words in GROUPS.items():
        # Exclude the mandatory root package prefix from keyword matching.
        matching = [name for name in eligible if any(
            word in name.removeprefix("com.google.android.apps.messaging.").lower()
            for word in words)]
        output.append(f"\n[{title}] {len(matching)} matching defined classes")
        output.extend(f"  {name}" for name in matching[:sample])
        if len(matching) > sample:
            output.append(f"  ... {len(matching) - sample} additional matches omitted")
    return "\n".join(output)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--serial", help="ADB device serial (installed APK; no app data)")
    group.add_argument("--apk", type=Path, help="Local APK for offline class metadata analysis")
    parser.add_argument("--sample", type=int, default=12,
                        help="Max matching class names per category (default: 12)")
    options = parser.parse_args(argv)
    if not 1 <= options.sample <= 60:
        parser.error("--sample must be between 1 and 60")
    try:
        if options.apk is not None:
            names, dex_count = class_names_in_apk(options.apk)
            apk_count = 1
        else:
            with TemporaryDirectory(prefix="bbmessages-dex-") as location:
                names, dex_count = set(), 0
                paths = installed_apk_paths(options.serial)
                apk_count = len(paths)
                for index, path in enumerate(paths):
                    local = Path(location) / f"part-{index:02d}.apk"
                    subprocess.run(
                        ["adb", "-s", options.serial, "pull", path, str(local)],
                        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
                        text=True, check=True,
                    )
                    part_names, part_dex = class_names_in_apk(local)
                    names.update(part_names)
                    dex_count += part_dex
                    local.unlink()
        if dex_count == 0:
            raise RuntimeError("No classes*.dex found in the APKs")
        print(report(names, dex_count, apk_count, options.sample))
    except (OSError, ValueError, zipfile.BadZipFile,
            subprocess.CalledProcessError, RuntimeError) as exc:
        if isinstance(exc, DexFormatError):
            # Safe, constant diagnostics. Never print APK bytes or private data.
            print(f"DEX inventory unavailable: {exc}", file=sys.stderr)
        else:
            print(f"DEX inventory unavailable: {type(exc).__name__}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
