"""Synthetic DEX relationship tests; no real messages or APK data."""
import struct
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import find_history_implementations as finder
from test_inspect_rcs_classes import synthetic_dex


def with_interface(type_index: int = 1, count: int = 1) -> bytes:
    """Apply a valid class_def direct interface list to the DEX fixture."""
    data = bytearray(synthetic_dex())
    interface_list_off = len(data)
    data.extend(struct.pack("<I", count))
    data.extend(struct.pack("<" + ("H" * count), *([type_index] * count)))
    if count % 2:
        data.extend(b"\x00\x00")
    cls_off = struct.unpack_from("<I", data, 0x64)[0]
    struct.pack_into("<I", data, cls_off + 12, interface_list_off)
    struct.pack_into("<I", data, 0x20, len(data))
    return bytes(data)


class RelationshipTests(unittest.TestCase):
    def test_direct_implementation_is_class_definition(self):
        d = finder.DexMetadata(with_interface())
        matches = finder.relationships(d, ("Ljava/lang/String;",))
        self.assertEqual(len(matches["Ljava/lang/String;"]), 1)
        owner, kind, methods = matches["Ljava/lang/String;"][0]
        self.assertIn("ProcessRcsDeliveryReportAction;", owner)
        self.assertEqual(kind, "implements")
        self.assertEqual(methods, ("deliver():V",))

    def test_unrelated_interface_does_not_match(self):
        d = finder.DexMetadata(with_interface())
        matches = finder.relationships(d, ("Lcom/example/Other;",))
        self.assertEqual(matches["Lcom/example/Other;"], [])

    def test_no_implementation_when_interface_list_absent(self):
        d = finder.DexMetadata(synthetic_dex())
        matches = finder.relationships(d, ("Ljava/lang/String;",))
        self.assertEqual(matches["Ljava/lang/String;"], [])

    def test_valid_type_list_exceeding_old_96_limit(self):
        # A 97-entry in-bounds list should not trigger a hard per-class
        # limit. This fixture deliberately repeats a descriptor to test
        # metadata scanning bounds; it is not intended as valid Java code.
        d = finder.DexMetadata(with_interface(count=97))
        rows = finder.relationships(d, ("Ljava/lang/String;",))
        self.assertEqual(len(rows["Ljava/lang/String;"]), 1)

    def test_reject_out_of_bounds_interface_table(self):
        data = bytearray(with_interface())
        cls_off = struct.unpack_from("<I", data, 0x64)[0]
        interface_off = struct.unpack_from("<I", data, cls_off + 12)[0]
        struct.pack_into("<I", data, interface_off, len(data))
        with self.assertRaises(finder.DexFormatError):
            finder.relationships(
                finder.DexMetadata(bytes(data)), ("Ljava/lang/String;",)
            )

    def test_reject_invalid_interface_type_index(self):
        d = finder.DexMetadata(with_interface(type_index=65535))
        with self.assertRaises(finder.DexFormatError):
            finder.relationships(d, ("Ljava/lang/String;",))

    def test_reject_defined_method_with_wrong_owner(self):
        data = bytearray(with_interface())
        method_ids_off = struct.unpack_from("<I", data, 0x5c)[0]
        struct.pack_into("<H", data, method_ids_off, 1)
        with self.assertRaises(finder.DexFormatError):
            finder.relationships(
                finder.DexMetadata(bytes(data)), ("Ljava/lang/String;",)
            )


if __name__ == "__main__":
    unittest.main()
