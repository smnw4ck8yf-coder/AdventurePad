import importlib.util
import gc
import json
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "tools" / "build-proof-skin.py"
BUILDER_SPEC = importlib.util.spec_from_file_location("build_proof_skin", BUILDER_PATH)
builder = importlib.util.module_from_spec(BUILDER_SPEC)
assert BUILDER_SPEC.loader is not None
BUILDER_SPEC.loader.exec_module(builder)

SVG_NS = "http://www.w3.org/2000/svg"
PNG = ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2.png"
SVG = ROOT / "skin-authoring" / "AdventurePad-Skin-Template-v2.svg"
SPEC = ROOT / "skin-authoring" / "AUTHORING_TEMPLATE_SPEC.json"


class SkinAuthoringTemplateV2Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads(SPEC.read_text(encoding="utf-8"))
        cls.svg = ET.parse(SVG).getroot()
        cls.width, cls.height = struct.unpack(">II", PNG.read_bytes()[16:24])

    def test_canvas_and_svg_registrations_match_spec_exactly(self):
        canvas = self.spec["masterCanvas"]
        self.assertEqual((canvas["width"], canvas["height"]), (self.width, self.height))
        registrations = self.svg.findall(
            f".//{{{SVG_NS}}}g[@id='REGISTRATION']/{{{SVG_NS}}}rect"
        )
        artwork_groups = self.svg.findall(
            f".//{{{SVG_NS}}}g[@id='ARTWORK']/{{{SVG_NS}}}g"
        )
        self.assertEqual(len(self.spec["regions"]), len(registrations))
        self.assertEqual(
            [region["slotId"] for region in sorted(self.spec["regions"], key=lambda item: item["index"])],
            [group.get("data-slot-id") for group in artwork_groups],
        )
        for region, registration in zip(
            sorted(self.spec["regions"], key=lambda item: item["index"]), registrations
        ):
            origin, size = region["origin"], region["size"]
            self.assertEqual(region["slotId"], registration.get("data-slot-id"))
            self.assertEqual(
                (origin["x"], origin["y"], size["width"], size["height"]),
                tuple(int(registration.get(key)) for key in ("x", "y", "width", "height")),
            )

    def test_all_required_surfaces_and_button_states_are_present(self):
        slots = {region["slotId"] for region in self.spec["regions"]}
        expected = {
            "top.surround", "bottom.trackpad.background", "bottom.split.background",
            "companion.background", "notes.background", "walkthrough.background",
            "button.lmb.normal", "button.lmb.pressed", "button.rmb.normal",
            "button.rmb.pressed", "button.companion.normal", "button.companion.pressed",
            "button.settings.normal", "button.settings.pressed",
            "button.notes.normal", "button.notes.pressed",
            "button.walkthrough.normal", "button.walkthrough.pressed",
        }
        self.assertTrue(expected.issubset(slots))

    def test_v2_registers_exactly_21_regions_and_companion_action_geometry(self):
        regions = {region["slotId"]: region for region in self.spec["regions"]}
        self.assertEqual(21, len(regions))
        layout = json.loads((ROOT / "skin-creator-kit/LAYOUT_SPEC.json").read_text())
        actions = layout["surfaces"]["companion"]["primaryActions"]
        for prefix, reference_key in (
            ("button.notes", "notesReferenceRectPx"),
            ("button.walkthrough", "walkthroughReferenceRectPx"),
        ):
            rect = actions[reference_key]
            expected_size = (rect[2] - rect[0], rect[3] - rect[1])
            self.assertEqual((572, 192), expected_size)
            for state in ("normal", "pressed"):
                region = regions[f"{prefix}.{state}"]
                self.assertEqual(expected_size, (region["size"]["width"], region["size"]["height"]))

    def test_preview_is_opaque_and_other_registered_pixels_are_transparent(self):
        gc.collect()
        width, _, pixels = builder.read_rgba_png(PNG)
        for region in self.spec["regions"]:
            origin, size = region["origin"], region["size"]
            crop = builder.crop_rgba(
                pixels, width, origin["x"], origin["y"],
                size["width"], size["height"],
            )
            alphas = crop[3::4]
            expected_alpha = 255 if region["slotId"] == "preview" else 0
            self.assertTrue(
                all(alpha == expected_alpha for alpha in alphas),
                f'{region["slotId"]} contains unexpected alpha values',
            )

    def test_png_builds_and_validates_as_a_complete_skin(self):
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory) / "template-v2.apskin"
            result = builder.build_skin(
                PNG,
                SPEC,
                {
                    "id": "org.adventurepad.template.v2.validation",
                    "name": "AdventurePad Template v2 Validation",
                    "author": "AdventurePad",
                    "packageVersion": "1.0.0",
                },
                package,
            )
            self.assertEqual("valid", result["status"])
            self.assertEqual(len(self.spec["regions"]), result["regions"])


if __name__ == "__main__":
    unittest.main()
