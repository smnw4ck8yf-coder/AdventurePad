#!/usr/bin/env python3
"""Generate the canonical one-PNG authoring documents.

This creates the labelled reference sheet and clean creator canvas only. It
does not slice artwork, build .apskin archives, or modify runtime behavior.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
KIT = ROOT / "skin-creator-kit"
GUIDES = KIT / "templates" / "source-guides"
OUT = ROOT / "skin-authoring"

SVG_NS = "http://www.w3.org/2000/svg"
INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape"
ET.register_namespace("", SVG_NS)
ET.register_namespace("inkscape", INKSCAPE_NS)

MASTER_WIDTH = 4720
MASTER_HEIGHT = 4040
V2_SVG = "AdventurePad-Skin-Template-v2.svg"
V2_PNG = "AdventurePad-Skin-Template-v2.png"
V2_REFERENCE_SVG = "AdventurePad-Skin-Template-v2-reference.svg"
V2_REFERENCE_PNG = "AdventurePad-Skin-Template-v2-reference.png"

LAYOUT_SPEC = json.loads((KIT / "LAYOUT_SPEC.json").read_text(encoding="utf-8"))
COMPANION_ACTIONS = LAYOUT_SPEC["surfaces"]["companion"]["primaryActions"]
REFERENCE_DENSITY = int(LAYOUT_SPEC["coordinateConventions"]["referenceScenario"]["densityPxPerDp"])
NOTES_ACTION_RECT = COMPANION_ACTIONS["notesReferenceRectPx"]
WALKTHROUGH_ACTION_RECT = COMPANION_ACTIONS["walkthroughReferenceRectPx"]
COMPANION_ACTION_WIDTH = NOTES_ACTION_RECT[2] - NOTES_ACTION_RECT[0]
COMPANION_ACTION_HEIGHT = NOTES_ACTION_RECT[3] - NOTES_ACTION_RECT[1]
COMPANION_ACTION_NINE_SLICE = COMPANION_ACTIONS["horizontalPaddingDp"] * REFERENCE_DENSITY


REGIONS = [
    {"slotId": "top.surround", "title": "TOP GAMEPLAY SURROUND", "x": 80, "y": 180, "width": 1920, "height": 1080, "scaleMode": "fill", "alphaMode": "rgba-runtime-clipped", "guide": "top-gameplay-template.svg"},
    {"slotId": "bottom.trackpad.background", "title": "BOTTOM TRACKPAD", "x": 2080, "y": 180, "width": 1240, "height": 1080, "scaleMode": "cover", "alphaMode": "rgba", "guide": "bottom-trackpad-template.svg"},
    {"slotId": "bottom.split.background", "title": "BOTTOM SPLIT VIEW", "x": 3400, "y": 180, "width": 1240, "height": 1080, "scaleMode": "cover", "alphaMode": "rgba", "guide": "bottom-split-template.svg"},
    {"slotId": "companion.background", "title": "COMPANION", "x": 80, "y": 1420, "width": 1240, "height": 1080, "scaleMode": "cover", "alphaMode": "rgba", "guide": "companion-template.svg"},
    {"slotId": "notes.background", "title": "NOTES", "x": 1400, "y": 1420, "width": 1240, "height": 1080, "scaleMode": "cover", "alphaMode": "rgba", "guide": "notes-template.svg"},
    {"slotId": "walkthrough.background", "title": "WALKTHROUGH", "x": 2720, "y": 1420, "width": 1240, "height": 1080, "scaleMode": "cover", "alphaMode": "rgba", "guide": "walkthrough-template.svg"},
    {"slotId": "trackpad.surface", "title": "TRACKPAD SURFACE", "x": 80, "y": 2660, "width": 1240, "height": 720, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 96, "top": 96, "right": 96, "bottom": 96}, "customGuide": "trackpad"},
    {"slotId": "panel.frame", "title": "PANEL FRAME", "x": 1400, "y": 2660, "width": 1240, "height": 360, "scaleMode": "nineSlice", "alphaMode": "rgba-transparent-center", "nineSliceInsets": {"left": 64, "top": 64, "right": 64, "bottom": 64}, "guide": "panel-frame-template.svg"},
    {"slotId": "preview", "title": "CATALOG PREVIEW", "x": 2720, "y": 2660, "width": 1200, "height": 675, "scaleMode": "contain", "alphaMode": "opaque", "required": True, "customGuide": "preview"},
    {"slotId": "button.lmb.normal", "title": "LMB NORMAL", "x": 80, "y": 3560, "width": 528, "height": 224, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 48, "top": 48, "right": 48, "bottom": 48}, "guide": "button-lmb-normal-template.svg"},
    {"slotId": "button.lmb.pressed", "title": "LMB PRESSED", "x": 688, "y": 3560, "width": 528, "height": 224, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 48, "top": 48, "right": 48, "bottom": 48}, "guide": "button-lmb-pressed-template.svg"},
    {"slotId": "button.rmb.normal", "title": "RMB NORMAL", "x": 1296, "y": 3560, "width": 528, "height": 224, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 48, "top": 48, "right": 48, "bottom": 48}, "guide": "button-rmb-normal-template.svg"},
    {"slotId": "button.rmb.pressed", "title": "RMB PRESSED", "x": 1904, "y": 3560, "width": 528, "height": 224, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 48, "top": 48, "right": 48, "bottom": 48}, "guide": "button-rmb-pressed-template.svg"},
    {"slotId": "button.companion.normal", "title": "COMPANION NORMAL", "x": 2512, "y": 3560, "width": 264, "height": 112, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 24, "top": 24, "right": 24, "bottom": 24}, "guide": "button-companion-normal-template.svg"},
    {"slotId": "button.companion.pressed", "title": "COMPANION PRESSED", "x": 2856, "y": 3560, "width": 264, "height": 112, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 24, "top": 24, "right": 24, "bottom": 24}, "guide": "button-companion-pressed-template.svg"},
    {"slotId": "button.settings.normal", "title": "SETTINGS NORMAL", "x": 3200, "y": 3560, "width": 264, "height": 112, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 24, "top": 24, "right": 24, "bottom": 24}, "guide": "button-settings-normal-template.svg"},
    {"slotId": "button.settings.pressed", "title": "SETTINGS PRESSED", "x": 3544, "y": 3560, "width": 264, "height": 112, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": 24, "top": 24, "right": 24, "bottom": 24}, "guide": "button-settings-pressed-template.svg"},
    {"slotId": "button.notes.normal", "title": "NOTES NORMAL", "x": 80, "y": 3848, "width": COMPANION_ACTION_WIDTH, "height": COMPANION_ACTION_HEIGHT, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": COMPANION_ACTION_NINE_SLICE, "top": COMPANION_ACTION_NINE_SLICE, "right": COMPANION_ACTION_NINE_SLICE, "bottom": COMPANION_ACTION_NINE_SLICE}, "guide": "button-notes-normal-template.svg"},
    {"slotId": "button.notes.pressed", "title": "NOTES PRESSED", "x": 732, "y": 3848, "width": COMPANION_ACTION_WIDTH, "height": COMPANION_ACTION_HEIGHT, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": COMPANION_ACTION_NINE_SLICE, "top": COMPANION_ACTION_NINE_SLICE, "right": COMPANION_ACTION_NINE_SLICE, "bottom": COMPANION_ACTION_NINE_SLICE}, "guide": "button-notes-pressed-template.svg"},
    {"slotId": "button.walkthrough.normal", "title": "WALKTHROUGH NORMAL", "x": 1384, "y": 3848, "width": COMPANION_ACTION_WIDTH, "height": COMPANION_ACTION_HEIGHT, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": COMPANION_ACTION_NINE_SLICE, "top": COMPANION_ACTION_NINE_SLICE, "right": COMPANION_ACTION_NINE_SLICE, "bottom": COMPANION_ACTION_NINE_SLICE}, "guide": "button-walkthrough-normal-template.svg"},
    {"slotId": "button.walkthrough.pressed", "title": "WALKTHROUGH PRESSED", "x": 2036, "y": 3848, "width": COMPANION_ACTION_WIDTH, "height": COMPANION_ACTION_HEIGHT, "scaleMode": "nineSlice", "alphaMode": "rgba", "nineSliceInsets": {"left": COMPANION_ACTION_NINE_SLICE, "top": COMPANION_ACTION_NINE_SLICE, "right": COMPANION_ACTION_NINE_SLICE, "bottom": COMPANION_ACTION_NINE_SLICE}, "guide": "button-walkthrough-pressed-template.svg"},
]


def qname(tag: str) -> str:
    return f"{{{SVG_NS}}}{tag}"


def inkscape(name: str) -> str:
    return f"{{{INKSCAPE_NS}}}{name}"


def safe_id(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_-]", "-", value)


def layer(root: ET.Element, ident: str) -> ET.Element:
    return ET.SubElement(root, qname("g"), {"id": ident, inkscape("groupmode"): "layer", inkscape("label"): ident})


def text(parent: ET.Element, x: int, y: int, value: str, size: int = 18, color: str = "#18212a", weight: str = "400", anchor: str = "start") -> ET.Element:
    element = ET.SubElement(parent, qname("text"), {
        "x": str(x), "y": str(y), "font-family": "Arial, sans-serif", "font-size": str(size),
        "font-weight": weight, "text-anchor": anchor, "fill": color,
    })
    element.text = value
    return element


def rewrite_references(element: ET.Element, prefix: str) -> None:
    id_map = {}
    for child in element.iter():
        old = child.get("id")
        if old:
            new = f"{prefix}-{old}"
            id_map[old] = new
            child.set("id", new)
    for child in element.iter():
        for name, value in list(child.attrib.items()):
            for old, new in id_map.items():
                value = value.replace(f"url(#{old})", f"url(#{new})").replace(f"#{old}", f"#{new}")
            child.set(name, value)


def import_guide(region: dict, defs: ET.Element, backgrounds_layer: ET.Element, guides_layer: ET.Element, labels_layer: ET.Element, clip_id: str | None = None) -> None:
    source = ET.parse(GUIDES / region["guide"]).getroot()
    prefix = safe_id(region["slotId"])
    rewrite_references(source, prefix)
    guide_group = ET.SubElement(guides_layer, qname("g"), {
        "id": f"guide-{prefix}", "transform": f'translate({region["x"]} {region["y"]})',
        "data-slot-id": region["slotId"],
    })
    label_group = ET.SubElement(labels_layer, qname("g"), {
        "id": f"labels-{prefix}", "transform": f'translate({region["x"]} {region["y"]})',
        "data-slot-id": region["slotId"],
    })
    background_group = ET.SubElement(backgrounds_layer, qname("g"), {
        "id": f"guide-background-{prefix}", "transform": f'translate({region["x"]} {region["y"]})',
        "data-slot-id": region["slotId"],
    })
    if clip_id:
        for group in (guide_group, label_group, background_group):
            group.set("clip-path", f"url(#{clip_id})")
    for child in list(source):
        if child.tag == qname("defs"):
            for definition in list(child):
                defs.append(copy.deepcopy(definition))
        elif child.tag == qname("text"):
            label_group.append(copy.deepcopy(child))
        elif child.tag == qname("rect") and child.get("fill") == "#101820" and child.get("width") == str(region["width"]) and child.get("height") == str(region["height"]):
            background_group.append(copy.deepcopy(child))
        else:
            guide_group.append(copy.deepcopy(child))


def custom_trackpad(region: dict, backgrounds_layer: ET.Element, guides_layer: ET.Element, labels_layer: ET.Element, clip_id: str | None = None) -> None:
    x, y, w, h = (region[k] for k in ("x", "y", "width", "height"))
    ET.SubElement(backgrounds_layer, qname("rect"), {"id": "guide-background-trackpad-surface", "x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "#101820"})
    g = ET.SubElement(guides_layer, qname("g"), {"id": "guide-trackpad-surface", "data-slot-id": region["slotId"]})
    if clip_id:
        g.set("clip-path", f"url(#{clip_id})")
    ET.SubElement(g, qname("rect"), {"x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "none", "stroke": "#f4f7fa", "stroke-width": "6"})
    ET.SubElement(g, qname("rect"), {"x": str(x + 96), "y": str(y + 96), "width": str(w - 192), "height": str(h - 192), "fill": "#4ddd8b", "fill-opacity": ".08", "stroke": "#ffc857", "stroke-width": "4", "stroke-dasharray": "18 12"})
    for xx in (x + 96, x + w - 96):
        ET.SubElement(g, qname("line"), {"x1": str(xx), "y1": str(y), "x2": str(xx), "y2": str(y + h), "stroke": "#ffc857", "stroke-width": "3", "stroke-dasharray": "10 8"})
    for yy in (y + 96, y + h - 96):
        ET.SubElement(g, qname("line"), {"x1": str(x), "y1": str(yy), "x2": str(x + w), "y2": str(yy), "stroke": "#ffc857", "stroke-width": "3", "stroke-dasharray": "10 8"})
    label = ET.SubElement(labels_layer, qname("g"), {"id": "labels-trackpad-surface", "data-slot-id": region["slotId"]})
    if clip_id:
        label.set("clip-path", f"url(#{clip_id})")
    text(label, x + 32, y + 55, "TRACKPAD SURFACE / trackpad.surface", 28, "#f4f7fa", "700")
    text(label, x + w // 2, y + h // 2, "GESTURE CONTENT IS NATIVE • ARTWORK IS NON-INTERACTIVE", 22, "#ffc857", "700", "middle")
    text(label, x + w // 2, y + h - 35, "96 px suggested nine-slice borders • keep essential detail out of stretchable centre", 18, "#4ddd8b", "700", "middle")


def custom_preview(region: dict, backgrounds_layer: ET.Element, guides_layer: ET.Element, labels_layer: ET.Element, clip_id: str | None = None) -> None:
    x, y, w, h = (region[k] for k in ("x", "y", "width", "height"))
    ET.SubElement(backgrounds_layer, qname("rect"), {"id": "guide-background-preview", "x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "#101820"})
    g = ET.SubElement(guides_layer, qname("g"), {"id": "guide-preview", "data-slot-id": region["slotId"]})
    if clip_id:
        g.set("clip-path", f"url(#{clip_id})")
    ET.SubElement(g, qname("rect"), {"x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "none", "stroke": "#f4f7fa", "stroke-width": "6"})
    ET.SubElement(g, qname("rect"), {"x": str(x + 40), "y": str(y + 40), "width": str(w - 80), "height": str(h - 80), "fill": "none", "stroke": "#4ddd8b", "stroke-width": "3", "stroke-dasharray": "16 10"})
    label = ET.SubElement(labels_layer, qname("g"), {"id": "labels-preview", "data-slot-id": region["slotId"]})
    if clip_id:
        label.set("clip-path", f"url(#{clip_id})")
    text(label, x + 32, y + 55, "PREVIEW / preview", 28, "#f4f7fa", "700")
    text(label, x + w // 2, y + h // 2, "CATALOG / CONFIRMATION IMAGE ONLY", 28, "#24d4e8", "700", "middle")
    text(label, x + w // 2, y + h // 2 + 38, "1200×675 • opaque • contain • never rendered as UI", 18, "#a8b6c2", "400", "middle")


def build_reference_svg(regions: list[dict], version: str, output_name: str, clip_to_regions: bool = False) -> None:
    legacy = version == "v1"
    root = ET.Element(qname("svg"), {
        "width": str(MASTER_WIDTH), "height": str(MASTER_HEIGHT), "viewBox": f"0 0 {MASTER_WIDTH} {MASTER_HEIGHT}",
        "version": "1.1", "id": "AdventurePad-Skin-Template-v1" if legacy else f"AdventurePad-Skin-Template-{version}-reference",
    })
    title_node = ET.SubElement(root, qname("title"))
    title_node.text = "AdventurePad Skin Authoring Template v1" if legacy else f"AdventurePad Skin Authoring Reference {version}"
    desc = ET.SubElement(root, qname("desc"))
    desc.text = "Canonical editable source with separate ARTWORK, GUIDES, LABELS, and REGISTRATION layers."
    defs = ET.SubElement(root, qname("defs"))

    guide_backgrounds = layer(root, "GUIDE_BACKGROUNDS")
    artwork = layer(root, "ARTWORK")
    guides = layer(root, "GUIDES")
    labels = layer(root, "LABELS")
    registration = layer(root, "REGISTRATION")
    registration.set("style", "display:none")

    ET.SubElement(guide_backgrounds, qname("rect"), {"id": "reference-sheet-background", "width": str(MASTER_WIDTH), "height": str(MASTER_HEIGHT), "fill": "#e9edf1"})
    heading = "AdventurePad Skin Authoring Template v1" if legacy else f"AdventurePad Skin Authoring Reference {version}"
    subtitle = (
        "Edit only ARTWORK groups. Ignore all GUIDE layers and LABELS during extraction. REGISTRATION is machine metadata."
        if legacy else
        "Documentation only — create and submit artwork with AdventurePad-Skin-Template-v2.png."
    )
    text(labels, 80, 72, heading, 40, "#101820", "700")
    text(labels, 80, 112, subtitle, 20, "#425466", "400")

    for region in regions:
        prefix = safe_id(region["slotId"])
        global_clip_id = f"crop-clip-global-{prefix}" if clip_to_regions else None
        local_clip_id = f"crop-clip-local-{prefix}" if clip_to_regions else None
        if global_clip_id and local_clip_id:
            clip = ET.SubElement(defs, qname("clipPath"), {"id": global_clip_id, "clipPathUnits": "userSpaceOnUse"})
            ET.SubElement(clip, qname("rect"), {
                "x": str(region["x"]), "y": str(region["y"]),
                "width": str(region["width"]), "height": str(region["height"]),
            })
            local_clip = ET.SubElement(defs, qname("clipPath"), {"id": local_clip_id, "clipPathUnits": "userSpaceOnUse"})
            ET.SubElement(local_clip, qname("rect"), {
                "x": "0", "y": "0", "width": str(region["width"]), "height": str(region["height"]),
            })
        art = ET.SubElement(artwork, qname("g"), {
            "id": region["slotId"], "data-slot-id": region["slotId"],
            "data-output-width": str(region["width"]), "data-output-height": str(region["height"]),
            inkscape("label"): region["slotId"],
        })
        ET.SubElement(art, qname("rect"), {
            "id": f"artboard-{prefix}", "x": str(region["x"]), "y": str(region["y"]),
            "width": str(region["width"]), "height": str(region["height"]),
            "fill": "#ffffff", "fill-opacity": "0",
        })

        if region.get("guide"):
            import_guide(region, defs, guide_backgrounds, guides, labels, local_clip_id)
        elif region["customGuide"] == "trackpad":
            custom_trackpad(region, guide_backgrounds, guides, labels, global_clip_id)
        else:
            custom_preview(region, guide_backgrounds, guides, labels, global_clip_id)

        if region["width"] < 400:
            heading = f'{region["title"]}  •  {region["width"]}×{region["height"]}'
            heading_size = 11
        else:
            heading = f'{region["title"]}  •  {region["slotId"]}  •  {region["width"]}×{region["height"]}'
            heading_size = 17
        text(labels, region["x"], region["y"] - 18, heading, heading_size, "#18212a", "700")
        attributes = {
            "id": f"registration-{prefix}", "x": str(region["x"]), "y": str(region["y"]),
            "width": str(region["width"]), "height": str(region["height"]), "fill": "none",
            "data-slot-id": region["slotId"], "data-scale-mode": region["scaleMode"], "data-alpha-mode": region["alphaMode"],
        }
        if "nineSliceInsets" in region:
            attributes["data-nine-slice"] = ",".join(str(region["nineSliceInsets"][side]) for side in ("left", "top", "right", "bottom"))
        ET.SubElement(registration, qname("rect"), attributes)

    ET.ElementTree(root).write(OUT / output_name, encoding="utf-8", xml_declaration=True)


def build_svg() -> None:
    build_reference_svg(REGIONS, "v1", "AdventurePad-Skin-Template-v1.svg")


def build_creator_canvas_svg() -> None:
    """Build a label-free canvas whose visible marks stay outside every slice."""
    root = ET.Element(qname("svg"), {
        "width": str(MASTER_WIDTH), "height": str(MASTER_HEIGHT),
        "viewBox": f"0 0 {MASTER_WIDTH} {MASTER_HEIGHT}",
        "version": "1.1", "id": "AdventurePad-Creator-Canvas-v1",
    })
    boundaries = ET.SubElement(root, qname("g"), {
        "id": "BOUNDARIES", "fill": "none", "stroke": "#7c8792",
        "stroke-opacity": ".32", "stroke-width": "2",
    })
    registration = ET.SubElement(root, qname("g"), {
        "id": "REGISTRATION", "style": "display:none",
    })

    for index, region in enumerate(REGIONS, 1):
        # The stroke's inner edge is one full pixel outside the registered
        # rectangle, so rasterized guide pixels can never enter runtime art.
        ET.SubElement(boundaries, qname("rect"), {
            "id": f"boundary-{index:02d}",
            "x": str(region["x"] - 2), "y": str(region["y"] - 2),
            "width": str(region["width"] + 4), "height": str(region["height"] + 4),
        })
        ET.SubElement(registration, qname("rect"), {
            "id": f"registration-{index:02d}",
            "x": str(region["x"]), "y": str(region["y"]),
            "width": str(region["width"]), "height": str(region["height"]),
            "fill": "none",
        })

    ET.ElementTree(root).write(
        OUT / "AdventurePad-Creator-Canvas-v1.svg",
        encoding="utf-8",
        xml_declaration=True,
    )


def load_authoring_spec() -> dict:
    """Load the runtime-bundled crop contract used by the PNG importer."""
    path = OUT / "AUTHORING_TEMPLATE_SPEC.json"
    try:
        spec = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Could not load {path.relative_to(ROOT)}: {exc}") from exc
    canvas = spec.get("masterCanvas", {})
    if canvas.get("width") != MASTER_WIDTH or canvas.get("height") != MASTER_HEIGHT:
        raise SystemExit("AUTHORING_TEMPLATE_SPEC.json has an unexpected master canvas")
    if not spec.get("regions"):
        raise SystemExit("AUTHORING_TEMPLATE_SPEC.json contains no authoring regions")
    return spec


def reference_regions_from_spec() -> list[dict]:
    """Combine guide metadata with geometry owned exclusively by the runtime spec."""
    guide_metadata = {region["slotId"]: region for region in REGIONS}
    output = []
    for region in sorted(load_authoring_spec()["regions"], key=lambda item: item["index"]):
        slot = region["slotId"]
        if slot not in guide_metadata:
            raise SystemExit(f"No reference guide is registered for {slot}")
        source = guide_metadata[slot]
        origin, size = region["origin"], region["size"]
        merged = {
            "slotId": slot,
            "title": source["title"],
            "x": origin["x"], "y": origin["y"],
            "width": size["width"], "height": size["height"],
            "scaleMode": region["scaleMode"], "alphaMode": region["alphaMode"],
        }
        if source.get("guide"):
            guide_path = GUIDES / source["guide"]
            guide = ET.parse(guide_path).getroot()
            if (int(guide.get("width")), int(guide.get("height"))) != (size["width"], size["height"]):
                raise SystemExit(f"{guide_path.relative_to(ROOT)} does not match the {slot} crop")
            merged["guide"] = source["guide"]
        else:
            merged["customGuide"] = source["customGuide"]
        if "nineSliceInsets" in region:
            merged["nineSliceInsets"] = region["nineSliceInsets"]
        output.append(merged)
    return output


def build_v2_reference_svg() -> None:
    build_reference_svg(reference_regions_from_spec(), "v2", V2_REFERENCE_SVG, clip_to_regions=True)


def build_v2_svg() -> None:
    """Build the official import-safe v2 template from the runtime crop spec."""
    spec = load_authoring_spec()
    region_titles = {region["slotId"]: region["title"] for region in REGIONS}
    canvas = spec["masterCanvas"]
    root = ET.Element(qname("svg"), {
        "width": str(canvas["width"]), "height": str(canvas["height"]),
        "viewBox": f'0 0 {canvas["width"]} {canvas["height"]}',
        "version": "1.1", "id": "AdventurePad-Skin-Template-v2",
    })
    title_node = ET.SubElement(root, qname("title"))
    title_node.text = "AdventurePad Skin Template v2"
    desc = ET.SubElement(root, qname("desc"))
    desc.text = (
        "Official single-PNG creator template. Crop geometry is generated from "
        "AUTHORING_TEMPLATE_SPEC.json; visible guides remain outside runtime slices."
    )

    artwork = layer(root, "ARTWORK")
    boundaries = layer(root, "BOUNDARIES")
    boundaries.set("fill", "none")
    boundaries.set("stroke", "#7c8792")
    boundaries.set("stroke-opacity", ".55")
    boundaries.set("stroke-width", "2")
    labels = layer(root, "LABELS")
    registration = layer(root, "REGISTRATION")
    registration.set("style", "display:none")

    text(labels, 80, 72, "AdventurePad Skin Template v2", 40, "#101820", "700")
    text(
        labels, 80, 112,
        "Paint inside every labelled region, replace the opaque catalog preview, then export this entire canvas as one PNG.",
        20, "#425466", "400",
    )

    for region in sorted(spec["regions"], key=lambda item: item["index"]):
        slot = region["slotId"]
        prefix = safe_id(slot)
        origin, size = region["origin"], region["size"]
        x, y = origin["x"], origin["y"]
        width, height = size["width"], size["height"]

        art = ET.SubElement(artwork, qname("g"), {
            "id": slot, "data-slot-id": slot, inkscape("label"): slot,
        })
        if region.get("alphaMode") == "opaque":
            ET.SubElement(art, qname("rect"), {
                "id": f"opaque-placeholder-{prefix}", "x": str(x), "y": str(y),
                "width": str(width), "height": str(height), "fill": "#101820",
            })
            text(
                art, x + width // 2, y + height // 2 - 10,
                "CATALOG PREVIEW", 34, "#f4f7fa", "700", "middle",
            )
            text(
                art, x + width // 2, y + height // 2 + 34,
                "Replace with fully opaque artwork", 20, "#a8b6c2", "400", "middle",
            )
        else:
            ET.SubElement(art, qname("rect"), {
                "id": f"transparent-artboard-{prefix}", "x": str(x), "y": str(y),
                "width": str(width), "height": str(height),
                "fill": "#ffffff", "fill-opacity": "0",
            })

        # With a 2 px stroke centred two pixels outside the crop, the stroke's
        # inner edge remains one full pixel outside all importer-visible art.
        ET.SubElement(boundaries, qname("rect"), {
            "id": f"boundary-{prefix}", "x": str(x - 2), "y": str(y - 2),
            "width": str(width + 4), "height": str(height + 4),
        })
        heading_size = 11 if width < 400 else 17
        heading = f'{region["index"]:02d}  {region_titles.get(slot, slot)}  •  {slot}  •  {width}×{height}'
        text(labels, x, y - 18, heading, heading_size, "#18212a", "700")

        attributes = {
            "id": f"registration-{prefix}", "x": str(x), "y": str(y),
            "width": str(width), "height": str(height), "fill": "none",
            "data-slot-id": slot, "data-scale-mode": region["scaleMode"],
            "data-alpha-mode": region["alphaMode"],
        }
        if "nineSliceInsets" in region:
            inset = region["nineSliceInsets"]
            attributes["data-nine-slice"] = ",".join(
                str(inset[side]) for side in ("left", "top", "right", "bottom")
            )
        ET.SubElement(registration, qname("rect"), attributes)

    ET.ElementTree(root).write(OUT / V2_SVG, encoding="utf-8", xml_declaration=True)


def build_spec() -> None:
    output_regions = []
    for index, region in enumerate(REGIONS, 1):
        entry = {
            "index": index,
            "slotId": region["slotId"],
            "svgArtworkGroupId": region["slotId"],
            "origin": {"x": region["x"], "y": region["y"]},
            "size": {"width": region["width"], "height": region["height"]},
            "output": {"width": region["width"], "height": region["height"], "format": "png"},
            "alphaMode": region["alphaMode"],
            "scaleMode": region["scaleMode"],
            "requiredByRuntime": region.get("required", False),
        }
        if "nineSliceInsets" in region:
            entry["nineSliceInsets"] = region["nineSliceInsets"]
        if region["slotId"] == "top.surround":
            entry["runtimeProtection"] = "Full artwork is exported; runtime clips the actual live-game viewport. Never author a transparent game-window hole."
        elif region["slotId"] == "bottom.split.background":
            entry["runtimeProtection"] = "Full background is exported; the runtime-sized mirrored region is composited above it."
        elif region["slotId"] == "panel.frame":
            entry["transparentCenter"] = {"x": 64, "y": 64, "width": 1112, "height": 232, "requiredAlpha": 0}
        output_regions.append(entry)

    spec = {
        "schemaVersion": 1,
        "templateVersion": "2.0.0",
        "templateFile": V2_PNG,
        "masterCanvas": {"width": MASTER_WIDTH, "height": MASTER_HEIGHT, "units": "px"},
        "coordinates": "Master-canvas pixels; origins are top-left; right and bottom are exclusive.",
        "layerContract": {
            "GUIDE_BACKGROUNDS": "Non-exporting artboard/reference backdrops placed below artwork for editing visibility.",
            "ARTWORK": "Editable artist content. One child group per slot ID; Immersive artwork supplies visual labels where the guides require them.",
            "GUIDES": "Non-exporting geometry, safe/protected/dynamic regions, inset and nine-slice guides.",
            "LABELS": "Non-exporting dimensions and explanatory/native-label annotations.",
            "REGISTRATION": "Hidden machine-readable rectangles mirroring JSON registration data."
        },
        "sourceAuthority": [
            "../skin-creator-kit/LAYOUT_SPEC.json",
            "../skin-creator-kit/LAYOUT_SPEC.md",
            "../skin-creator-kit/templates/",
            "../docs/skins/SKIN_FORMAT_V1.md"
        ],
        "regions": output_regions,
        "excludedRuntimeSlots": {
            "launcher.background": "Optional v1 launcher slot; outside the Milestone 7.2 enumerated master-template scope.",
            "launcher.header": "Optional v1 launcher slot; outside the Milestone 7.2 enumerated master-template scope.",
            "launcher.brand": "Optional v1 launcher slot; outside the Milestone 7.2 enumerated master-template scope."
        },
        "legacySlotsExcluded": ["bottom.background", "trackpad.button.left", "trackpad.button.right"]
    }
    (OUT / "AUTHORING_TEMPLATE_SPEC.json").write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")


def build_markdown() -> None:
    rows = []
    for region in REGIONS:
        slice_value = "—"
        if "nineSliceInsets" in region:
            i = region["nineSliceInsets"]
            slice_value = f'{i["left"]}/{i["top"]}/{i["right"]}/{i["bottom"]}'
        rows.append(f'| `{region["slotId"]}` | {region["x"]},{region["y"]} | {region["width"]}×{region["height"]} | `{region["scaleMode"]}` | `{region["alphaMode"]}` | {slice_value} |')
    document = f"""# AdventurePad canonical skin authoring template v2

This directory documents the artist-facing registration for AdventurePad's single-PNG Skin Builder. It contains two distinct authoring assets:

- `AdventurePad-Skin-Template-v2-reference.svg` and `AdventurePad-Skin-Template-v2-reference.png` are the labelled reference/learning template. They explain the regions and authoring constraints; never submit the guide/reference file as a skin.
- `AdventurePad-Skin-Template-v2.svg` and `AdventurePad-Skin-Template-v2.png` are the canonical production canvas. Visible boundaries and labels sit entirely in non-runtime gutters, leaving every registered region ready to paint.

Start finished artwork from the Creator Canvas, paint every required region, and export one **4720×4040 sRGB PNG**. The creator submits only that final PNG; AdventurePad validates its dimensions, slices every registered region, generates the internal assets and metadata, and installs the selectable skin.

Creators do not build `.apskin` archives, manifests, folders, or individual assets. Those are internal AdventurePad implementation details. Guide graphics and labels are reference-only and must be hidden or painted over in the submitted master PNG.

## Layer contract

- `GUIDE_BACKGROUNDS`: non-exporting artboard/reference backdrops below the artwork, so edits remain visible while guides are enabled.
- `ARTWORK`: the only artist-editable/exportable layer. Each child group ID is the exact runtime slot ID. Put artwork inside that group and keep native labels out.
- `GUIDES`: non-exporting visual geometry: live/protected content, artwork-safe and dynamic areas, safe-drawing guidance, native controls, and nine-slice boundaries.
- `LABELS`: non-exporting dimensions, region names, native-label zones, and explanatory notes.
- `REGISTRATION`: hidden exact-bound rectangles with `data-*` attributes. `AUTHORING_TEMPLATE_SPEC.json` is the complete slicing-registration contract.

Hide or ignore `GUIDE_BACKGROUNDS`, `GUIDES`, `LABELS`, and `REGISTRATION` when extracting art. Automated tooling must read the `ARTWORK` group identified by `svgArtworkGroupId`, clipped to the registered region rectangle. Guide graphics and text must never enter runtime assets.

## Canvas and region layout

The master canvas is **{MASTER_WIDTH}×{MASTER_HEIGHT} px**. Coordinates use a top-left origin and exclusive right/bottom edges. Regions have intentional gutters and do not overlap.

| Slot ID | Origin x,y | Output | Scale | Alpha behavior | Nine-slice L/T/R/B |
|---|---:|---:|---|---|---:|
{chr(10).join(rows)}

## Authoring rules

### Upper display

`top.surround` is only the 1920×1080 upper display, never the physical device, bezel, controls, or hinge. Paint the complete artboard. Do not cut a transparent game-window hole. Runtime clips the skin out of the actual ScummVM viewport, so game pixels always win. The 4:3 and 16:10 rectangles are examples in `GUIDES`; a full-screen 16:9 game can hide the skin completely.

### Lower displays and pages

Every 1240×1080 lower artboard represents screen pixels only. The zero-inset, 2 px/dp rectangles are reference geometry, not a density or inset guarantee. Native content, labels, touch targets, scrolling, keyboard resizing, and `WindowInsets.safeDrawing` remain runtime-owned.

Split View has no fixed transparent cutout. Export all of `bottom.split.background`; runtime geometry sizes and layers the mirror above it. In Standard style Companion, Notes, and Walkthrough retain centered 94% opaque native pages. With exact artwork in Immersive style they become full lower-display game surfaces. Companion uses a fixed 72 dp header, a 48 dp top offset, and a fixed 96 dp action row: Notes on the left and Walkthrough on the right, with 16 dp outer padding and a 16 dp gap. Both edges of the original-height buttons move down by 48 dp. In the 2 px/dp reference this produces exact 572×192 px hit regions at `[32,240,604,432]` and `[636,240,1208,432]`. Paint the Companion title and surrounding decoration into `companion.background`; paint the two action appearances and labels into their dedicated normal/pressed assets. AdventurePad owns the fixed hit targets and never asks artists for coordinates. Keep Notes and Walkthrough content areas pale, quiet, and readable beneath native dark text, and preserve their marked header areas.

### Buttons and panel frame

Normal and pressed button artwork are separate groups. Standard style draws native labels. Immersive style hides native labels and chrome, so immersive-oriented artwork should visually identify its fixed hit regions. Notes and Walkthrough use their exact 572×192 px reference hit-area geometry. Suggested nine-slice borders are registration metadata and visible guides; avoid essential detail in stretchable centers.

`panel.frame` requires an RGBA image whose inner rectangle `(64,64)` through `(1176,296)` is fully transparent. Runtime draws only the eight border patches and intentionally omits the center patch.

### Preview

`preview` is the only required package image. It is a 1200×675 opaque catalog/confirmation image and is never rendered as application UI.

## Scope relative to the runtime skin contract

The authoritative sources agree on the dimensions and behavior of all 21 included regions. `trackpad.surface` remains a separate runtime asset and is therefore included.

The runtime format also recognizes optional `launcher.background`, `launcher.header`, and `launcher.brand` slots. They are intentionally absent from this focused gameplay/lower-screen template. Legacy compatibility slots (`bottom.background`, `trackpad.button.left`, `trackpad.button.right`) are likewise not authoritative creator slots.

## Skin Builder consumption

`AUTHORING_TEMPLATE_SPEC.json` supplies slot ID, exact master origin, source/output size, alpha mode, scaling, and nine-slice metadata. The current builder reads this registration, validates the submitted master PNG, clips each registered rectangle, enforces `preview` opacity and the `panel.frame` transparent center, then produces and installs the internal package. The checked-in Adventure Journal proof follows this exact path.
"""
    (OUT / "AUTHORING_TEMPLATE_SPEC.md").write_text(document, encoding="utf-8")


def render_svgs() -> None:
    converter = shutil.which("rsvg-convert")
    if not converter:
        raise SystemExit("rsvg-convert is required to render the reference PNG")
    subprocess.run([
        converter,
        str(OUT / "AdventurePad-Creator-Canvas-v1.svg"),
        "-o", str(OUT / "AdventurePad-Creator-Canvas-v1.png"),
    ], check=True)
    subprocess.run([
        converter,
        str(OUT / V2_SVG),
        "-o", str(OUT / V2_PNG),
    ], check=True)
    subprocess.run([
        converter,
        str(OUT / V2_REFERENCE_SVG),
        "-o", str(OUT / V2_REFERENCE_PNG),
    ], check=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    build_creator_canvas_svg()
    build_spec()
    build_markdown()
    build_v2_svg()
    build_v2_reference_svg()
    render_svgs()
    for path in sorted(OUT.iterdir()):
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
