"""Synthetic DEX relationship tests; no real messages or APK data."""
import struct
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import find_history_implementations as finder
from test_inspect_rcs_classes import synthetic_dex


def with_interface(type_index: int = 1) -> bytes:
    """Apply a valid class_def direct interface list to the DEX fixture."""
    data = bytearray(synthetic_dex())
    interface_list_off = len(data)
    data.extend(struct.pack("<IHH", 1, type_index, 0))
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
