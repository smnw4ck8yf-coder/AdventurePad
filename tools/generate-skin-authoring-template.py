#!/usr/bin/env python3
"""Generate the canonical one-PNG authoring document and reference sheet.

This creates creator-facing source material only. It does not slice artwork,
build .apskin archives, or modify runtime behavior.
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


def import_guide(region: dict, defs: ET.Element, backgrounds_layer: ET.Element, guides_layer: ET.Element, labels_layer: ET.Element) -> None:
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


def custom_trackpad(region: dict, backgrounds_layer: ET.Element, guides_layer: ET.Element, labels_layer: ET.Element) -> None:
    x, y, w, h = (region[k] for k in ("x", "y", "width", "height"))
    ET.SubElement(backgrounds_layer, qname("rect"), {"id": "guide-background-trackpad-surface", "x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "#101820"})
    g = ET.SubElement(guides_layer, qname("g"), {"id": "guide-trackpad-surface", "data-slot-id": region["slotId"]})
    ET.SubElement(g, qname("rect"), {"x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "none", "stroke": "#f4f7fa", "stroke-width": "6"})
    ET.SubElement(g, qname("rect"), {"x": str(x + 96), "y": str(y + 96), "width": str(w - 192), "height": str(h - 192), "fill": "#4ddd8b", "fill-opacity": ".08", "stroke": "#ffc857", "stroke-width": "4", "stroke-dasharray": "18 12"})
    for xx in (x + 96, x + w - 96):
        ET.SubElement(g, qname("line"), {"x1": str(xx), "y1": str(y), "x2": str(xx), "y2": str(y + h), "stroke": "#ffc857", "stroke-width": "3", "stroke-dasharray": "10 8"})
    for yy in (y + 96, y + h - 96):
        ET.SubElement(g, qname("line"), {"x1": str(x), "y1": str(yy), "x2": str(x + w), "y2": str(yy), "stroke": "#ffc857", "stroke-width": "3", "stroke-dasharray": "10 8"})
    label = ET.SubElement(labels_layer, qname("g"), {"id": "labels-trackpad-surface", "data-slot-id": region["slotId"]})
    text(label, x + 32, y + 55, "TRACKPAD SURFACE / trackpad.surface", 28, "#f4f7fa", "700")
    text(label, x + w // 2, y + h // 2, "GESTURE CONTENT IS NATIVE • ARTWORK IS NON-INTERACTIVE", 22, "#ffc857", "700", "middle")
    text(label, x + w // 2, y + h - 35, "96 px suggested nine-slice borders • keep essential detail out of stretchable centre", 18, "#4ddd8b", "700", "middle")


def custom_preview(region: dict, backgrounds_layer: ET.Element, guides_layer: ET.Element, labels_layer: ET.Element) -> None:
    x, y, w, h = (region[k] for k in ("x", "y", "width", "height"))
    ET.SubElement(backgrounds_layer, qname("rect"), {"id": "guide-background-preview", "x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "#101820"})
    g = ET.SubElement(guides_layer, qname("g"), {"id": "guide-preview", "data-slot-id": region["slotId"]})
    ET.SubElement(g, qname("rect"), {"x": str(x), "y": str(y), "width": str(w), "height": str(h), "fill": "none", "stroke": "#f4f7fa", "stroke-width": "6"})
    ET.SubElement(g, qname("rect"), {"x": str(x + 40), "y": str(y + 40), "width": str(w - 80), "height": str(h - 80), "fill": "none", "stroke": "#4ddd8b", "stroke-width": "3", "stroke-dasharray": "16 10"})
    label = ET.SubElement(labels_layer, qname("g"), {"id": "labels-preview", "data-slot-id": region["slotId"]})
    text(label, x + 32, y + 55, "PREVIEW / preview", 28, "#f4f7fa", "700")
    text(label, x + w // 2, y + h // 2, "CATALOG / CONFIRMATION IMAGE ONLY", 28, "#24d4e8", "700", "middle")
    text(label, x + w // 2, y + h // 2 + 38, "1200×675 • opaque • contain • never rendered as UI", 18, "#a8b6c2", "400", "middle")


def build_svg() -> None:
    root = ET.Element(qname("svg"), {
        "width": str(MASTER_WIDTH), "height": str(MASTER_HEIGHT), "viewBox": f"0 0 {MASTER_WIDTH} {MASTER_HEIGHT}",
        "version": "1.1", "id": "AdventurePad-Skin-Template-v1",
    })
    title_node = ET.SubElement(root, qname("title"))
    title_node.text = "AdventurePad Skin Authoring Template v1"
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
    text(labels, 80, 72, "AdventurePad Skin Authoring Template v1", 40, "#101820", "700")
    text(labels, 80, 112, "Edit only ARTWORK groups. Ignore all GUIDE layers and LABELS during extraction. REGISTRATION is machine metadata.", 20, "#425466", "400")

    for region in REGIONS:
        prefix = safe_id(region["slotId"])
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
            import_guide(region, defs, guide_backgrounds, guides, labels)
        elif region["customGuide"] == "trackpad":
            custom_trackpad(region, guide_backgrounds, guides, labels)
        else:
            custom_preview(region, guide_backgrounds, guides, labels)

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

    ET.ElementTree(root).write(OUT / "AdventurePad-Skin-Template-v1.svg", encoding="utf-8", xml_declaration=True)


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
        "templateVersion": "1.0.0",
        "templateFile": "AdventurePad-Skin-Template-v1.svg",
        "masterCanvas": {"width": MASTER_WIDTH, "height": MASTER_HEIGHT, "units": "px"},
        "coordinates": "Master-canvas pixels; origins are top-left; right and bottom are exclusive.",
        "layerContract": {
            "GUIDE_BACKGROUNDS": "Non-exporting artboard/reference backdrops placed below artwork for editing visibility.",
            "ARTWORK": "Editable, label-free artist content. One child group per slot ID.",
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
    document = f"""# AdventurePad canonical skin authoring template v1

This directory documents the artist-facing registration for AdventurePad's single-PNG Skin Builder. Use `AdventurePad-Skin-Template-v1.svg` and the flattened reference PNG as layout aids, then export one **4720×4040 sRGB PNG** containing only the finished artwork. The creator submits that one PNG; AdventurePad validates its dimensions, slices every registered region, generates the internal assets and metadata, and installs the selectable skin.

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

Split View has no fixed transparent cutout. Export all of `bottom.split.background`; runtime geometry sizes and layers the mirror above it. Companion, Notes, and Walkthrough retain centered 94% native layouts. In Standard style the native page is opaque. In Immersive style only Notes and Walkthrough remove that opaque pane when their exact artwork exists, so keep their marked text areas pale, quiet, and readable beneath native dark text. Page bounds, dynamic controls, scrolling areas, and optional frame placement remain guides only.

### Buttons and panel frame

Normal and pressed button artwork are separate groups. Keep artwork label-free: AdventurePad draws Left/Right, COMPANION, and SETTINGS natively. Suggested nine-slice borders are registration metadata and visible guides; avoid essential detail in stretchable centers.

`panel.frame` requires an RGBA image whose inner rectangle `(64,64)` through `(1176,296)` is fully transparent. Runtime draws only the eight border patches and intentionally omits the center patch.

### Preview

`preview` is the only required v1 package image. It is a 1200×675 opaque catalog/confirmation image and is never rendered as application UI.

## Scope relative to runtime v1

The authoritative sources agree on the dimensions and behavior of all 17 included regions. `trackpad.surface` remains a separate runtime asset and is therefore included.

The runtime format also recognizes optional `launcher.background`, `launcher.header`, and `launcher.brand` slots. They are intentionally absent from this focused gameplay/lower-screen template. Legacy compatibility slots (`bottom.background`, `trackpad.button.left`, `trackpad.button.right`) are likewise not authoritative creator slots.

## Skin Builder consumption

`AUTHORING_TEMPLATE_SPEC.json` supplies slot ID, exact master origin, source/output size, alpha mode, scaling, and nine-slice metadata. The current builder reads this registration, validates the submitted master PNG, clips each registered rectangle, enforces `preview` opacity and the `panel.frame` transparent center, then produces and installs the internal package. The checked-in Adventure Journal proof follows this exact path.
"""
    (OUT / "AUTHORING_TEMPLATE_SPEC.md").write_text(document, encoding="utf-8")


def render_reference() -> None:
    converter = shutil.which("rsvg-convert")
    if not converter:
        raise SystemExit("rsvg-convert is required to render the reference PNG")
    subprocess.run([
        converter,
        str(OUT / "AdventurePad-Skin-Template-v1.svg"),
        "-o", str(OUT / "AdventurePad-Skin-Template-v1-reference.png"),
    ], check=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    build_svg()
    build_spec()
    build_markdown()
    render_reference()
    for path in sorted(OUT.iterdir()):
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
