"""Synthetic DEX tests: no device APK or private data required."""
from pathlib import Path
import importlib.util
import io
import struct
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "inventory_google_messages_dex.py"
spec = importlib.util.spec_from_file_location("dex_inventory", SCRIPT)
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


def fake_dex(descriptors: list[str]) -> bytes:
    """Build enough of a standard DEX 039 for metadata-only parser tests."""
    header = bytearray(0x70)
    header[:8] = b"dex\n039\0"
    struct.pack_into("<I", header, 0x24, 0x70)
    struct.pack_into("<I", header, 0x28, 0x12345678)
    strings_off = len(header)
    types_off = strings_off + 4 * len(descriptors)
    classes_off = types_off + 4 * len(descriptors)
    strings_data_off = classes_off + 32 * len(descriptors)
    string_ids = bytearray()
    type_ids = bytearray()
    class_defs = bytearray()
    strings_data = bytearray()
    for i, value in enumerate(descriptors):
        descriptor = value.encode("utf-8")
        string_ids += struct.pack("<I", strings_data_off + len(strings_data))
        strings_data += bytes([len(value)]) + descriptor + b"\0"
        type_ids += struct.pack("<I", i)
        class_defs += struct.pack("<I", i) + bytes(28)
    data = header + string_ids + type_ids + class_defs + strings_data
    struct.pack_into("<I", data, 0x20, len(data))
    for base, count, off in ((0x38, len(descriptors), strings_off),
                             (0x40, len(descriptors), types_off),
                             (0x60, len(descriptors), classes_off)):
        struct.pack_into("<II", data, base, count, off)
    return bytes(data)


class DexInventoryTest(unittest.TestCase):
    def test_extracts_only_defined_classes(self):
        names = scanner.class_names_in_dex(fake_dex([
            "Lcom/google/android/apps/messaging/shared/api/rcs/RcsTransport;",
            "Lcom/google/android/apps/messaging/ui/ConversationList;",
        ]))
        self.assertEqual(names, [
            "com.google.android.apps.messaging.shared.api.rcs.RcsTransport",
            "com.google.android.apps.messaging.ui.ConversationList",
        ])

    def test_rejects_corrupt_header_and_versions(self):
        with self.assertRaises(scanner.DexFormatError):
            scanner.class_names_in_dex(b"broken")
        raw = bytearray(fake_dex(["Lcom/google/android/apps/messaging/A;"]))
        raw[4:7] = b"041"
        with self.assertRaisesRegex(scanner.DexFormatError, "Unsupported DEX version"):
            scanner.class_names_in_dex(bytes(raw))

    def test_apk_contains_only_class_definitions(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as directory:
            path = Path(directory) / "test.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("classes.dex", fake_dex([
                    "Lcom/google/android/apps/messaging/rcs/RcsService;"
                ]))
                archive.writestr("res/raw/something.dex", b"not classes")
            names, count = scanner.class_names_in_apk(path)
            self.assertEqual(count, 1)
            self.assertIn("com.google.android.apps.messaging.rcs.RcsService", names)

    def test_direct_ims_package_is_an_rcs_candidate(self):
        text = scanner.report({"com.google.android.ims.internal.Transport"}, 1, 1, 5)
        self.assertIn("[RCS / IMS / Jibe] 1 matching", text)

    def test_grouping_excludes_root_package_keyword(self):
        text = scanner.report({
            "com.google.android.apps.messaging.ui.View",
            "com.google.android.apps.messaging.rcs.RcsFeature",
        }, 1, 1, 5)
        self.assertIn("[RCS / IMS / Jibe] 1 matching", text)
        self.assertIn("[Messages / receipts] 0 matching", text)


if __name__ == "__main__":
    unittest.main()
