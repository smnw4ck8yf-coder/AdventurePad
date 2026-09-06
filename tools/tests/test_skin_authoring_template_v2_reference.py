import importlib.util
import json
from pathlib import Path
import re
import struct
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate-skin-authoring-template.py"
GENERATOR_SPEC = importlib.util.spec_from_file_location("generate_skin_authoring_template", GENERATOR_PATH)
generator = importlib.util.module_from_spec(GENERATOR_SPEC)
assert GENERATOR_SPEC.loader is not None
GENERATOR_SPEC.loader.exec_module(generator)

SVG_NS = "http://www.w3.org/2000/svg"
SVG = ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2-reference.svg"
PNG = ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2-reference.png"
SPEC = ROOT / "skin-authoring" / "AUTHORING_TEMPLATE_SPEC.json"
COMPANION_GUIDE = ROOT / "skin-creator-kit/templates/source-guides/companion-template.svg"


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise AssertionError(f"Not a PNG: {path}")
    return struct.unpack(">II", header[16:24])


class SkinAuthoringTemplateV2ReferenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads(SPEC.read_text(encoding="utf-8"))
        cls.svg = ET.parse(SVG).getroot()

    def test_reference_canvas_and_registrations_match_authoritative_spec(self):
        canvas = self.spec["masterCanvas"]
        self.assertEqual((canvas["width"], canvas["height"]), png_size(PNG))
        registrations = self.svg.findall(
            f".//{{{SVG_NS}}}g[@id='REGISTRATION']/{{{SVG_NS}}}rect"
        )
        regions = sorted(self.spec["regions"], key=lambda item: item["index"])
        self.assertEqual(len(regions), len(registrations))
        for region, registration in zip(regions, registrations):
            origin, size = region["origin"], region["size"]
            self.assertEqual(region["slotId"], registration.get("data-slot-id"))
            self.assertEqual(
                (origin["x"], origin["y"], size["width"], size["height"]),
                tuple(int(registration.get(key)) for key in ("x", "y", "width", "height")),
            )

    def test_every_reference_overlay_group_is_clipped_to_its_crop(self):
        expected_slots = {region["slotId"] for region in self.spec["regions"]}
        guide_groups = self.svg.findall(f".//{{{SVG_NS}}}g[@id='GUIDES']/{{{SVG_NS}}}g")
        label_groups = [
            group for group in self.svg.findall(f".//{{{SVG_NS}}}g[@id='LABELS']/{{{SVG_NS}}}g")
            if group.get("data-slot-id")
        ]
        self.assertEqual(expected_slots, {group.get("data-slot-id") for group in guide_groups})
        self.assertEqual(expected_slots, {group.get("data-slot-id") for group in label_groups})
        for group in guide_groups + label_groups:
            self.assertRegex(group.get("clip-path", ""), r"^url\(#crop-clip-(?:local|global)-")

    def test_reference_contains_every_required_surface_and_button_state(self):
        source = SVG.read_text(encoding="utf-8").upper()
        required_labels = (
            "TOP GAMEPLAY / TOP.SURROUND", "BOTTOM TRACKPAD", "BOTTOM SPLIT VIEW",
            "COMPANION", "NOTES", "WALKTHROUGH", "TRACKPAD SURFACE",
            "PANEL FRAME", "CATALOG PREVIEW", "LMB NORMAL", "LMB PRESSED",
            "RMB NORMAL", "RMB PRESSED", "COMPANION NORMAL", "COMPANION PRESSED",
            "SETTINGS NORMAL", "SETTINGS PRESSED",
            "NOTES NORMAL", "NOTES PRESSED",
            "WALKTHROUGH NORMAL", "WALKTHROUGH PRESSED",
        )
        for label in required_labels:
            self.assertIn(label, source)

    def test_companion_guide_matches_runtime_and_documents_current_controls(self):
        runtime = (
            ROOT / "app/src/main/java/com/jamesmoran/adventurepad/LowerScreenNavigation.kt"
        ).read_text(encoding="utf-8")
        design = (
            ROOT / "app/src/main/java/com/jamesmoran/adventurepad/ui/theme/AdventurePadDesign.kt"
        ).read_text(encoding="utf-8")

        def integer(source: str, name: str) -> int:
            match = re.search(rf"{name}\s*=\s*(\d+)", source)
            self.assertIsNotNone(match, f"Missing runtime value {name}")
            return int(match.group(1))

        header_dp = integer(runtime, "COMPANION_HEADER_HEIGHT_DP")
        action_top_offset_dp = integer(runtime, "COMPANION_ACTION_TOP_OFFSET_DP")
        action_dp = integer(runtime, "COMPANION_ACTION_ROW_HEIGHT_DP")
        padding_dp = integer(runtime, "COMPANION_ACTION_HORIZONTAL_PADDING_DP")
        gap_dp = integer(runtime, "COMPANION_ACTION_GAP_DP")
        content_padding_dp = integer(design, "contentPadding")
        utility_target_dp = integer(design, "utilityTouchTarget")
        layout = json.loads((ROOT / "skin-creator-kit/LAYOUT_SPEC.json").read_text(encoding="utf-8"))
        companion = layout["surfaces"]["companion"]
        self.assertEqual(header_dp, companion["header"]["heightDp"])
        self.assertEqual(action_top_offset_dp, companion["primaryActions"]["topOffsetDp"])
        self.assertEqual(action_dp, companion["primaryActions"]["rowHeightDp"])
        self.assertEqual(padding_dp, companion["primaryActions"]["horizontalPaddingDp"])
        self.assertEqual(gap_dp, companion["primaryActions"]["gapDp"])
        self.assertEqual(content_padding_dp, padding_dp)

        guide = ET.parse(COMPANION_GUIDE).getroot()
        expected_rects = {
            "companion-header-art-safe-area",
            "companion-header-close-hit-area",
            "companion-notes-hit-area",
            "companion-notes-art-safe-area",
            "companion-walkthrough-hit-area",
            "companion-walkthrough-art-safe-area",
        }
        actual_rects = {
            element.get("id") for element in guide.findall(f".//{{{SVG_NS}}}rect")
            if element.get("id")
        }
        self.assertTrue(expected_rects.issubset(actual_rects))
        close = guide.find(f".//{{{SVG_NS}}}rect[@id='companion-header-close-hit-area']")
        self.assertIsNotNone(close)
        density = 2
        close_width = 48 * density
        close_height = utility_target_dp * density
        self.assertEqual(
            (
                1240 - padding_dp * density - close_width,
                (header_dp * density - close_height) // 2,
                close_width,
                close_height,
            ),
            tuple(int(close.get(key)) for key in ("x", "y", "width", "height")),
        )
        guide_text = COMPANION_GUIDE.read_text(encoding="utf-8").upper()
        for phrase in (
            "HEADER 72 DP", "CLOSE HIT TARGET IS RUNTIME-OWNED",
            "NOTES BUTTON", "WALKTHROUGH BUTTON", "RECOMMENDED ART SAFE",
            "DYNAMIC", "STANDARD SUPPLIES VISIBLE HEADER/BUTTONS",
        ):
            self.assertIn(phrase, guide_text)

        reference_text = SVG.read_text(encoding="utf-8").upper()
        self.assertIn(
            "COMPANION AND SETTINGS EACH USE SEPARATE NORMAL/PRESSED ASSETS SHOWN BELOW",
            reference_text,
        )


if __name__ == "__main__":
    unittest.main()
