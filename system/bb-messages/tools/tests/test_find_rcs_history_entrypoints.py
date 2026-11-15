"""Tests for discovery of actually defined DEX methods, not references."""
import struct
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import find_rcs_history_entrypoints as finder
from test_inspect_rcs_classes import synthetic_dex


def fixture(return_string: bool = False, param_string: bool = False) -> bytes:
    data = bytearray(synthetic_dex())
    proto_off = struct.unpack_from("<I", data, 0x4c)[0]
    if return_string:
        struct.pack_into("<I", data, proto_off + 4, 1)
    if param_string:
        param_off = len(data)
        data.extend(struct.pack("<IH", 1, 1))
        data.extend(b"\0\0")
        struct.pack_into("<I", data, proto_off + 8, param_off)
    struct.pack_into("<I", data, 0x20, len(data))
    return bytes(data)


class DefinedReferrerTest(unittest.TestCase):
    def test_only_defined_returning_method_is_found(self):
        watches = (("result", "return", "Ljava/lang/String;"),)
        hits = finder.defined_method_hits(
            finder.DexMetadata(fixture(return_string=True)),
            watches=watches,
        )
        self.assertEqual(
            hits["result"],
            [("L" + finder_source_owner() + ";", "deliver", "Ljava/lang/String;", ())],
        )

    def test_method_id_reference_alone_does_not_count(self):
        data = bytearray(fixture(return_string=True))
        cls_off = struct.unpack_from("<I", data, 0x64)[0]
        cd_off = struct.unpack_from("<I", data, cls_off + 24)[0]
        # class_data: static=0, instance=1, direct=1, virtual=0.
        # Removing the sole encoded definition leaves method_ids unchanged.
        data[cd_off + 2] = 0
        watches = (("result", "return", "Ljava/lang/String;"),)
        hits = finder.defined_method_hits(
            finder.DexMetadata(bytes(data)), watches=watches,
        )
        self.assertEqual(hits["result"], [])

    def test_detects_parameter_type(self):
        watches = (("input", "param", "Ljava/lang/String;"),)
        hits = finder.defined_method_hits(
            finder.DexMetadata(fixture(param_string=True)),
            watches=watches,
        )
        self.assertEqual(len(hits["input"]), 1)
        self.assertEqual(hits["input"][0][1], "deliver")
        self.assertEqual(hits["input"][0][3], ("Ljava/lang/String;",))

    def test_no_matches_for_unrelated_descriptor(self):
        watches = (("other", "return", "Lsome/OtherType;"),)
        hits = finder.defined_method_hits(
            finder.DexMetadata(fixture()), watches=watches,
        )
        self.assertEqual(hits["other"], [])

    def test_named_and_short_owner_reporting(self):
        rows = {
            "returns_core": [
                ("Lcom/google/Foo;", "a", "V", ()),
                ("Labcd;", "b", "V", ()),
            ],
        }
        text = finder.output_summary(rows, 4, 12)
        self.assertIn("named_owners=1 short_owners=1", text)
        self.assertIn("named Lcom/google/Foo;.a():V", text)
        self.assertIn("short Labcd;.b():V", text)


def finder_source_owner() -> str:
    return finder_target().replace(".", "/")


def finder_target() -> str:
    # Same fixture class used by the existing DEX inspection tests.
    from inspect_rcs_classes import TARGETS
    return TARGETS[2]


if __name__ == "__main__":
    unittest.main()
