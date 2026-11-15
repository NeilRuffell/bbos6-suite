"""Standard-library synthetic DEX tests; zero device or account data."""
import struct
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import inspect_rcs_classes as tool


def uleb(value: int) -> bytes:
    result = []
    while True:
        b = value & 0x7f
        value >>= 7
        if value:
            result.append(b | 0x80)
        else:
            result.append(b)
            return bytes(result)


def synthetic_dex() -> bytes:
    target = tool.TARGETS[2]
    strings = [
        "L" + target.replace(".", "/") + ";",  # 0 class descriptor
        "Ljava/lang/String;",                       # 1 field type
        "V",                                        # 2 return type
        "status",                                   # 3 field name
        "deliver",                                  # 4 method name
    ]
    stringids = bytearray()
    typeids = b"".join(struct.pack("<I", i) for i in (0, 1, 2))
    proto = struct.pack("<III", 2, 2, 0)  # () -> void
    fieldid = struct.pack("<HHI", 0, 1, 3)
    methodid = struct.pack("<HHI", 0, 0, 4)
    header = bytearray(0x70)
    header[:8] = b"dex\n039\0"
    struct.pack_into("<II", header, 0x24, 0x70, 0x12345678)
    str_off = 0x70
    typ_off = str_off + 4 * len(strings)
    proto_off = typ_off + len(typeids)
    field_off = proto_off + len(proto)
    method_off = field_off + len(fieldid)
    cls_off = method_off + len(methodid)
    str_data_off = cls_off + 32
    str_data = bytearray()
    for name in strings:
        raw = name.encode()
        stringids += struct.pack("<I", str_data_off + len(str_data))
        str_data += uleb(len(raw)) + raw + b"\x00"
    cls_data_off = str_data_off + len(str_data)
    classdef = struct.pack("<IIIIIIII", 0, 1, 0xffffffff, 0, 0xffffffff, 0, cls_data_off, 0)
    classdata = b"".join(map(uleb, [0, 1, 1, 0, 0, 1, 0, 1, 0]))
    data = header + stringids + typeids + proto + fieldid + methodid + classdef + str_data + classdata
    struct.pack_into("<I", data, 0x20, len(data))
    for off, count, start in (
        (0x38, len(strings), str_off), (0x40, 3, typ_off),
        (0x48, 1, proto_off), (0x50, 1, field_off),
        (0x58, 1, method_off), (0x60, 1, cls_off),
    ):
        struct.pack_into("<II", data, off, count, start)
    return bytes(data)


class InspectorTest(unittest.TestCase):
    def test_only_defined_members(self):
        found = tool.DexMetadata(synthetic_dex()).target_summaries()
        self.assertEqual(list(found), [tool.TARGETS[2]])
        fields, methods = found[tool.TARGETS[2]]
        self.assertEqual(fields, ["  field status: Ljava/lang/String; access=0x1"])
        self.assertEqual(methods, ["  method deliver(): V access=0x1"])

    def test_invalid_version_rejected(self):
        data = bytearray(synthetic_dex())
        data[4:7] = b"041"
        with self.assertRaises(tool.DexFormatError):
            tool.DexMetadata(bytes(data))

    def test_invalid_method_reference_rejected(self):
        data = bytearray(synthetic_dex())
        # Defined encoded method index is outside method_ids table.
        off = struct.unpack_from("<I", data, 0x64)[0] + 24
        cls_data_off = struct.unpack_from("<I", data, off)[0]
        data[cls_data_off + 6] = 2
        with self.assertRaises(tool.DexFormatError):
            tool.DexMetadata(bytes(data)).target_summaries()

    def test_apk_selects_dex_definitions(self):
        with TemporaryDirectory() as location:
            apk = Path(location) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("classes.dex", synthetic_dex())
                z.writestr("assets/strings.txt", "not data to parse")
            names, count = tool.summaries_from_apk(apk)
            self.assertEqual(count, 1)
            self.assertIn(tool.TARGETS[2], names)

    def test_invalid_header_is_rejected(self):
        b = bytearray(synthetic_dex())
        b[7] = 1
        with self.assertRaises(tool.DexFormatError):
            tool.DexMetadata(bytes(b))


if __name__ == "__main__":
    unittest.main()
