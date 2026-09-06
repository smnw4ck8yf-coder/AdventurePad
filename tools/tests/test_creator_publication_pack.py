import json
import re
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
SPEC_PATH = ROOT / "skin-authoring" / "AUTHORING_TEMPLATE_SPEC.json"
CUSTOM_SKINS = ROOT / "docs" / "CUSTOM_SKINS.md"
SKIN_REFERENCE = ROOT / "docs" / "SKIN_REFERENCE.md"
EXAMPLE = ROOT / "skin-authoring" / "AdventurePad-Skin-Example.png"
CREATOR_LICENSE = ROOT / "CREATOR_ASSETS_LICENSE.md"


def png_size(path: Path):
    with path.open("rb") as source:
        signature = source.read(24)
    if signature[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"not a PNG: {path}")
    return struct.unpack(">II", signature[16:24])


class CreatorPublicationPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
        cls.custom = CUSTOM_SKINS.read_text(encoding="utf-8")
        cls.reference = SKIN_REFERENCE.read_text(encoding="utf-8")

    def test_canonical_canvas_and_region_count(self):
        self.assertEqual({"width": 4720, "height": 4040, "units": "px"}, self.spec["masterCanvas"])
        self.assertEqual(21, len(self.spec["regions"]))
        self.assertEqual(list(range(1, 22)), [region["index"] for region in self.spec["regions"]])

    def test_all_regions_are_in_bounds_and_do_not_overlap(self):
        width = self.spec["masterCanvas"]["width"]
        height = self.spec["masterCanvas"]["height"]
        regions = self.spec["regions"]
        for region in regions:
            x, y = region["origin"].values()
            w, h = region["size"].values()
            self.assertGreaterEqual(x, 0)
            self.assertGreaterEqual(y, 0)
            self.assertLessEqual(x + w, width)
            self.assertLessEqual(y + h, height)
        for index, first in enumerate(regions):
            ax, ay = first["origin"].values()
            aw, ah = first["size"].values()
            for second in regions[index + 1 :]:
                bx, by = second["origin"].values()
                bw, bh = second["size"].values()
                overlaps = ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah
                self.assertFalse(overlaps, f"{first['slotId']} overlaps {second['slotId']}")

    def test_reference_lists_every_slot_and_origin(self):
        for region in self.spec["regions"]:
            origin = region["origin"]
            size = region["output"]
            self.assertIn(f"`{region['slotId']}`", self.reference)
            self.assertIn(f"{origin['x']}, {origin['y']}", self.reference)
            self.assertIn(f"{size['width']}×{size['height']}", self.reference)

    def test_protected_geometry_and_nine_slice_values_are_documented(self):
        required = (
            "x=320…1600",
            "0.5%",
            "alpha 32",
            "width `1112`, height `232`",
            "96/96/96/96",
            "64/64/64/64",
            "48/48/48/48",
            "24/24/24/24",
            "32/32/32/32",
            "every pixel must be opaque",
        )
        lowered = self.reference.lower()
        for phrase in required:
            self.assertIn(phrase.lower(), lowered)

    def test_beginner_guide_has_one_png_workflow_without_stale_creator_instructions(self):
        self.assertIn("one 4720×4040 PNG", self.custom)
        self.assertIn("not the PSD and not 21 separate images", self.custom)
        self.assertIn("DPI is irrelevant", self.custom)
        self.assertIn("sRGB is recommended", self.custom)
        self.assertNotRegex(self.custom, re.compile(r"\b17[- ]region", re.IGNORECASE))
        self.assertNotIn("skin.json.template", self.custom)
        self.assertNotRegex(self.custom, re.compile(r"export (?:the )?21 separate", re.IGNORECASE))

    def test_public_example_is_exact_master_size(self):
        self.assertEqual((4720, 4040), png_size(EXAMPLE))

    def test_creator_material_license_has_scope_and_exclusions(self):
        terms = CREATOR_LICENSE.read_text(encoding="utf-8")
        for phrase in (
            "CC BY 4.0",
            "AdventurePad / James Moran",
            "ScummVM",
            "commercial game artwork",
            "trademarks",
        ):
            self.assertIn(phrase, terms)

    def test_generated_pack_includes_creator_license_and_expected_payload(self):
        psd = ROOT / "skin-authoring" / "test-master.psd"
        with tempfile.TemporaryDirectory() as directory:
            temporary_psd = Path(directory) / psd.name
            temporary_psd.write_bytes(b"creator-pack-test-psd")
            output = Path(directory) / "pack.zip"
            subprocess.run(
                [
                    "python3",
                    str(ROOT / "tools" / "generate-skin-creator-kit.py"),
                    "--psd",
                    str(temporary_psd),
                    "--output",
                    str(output),
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            with ZipFile(output) as archive:
                names = set(archive.namelist())
                prefix = "AdventurePad-Skin-Creator-Pack/"
                self.assertIn(prefix + "CREATOR_ASSETS_LICENSE.md", names)
                self.assertIn(prefix + "AdventurePad-Skin-Template.psd", names)
                self.assertIn(prefix + "skin-authoring/AdventurePad-Skin-Example.png", names)
                self.assertIn(prefix + "skin-authoring/AUTHORING_TEMPLATE_SPEC.json", names)
                self.assertIn(prefix + "docs/CUSTOM_SKINS.md", names)
                self.assertIn(prefix + "docs/SKIN_REFERENCE.md", names)
                self.assertTrue(any(name.startswith(prefix + "skin-creator-kit/templates/") for name in names))
                self.assertEqual(
                    b"creator-pack-test-psd",
                    archive.read(prefix + "AdventurePad-Skin-Template.psd"),
                )
                readme = archive.read(prefix + "README.md").decode("utf-8")
                self.assertIn("](CREATOR_ASSETS_LICENSE.md)", readme)
                forbidden_parts = (".DS_Store", "__MACOSX", "/history/", "-v1")
                self.assertFalse(any(any(part in name for part in forbidden_parts) for name in names))
                self.assertFalse(any(name.lower().endswith((".apskin", ".apk")) for name in names))
                self.assertFalse(any(name.startswith("/") or re.match(r"^[A-Za-z]:", name) for name in names))


if __name__ == "__main__":
    unittest.main()
