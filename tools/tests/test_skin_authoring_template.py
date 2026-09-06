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


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise AssertionError(f"Not a PNG: {path}")
    return struct.unpack(">II", header[16:24])


def geometry(region: dict) -> tuple[int, int, int, int]:
    return region["x"], region["y"], region["width"], region["height"]


class SkinAuthoringTemplateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads((ROOT / "skin-authoring" / "AUTHORING_TEMPLATE_SPEC.json").read_text())
        cls.canvas_svg = ET.parse(ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2.svg").getroot()

    def test_production_template_and_reference_are_exact_master_size(self):
        expected = (4720, 4040)
        self.assertEqual(expected, png_size(ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2.png"))
        self.assertEqual(expected, png_size(ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2-reference.png"))
        self.assertEqual("4720", self.canvas_svg.get("width"))
        self.assertEqual("4040", self.canvas_svg.get("height"))

    def test_all_production_template_registrations_match_authoritative_regions(self):
        authoritative = {
            region["slotId"]: geometry(region)
            for region in generator.REGIONS
        }
        spec_regions = {
            region["slotId"]: (
                region["origin"]["x"], region["origin"]["y"],
                region["size"]["width"], region["size"]["height"],
            )
            for region in self.spec["regions"]
        }
        registrations = [
            tuple(int(element.get(key)) for key in ("x", "y", "width", "height"))
            for element in self.canvas_svg.findall(f".//{{{SVG_NS}}}g[@id='REGISTRATION']/{{{SVG_NS}}}rect")
        ]
        self.assertEqual(21, len(authoritative))
        self.assertEqual(authoritative, spec_regions)
        self.assertEqual(list(authoritative.values()), registrations)

    def test_registered_regions_do_not_overlap(self):
        regions = list(map(geometry, generator.REGIONS))
        for index, (x1, y1, width1, height1) in enumerate(regions):
            for x2, y2, width2, height2 in regions[index + 1:]:
                overlaps = x1 < x2 + width2 and x2 < x1 + width1 and y1 < y2 + height2 and y2 < y1 + height1
                self.assertFalse(overlaps)

    def test_production_template_identifies_all_21_regions(self):
        source = (ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2.svg").read_text()
        for region in self.spec["regions"]:
            self.assertIn(region["slotId"], source)

    def test_visible_boundaries_are_outside_registered_regions(self):
        boundaries = self.canvas_svg.findall(f".//{{{SVG_NS}}}g[@id='BOUNDARIES']/{{{SVG_NS}}}rect")
        self.assertEqual(21, len(boundaries))
        for boundary, region in zip(boundaries, generator.REGIONS):
            x, y, width, height = geometry(region)
            self.assertEqual((x - 2, y - 2, width + 4, height + 4), tuple(int(boundary.get(key)) for key in ("x", "y", "width", "height")))

    def test_companion_reference_hit_areas_match_runtime_constants(self):
        runtime_source = (
            ROOT / "app/src/main/java/com/jamesmoran/adventurepad/LowerScreenNavigation.kt"
        ).read_text()

        def runtime_constant(name: str) -> int:
            match = re.search(rf"{name}\s*=\s*(\d+)", runtime_source)
            self.assertIsNotNone(match, f"Missing runtime constant {name}")
            return int(match.group(1))

        header_dp = runtime_constant("COMPANION_HEADER_HEIGHT_DP")
        action_top_offset_dp = runtime_constant("COMPANION_ACTION_TOP_OFFSET_DP")
        action_height_dp = runtime_constant("COMPANION_ACTION_ROW_HEIGHT_DP")
        gap_dp = runtime_constant("COMPANION_ACTION_GAP_DP")
        padding_dp = runtime_constant("COMPANION_ACTION_HORIZONTAL_PADDING_DP")
        density = 2
        canvas_width = 1240
        action_width = (canvas_width - 2 * padding_dp * density - gap_dp * density) // 2
        notes = [
            padding_dp * density,
            (header_dp + action_top_offset_dp) * density,
            padding_dp * density + action_width,
            (header_dp + action_top_offset_dp + action_height_dp) * density,
        ]
        walkthrough_left = notes[2] + gap_dp * density
        walkthrough = [
            walkthrough_left,
            notes[1],
            walkthrough_left + action_width,
            notes[3],
        ]

        layout = json.loads((ROOT / "skin-creator-kit/LAYOUT_SPEC.json").read_text())
        actions = layout["surfaces"]["companion"]["primaryActions"]
        self.assertEqual(action_top_offset_dp, actions["topOffsetDp"])
        self.assertEqual(action_height_dp, actions["rowHeightDp"])
        self.assertEqual(padding_dp, actions["horizontalPaddingDp"])
        self.assertEqual(gap_dp, actions["gapDp"])
        self.assertEqual(notes, actions["notesReferenceRectPx"])
        self.assertEqual(walkthrough, actions["walkthroughReferenceRectPx"])
        self.assertEqual([32, 240, 604, 432], notes)
        self.assertEqual([636, 240, 1208, 432], walkthrough)

        guide = ET.parse(
            ROOT / "skin-creator-kit/templates/source-guides/companion-template.svg"
        ).getroot()
        for element_id, expected in (
            ("companion-notes-hit-area", notes),
            ("companion-walkthrough-hit-area", walkthrough),
        ):
            rect = guide.find(f".//{{{SVG_NS}}}rect[@id='{element_id}']")
            self.assertIsNotNone(rect)
            actual = [
                int(rect.get("x")),
                int(rect.get("y")),
                int(rect.get("x")) + int(rect.get("width")),
                int(rect.get("y")) + int(rect.get("height")),
            ]
            self.assertEqual(expected, actual)

if __name__ == "__main__":
    unittest.main()
