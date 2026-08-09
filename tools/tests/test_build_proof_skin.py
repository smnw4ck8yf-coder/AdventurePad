import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "build-proof-skin.py"
SPEC = importlib.util.spec_from_file_location("build_proof_skin", SCRIPT)
builder = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(builder)


class ProofSkinBuilderTest(unittest.TestCase):
    def test_authoritative_spec_has_unique_regions(self):
        root = Path(__file__).resolve().parents[2]
        spec = builder.load_spec(root / "skin-authoring" / "AUTHORING_TEMPLATE_SPEC.json")
        self.assertEqual(17, len(spec["regions"]))
        self.assertEqual(17, len({region["slotId"] for region in spec["regions"]}))

    def test_small_spec_builds_from_json_coordinates_and_forces_frame_center_clear(self):
        spec = {
            "schemaVersion": 1,
            "templateVersion": "test",
            "masterCanvas": {"width": 6, "height": 4, "units": "px"},
            "regions": [
                {"index": 1, "slotId": "preview", "origin": {"x": 0, "y": 0},
                 "size": {"width": 2, "height": 2}, "output": {"width": 2, "height": 2, "format": "png"},
                 "alphaMode": "opaque", "scaleMode": "contain"},
                {"index": 2, "slotId": "panel.frame", "origin": {"x": 2, "y": 0},
                 "size": {"width": 4, "height": 4}, "output": {"width": 4, "height": 4, "format": "png"},
                 "alphaMode": "rgba-transparent-center", "scaleMode": "nineSlice",
                 "nineSliceInsets": {"left": 1, "top": 1, "right": 1, "bottom": 1},
                 "transparentCenter": {"x": 1, "y": 1, "width": 2, "height": 2, "requiredAlpha": 0}},
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spec_path = root / "spec.json"
            spec_path.write_text(json.dumps(spec))
            pixels = bytearray((10, 20, 30, 255) * 24)
            master = root / "master.png"
            package = root / "proof.apskin"
            builder.write_rgba_png(master, 6, 4, pixels)
            result = builder.build_skin(master, spec_path, builder.PROOF_METADATA, package)
            self.assertEqual("valid", result["status"])
            self.assertEqual(2, result["regions"])

    def test_rejects_wrong_master_dimensions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spec_path = root / "spec.json"
            spec_path.write_text(json.dumps({
                "schemaVersion": 1, "masterCanvas": {"width": 2, "height": 2},
                "regions": [
                    {"index": 1, "slotId": "preview", "origin": {"x": 0, "y": 0},
                     "size": {"width": 1, "height": 1}, "output": {"width": 1, "height": 1, "format": "png"},
                     "alphaMode": "opaque", "scaleMode": "contain"},
                    {"index": 2, "slotId": "panel.frame", "origin": {"x": 1, "y": 0},
                     "size": {"width": 1, "height": 1}, "output": {"width": 1, "height": 1, "format": "png"},
                     "alphaMode": "rgba", "scaleMode": "contain"},
                ],
            }))
            master = root / "wrong.png"
            builder.write_rgba_png(master, 1, 1, bytearray((0, 0, 0, 255)))
            with self.assertRaises(builder.BuildError):
                builder.build_skin(master, spec_path, builder.PROOF_METADATA, root / "out.apskin")

    def test_adventure_journal_notes_and_walkthrough_are_exact_registered_slices(self):
        root = Path(__file__).resolve().parents[2]
        spec = builder.load_spec(root / "skin-authoring" / "AUTHORING_TEMPLATE_SPEC.json")
        width, height, master = builder.read_rgba_png(root / "proof-skin" / "AdventureJournal-source.png")
        self.assertEqual((4720, 4040), (width, height))
        expected_geometry = {
            "notes.background": (1400, 1420, 1240, 1080),
            "walkthrough.background": (2720, 1420, 1240, 1080),
        }
        for slot, geometry in expected_geometry.items():
            region = next(item for item in spec["regions"] if item["slotId"] == slot)
            self.assertEqual(
                geometry,
                (region["origin"]["x"], region["origin"]["y"], region["size"]["width"], region["size"]["height"]),
            )
            extracted_path = root / "proof-skin" / "adventure-journal-extracted" / builder.asset_path(slot)
            out_width, out_height, extracted = builder.read_rgba_png(extracted_path)
            self.assertEqual((1240, 1080), (out_width, out_height))
            self.assertEqual(
                builder.crop_rgba(master, width, *geometry),
                extracted,
                f"{slot} must come directly from the registered master-PNG crop",
            )


if __name__ == "__main__":
    unittest.main()
