#!/usr/bin/env python3
"""Offline, bounded DEX field/method metadata for Q25-observed RCS classes.

No code execution, instructions, database access, object values, or private data.
Only class_data_item's *defined* fields/methods are reported (not references).
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import struct
import subprocess
import sys
from tempfile import TemporaryDirectory
import zipfile

from inventory_google_messages_dex import (
    DexFormatError, MAX_DEX_BYTES, check_table, installed_apk_paths, u32,
)

# Every name in this allowlist appeared in the user's installed APK inventory.
ACTION_TARGETS = (
    "com.google.android.apps.messaging.shared.api.messaging.message.send.insert.FastPathRcsSendMessageAction",
    "com.google.android.apps.messaging.shared.datamodel.action.InsertRcsMessageInTelephonyAction",
    "com.google.android.apps.messaging.shared.datamodel.action.ProcessRcsDeliveryReportAction",
    "com.google.android.apps.messaging.shared.datamodel.action.ForwardIncomingTypingIndicatorToTachyonAction",
    "com.google.android.apps.messaging.shared.datamodel.action.ProcessFileTransferAction",
    "com.google.android.apps.messaging.shared.datamodel.action.PauseRcsFileTransferAction",
    "com.google.android.apps.messaging.shared.datamodel.action.ResumeRcsFileTransferAction",
    "com.google.android.apps.messaging.shared.datamodel.action.ProcessConversationUpdateAsyncAction",
    "com.google.android.apps.messaging.external.p2p.impl.ExternalRcsEndpointService",
    "com.google.android.apps.messaging.rcsmigration.RcsStateProvider",
    "com.google.android.ims.rcsservice.contacts.ImsCapabilities",
)

# Q25-confirmed through actual action constructors / runtime type traces,
# or through named defined classes in the earlier APK inventory.
# Referenced descriptors may be MISSING from DEX class_def; that is valid.
MODEL_TARGETS = (
    "com.google.android.apps.messaging.shared.datamodel.data.common.MessageCoreData",
    "com.google.android.apps.messaging.shared.datamodel.data.datatypes.ConversationIdType",
    "com.google.android.apps.messaging.shared.datamodel.data.datatypes.MessageIdType",
    "com.google.android.ims.rcsservice.chatsession.ChatSessionMessageEvent",
    "com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId",
    "com.google.android.apps.messaging.shared.api.messaging.conversation.bugle.BugleConversationId",
    "com.google.android.apps.messaging.shared.datamodel.action.ProcessMessageUpdateAsyncAction",
    "com.google.android.apps.messaging.backup.unified.MessagesBackupAndRestoreService",
)

# Q25-observed named method owners or verified model/part descriptors.
# The parent MessagesTable name is inferred from the verified nested
# InsertableBindData descriptor; a missing class is valid and expected to
# be reported explicitly.
HISTORY_TARGETS = (
    "com.google.android.apps.messaging.shared.datamodel.data.MessageData",
    "com.google.android.apps.messaging.shared.datamodel.data.MessagePartData",
    "com.google.android.apps.messaging.shared.datamodel.data.message.part.MessagePartCoreData",
    "com.google.android.apps.messaging.home.dataservice.HomeDataService",
    "com.google.android.apps.messaging.shared.datamodel.databasegen.tabledefinitions.MessagesTable$InsertableBindData",
    "com.google.android.apps.messaging.shared.datamodel.databasegen.tabledefinitions.MessagesTable",
)

TARGETS = ACTION_TARGETS + MODEL_TARGETS + HISTORY_TARGETS
TARGET_DESCRIPTORS = {"L" + s.replace(".", "/") + ";": s for s in TARGETS}

def focused_targets(focus: str) -> tuple[str, ...]:
    if focus == "models":
        return MODEL_TARGETS
    if focus == "actions":
        return ACTION_TARGETS
    if focus == "history":
        return HISTORY_TARGETS
    if focus == "all":
        return TARGETS
    raise ValueError("Unknown focus")
MAX_FIELDS = 20
MAX_METHODS = 32
MAX_PARAM_TYPES = 8
MAX_DEX_BYTES_LOCAL = MAX_DEX_BYTES


def u16(data: bytes, off: int) -> int:
    if off < 0 or off + 2 > len(data):
        raise DexFormatError("DEX u16 outside file")
    return struct.unpack_from("<H", data, off)[0]


def leb(data: bytes, pos: int) -> tuple[int, int]:
    result = 0
    for i in range(5):
        if pos + i >= len(data):
            raise DexFormatError("Truncated DEX LEB128")
        byte = data[pos + i]
        result |= (byte & 0x7f) << (7 * i)
        if byte & 0x80 == 0:
            if result > 0xFFFFFFFF:
                raise DexFormatError("DEX LEB128 overflow")
            return result, pos + i + 1
    raise DexFormatError("Overlong DEX LEB128")


class DexMetadata:
    def __init__(self, data: bytes):
        self.data = data
        if len(data) < 0x70 or data[:4] != b"dex\n" or data[7] != 0:
            raise DexFormatError("Unsupported DEX header")
        v = data[4:7]
        if not v.isdigit() or not 35 <= int(v) <= 40:
            raise DexFormatError("Unsupported DEX version")
        if u32(data, 0x20) != len(data) or u32(data, 0x24) != 0x70 or u32(data, 0x28) != 0x12345678:
            raise DexFormatError("Invalid DEX header")
        self.str_count, self.str_off = u32(data, 0x38), u32(data, 0x3c)
        self.typ_count, self.typ_off = u32(data, 0x40), u32(data, 0x44)
        self.proto_count, self.proto_off = u32(data, 0x48), u32(data, 0x4c)
        self.field_count, self.field_off = u32(data, 0x50), u32(data, 0x54)
        self.method_count, self.method_off = u32(data, 0x58), u32(data, 0x5c)
        self.cls_count, self.cls_off = u32(data, 0x60), u32(data, 0x64)
        for count, offset, stride in (
            (self.str_count, self.str_off, 4),
            (self.typ_count, self.typ_off, 4),
            (self.proto_count, self.proto_off, 12),
            (self.field_count, self.field_off, 8),
            (self.method_count, self.method_off, 8),
            (self.cls_count, self.cls_off, 32),
        ):
            check_table(data, offset, count, stride)
        if self.cls_count > 500000:
            raise DexFormatError("Excessive DEX classes")
        self._cache: dict[int, str] = {}

    def string(self, index: int) -> str:
        if not 0 <= index < self.str_count:
            raise DexFormatError("Invalid string index")
        if index not in self._cache:
            offset = u32(self.data, self.str_off + 4 * index)
            if offset >= len(self.data):
                raise DexFormatError("Invalid string data offset")
            _, start = leb(self.data, offset)
            end = self.data.find(b"\x00", start, min(len(self.data), start + 1024))
            if end < 0:
                raise DexFormatError("Invalid metadata name")
            self._cache[index] = self.data[start:end].decode("utf-8", "replace")
        return self._cache[index]

    def typ(self, index: int) -> str:
        if not 0 <= index < self.typ_count:
            raise DexFormatError("Invalid type index")
        return self.string(u32(self.data, self.typ_off + index * 4))

    def fields(self, index: int) -> tuple[str, str]:
        if not 0 <= index < self.field_count:
            raise DexFormatError("Invalid field index")
        off = self.field_off + index * 8
        return self.string(u32(self.data, off + 4)), self.typ(u16(self.data, off + 2))

    def methods(self, index: int) -> tuple[str, str, list[str]]:
        if not 0 <= index < self.method_count:
            raise DexFormatError("Invalid method index")
        off = self.method_off + index * 8
        proto_idx = u16(self.data, off + 2)
        if proto_idx >= self.proto_count:
            raise DexFormatError("Invalid proto index")
        poff = self.proto_off + 12 * proto_idx
        ret = self.typ(u32(self.data, poff + 4))
        list_off = u32(self.data, poff + 8)
        params: list[str] = []
        if list_off:
            n = u32(self.data, list_off)
            check_table(self.data, list_off + 4, n, 2)
            if n > 255:
                raise DexFormatError("Excessive method parameter count")
            for i in range(min(n, MAX_PARAM_TYPES)):
                params.append(self.typ(u16(self.data, list_off + 4 + i * 2)))
            if n > MAX_PARAM_TYPES:
                params.append("...")
        return self.string(u32(self.data, off + 4)), ret, params

    def target_summaries(self) -> dict[str, tuple[list[str], list[str]]]:
        result: dict[str, tuple[list[str], list[str]]] = {}
        for i in range(self.cls_count):
            off = self.cls_off + 32 * i
            class_name = TARGET_DESCRIPTORS.get(self.typ(u32(self.data, off)))
            if class_name is None:
                continue
            class_data_off = u32(self.data, off + 24)
            fields: list[str] = []
            methods: list[str] = []
            if class_data_off:
                if class_data_off >= len(self.data):
                    raise DexFormatError("Invalid class_data offset")
                pos = class_data_off
                stat, pos = leb(self.data, pos)
                inst, pos = leb(self.data, pos)
                direct, pos = leb(self.data, pos)
                virtual, pos = leb(self.data, pos)
                if max(stat, inst, direct, virtual) > 200000:
                    raise DexFormatError("Excessive class_data member count")
                for count in (stat, inst):
                    current_idx = 0
                    for _ in range(count):
                        diff, pos = leb(self.data, pos)
                        current_idx += diff
                        access, pos = leb(self.data, pos)
                        if len(fields) < MAX_FIELDS:
                            name, typ = self.fields(current_idx)
                            fields.append(f"  field {name}: {typ} access=0x{access:x}")
                        elif current_idx >= self.field_count:
                            raise DexFormatError("Invalid encoded field")
                for count in (direct, virtual):
                    current_idx = 0
                    for _ in range(count):
                        diff, pos = leb(self.data, pos)
                        current_idx += diff
                        access, pos = leb(self.data, pos)
                        _code_off, pos = leb(self.data, pos)  # never inspect bytecode
                        if len(methods) < MAX_METHODS:
                            name, ret, params = self.methods(current_idx)
                            methods.append(f"  method {name}({', '.join(params)}): {ret} access=0x{access:x}")
                        elif current_idx >= self.method_count:
                            raise DexFormatError("Invalid encoded method")
            if class_name in result:
                raise DexFormatError("Duplicate target class definition")
            result[class_name] = fields, methods
        return result


def summaries_from_apk(path: Path) -> tuple[dict[str, tuple[list[str], list[str]]], int]:
    found: dict[str, tuple[list[str], list[str]]] = {}
    count = 0
    with zipfile.ZipFile(path) as archive:
        for meta in archive.infolist():
            if not re.fullmatch(r"classes(?:[0-9]+)?\.dex", meta.filename):
                continue
            if meta.file_size > MAX_DEX_BYTES_LOCAL:
                raise DexFormatError("DEX exceeds safe input size")
            count += 1
            for name, value in DexMetadata(archive.read(meta)).target_summaries().items():
                if name in found:
                    raise DexFormatError("Duplicate class across DEX files")
                found[name] = value
    return found, count


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--serial", help="ADB serial for installed Google Messages APK")
    choice.add_argument("--apk", type=Path, help="Offline local APK")
    parser.add_argument("--focus", choices=("actions", "models", "history", "all"),
                        default="actions",
                        help="Choose actions, models, history-related named owners, or all")
    args = parser.parse_args(argv)
    try:
        if args.apk is not None:
            names, dexes = summaries_from_apk(args.apk)
            apks = 1
        else:
            names, dexes, apks = {}, 0, 0
            with TemporaryDirectory(prefix="bbmessages-rcs-classes-") as directory:
                paths = installed_apk_paths(args.serial)
                apks = len(paths)
                for i, path in enumerate(paths):
                    local = Path(directory) / f"code-{i}.apk"
                    subprocess.run(
                        ["adb", "-s", args.serial, "pull", path, str(local)],
                        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
                        text=True, check=True,
                    )
                    found, n = summaries_from_apk(local)
                    if names.keys() & found.keys():
                        raise DexFormatError("Duplicate target class across APK splits")
                    names.update(found)
                    dexes += n
                    local.unlink()
        if dexes == 0:
            raise DexFormatError("No DEX files found")
        requested = focused_targets(args.focus)
        count = sum(1 for name in requested if name in names)
        print(f"APK files={apks}; DEX files={dexes}; focus={args.focus}; "
              f"named targets={count}/{len(requested)}")
        for name in requested:
            if name not in names:
                print(f"\n[MISSING] {name}")
                continue
            fields, methods = names[name]
            print(f"\n[DEFINED] {name}")
            print("\n".join(fields) if fields else "  no encoded instance/static fields")
            print("\n".join(methods) if methods else "  no encoded direct/virtual methods")
        print("\nSignatures prove definitions only, NOT RCS history, messaging, receipts or media access.")
    except (OSError, ValueError, zipfile.BadZipFile,
            subprocess.CalledProcessError, RuntimeError) as exc:
        message = str(exc) if isinstance(exc, DexFormatError) else type(exc).__name__
        print(f"RCS signature inspection unavailable: {message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
