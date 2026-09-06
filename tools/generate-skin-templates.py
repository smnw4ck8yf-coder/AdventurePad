#!/usr/bin/env python3
"""Generate Milestone 7 creator-facing guides; never generates skin artwork."""

from pathlib import Path
import json
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "skin-creator-kit" / "templates"
SOURCE = OUT / "source-guides"

BG = "#101820"
GRID = "#263746"
WHITE = "#f4f7fa"
MUTED = "#a8b6c2"
CYAN = "#24d4e8"       # dynamic/responsive
RED = "#ff5b63"        # live/protected/transparent
GREEN = "#4ddd8b"      # artwork-safe
AMBER = "#ffc857"      # native controls/text
MAGENTA = "#dc78ff"    # runtime/system inset

LAYOUT_SPEC = json.loads((ROOT / "skin-creator-kit" / "LAYOUT_SPEC.json").read_text(encoding="utf-8"))
REFERENCE_SCENARIO = LAYOUT_SPEC["coordinateConventions"]["referenceScenario"]
COMPANION_LAYOUT = LAYOUT_SPEC["surfaces"]["companion"]
COMPANION_ACTIONS = COMPANION_LAYOUT["primaryActions"]
REFERENCE_DENSITY_PX_PER_DP = int(REFERENCE_SCENARIO["densityPxPerDp"])
COMPANION_REFERENCE_WIDTH_PX = REFERENCE_SCENARIO["canvas"][0]
COMPANION_HEADER_HEIGHT_DP = COMPANION_LAYOUT["header"]["heightDp"]
COMPANION_ACTION_TOP_OFFSET_DP = COMPANION_ACTIONS["topOffsetDp"]
COMPANION_ACTION_HEIGHT_DP = COMPANION_ACTIONS["rowHeightDp"]
COMPANION_HORIZONTAL_PADDING_DP = COMPANION_ACTIONS["horizontalPaddingDp"]
COMPANION_ACTION_GAP_DP = COMPANION_ACTIONS["gapDp"]
COMPANION_HEADER_CLOSE_WIDTH_DP = 48
COMPANION_UTILITY_TOUCH_TARGET_DP = 56


def esc(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


class Svg:
    def __init__(self, width: int, height: int, title: str, subtitle: str):
        self.w, self.h = width, height
        self.parts = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
            '<defs><pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse"><path d="M40 0H0V40" fill="none" stroke="#263746" stroke-width="1"/></pattern><pattern id="danger" width="20" height="20" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="10" height="20" fill="#ff5b63" fill-opacity=".16"/></pattern><pattern id="dynamic" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M0 24L24 0" stroke="#24d4e8" stroke-opacity=".18" stroke-width="6"/></pattern></defs>',
            f'<rect width="{width}" height="{height}" fill="{BG}"/><rect width="{width}" height="{height}" fill="url(#grid)"/>',
            f'<rect x="4" y="4" width="{width-8}" height="{height-8}" fill="none" stroke="{WHITE}" stroke-width="8"/>',
            f'<rect x="22" y="18" width="{min(width-44, 1150)}" height="84" rx="10" fill="#101820" fill-opacity=".94"/>',
            f'<text x="38" y="52" font-family="Arial,sans-serif" font-size="30" font-weight="700" fill="{WHITE}">{esc(title)}</text>',
            f'<text x="38" y="82" font-family="Arial,sans-serif" font-size="18" fill="{MUTED}">{esc(subtitle)}</text>',
        ]

    def rect(self, x, y, w, h, color, label="", fill_opacity=.12, dash="", stroke=4, rx=0, element_id=None):
        dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
        id_attr = f' id="{element_id}"' if element_id else ""
        self.parts.append(f'<rect{id_attr} x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{color}" fill-opacity="{fill_opacity}" stroke="{color}" stroke-width="{stroke}"{dash_attr}/>' )
        if label:
            self.text(x + 12, y + 28, label, color, 19, bold=True)

    def pattern_rect(self, x, y, w, h, pattern, color, label=""):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#{pattern})" stroke="{color}" stroke-width="4"/>')
        if label:
            self.text(x + 12, y + 28, label, color, 19, bold=True)

    def line(self, x1, y1, x2, y2, color, width=3, dash=""):
        dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
        self.parts.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{width}"{dash_attr}/>' )

    def text(self, x, y, value, color=WHITE, size=20, bold=False, anchor="start"):
        weight = ' font-weight="700"' if bold else ""
        self.parts.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="Arial,sans-serif" font-size="{size}"{weight} fill="{color}">{esc(value)}</text>')

    def note(self, x, y, lines, width=560):
        height = 24 + 27 * len(lines)
        self.parts.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="10" fill="#101820" fill-opacity=".94" stroke="{GRID}" stroke-width="2"/>')
        for i, line in enumerate(lines):
            self.text(x + 14, y + 28 + i * 27, line, WHITE if i == 0 else MUTED, 18, bold=i == 0)

    def legend(self, y=None):
        y = y or self.h - 44
        entries = [(GREEN, "artwork-safe"), (RED, "live/protected"), (CYAN, "dynamic"), (AMBER, "native text/control"), (MAGENTA, "runtime inset")]
        x = 28
        self.parts.append(f'<rect x="18" y="{y-29}" width="{self.w-36}" height="45" rx="8" fill="#101820" fill-opacity=".95"/>')
        for color, label in entries:
            self.parts.append(f'<rect x="{x}" y="{y-14}" width="20" height="20" fill="{color}"/>')
            self.text(x + 28, y + 3, label, WHITE, 16)
            x += 28 + len(label) * 9 + 32

    def finish(self):
        return "".join(self.parts) + "</svg>"


def top():
    s = Svg(1920, 1080, "TOP GAMEPLAY / top.surround", "Two full-height side panels • keep the large central gameplay area transparent")
    s.rect(0, 0, 240, 1080, GREEN, "", .16, stroke=5)
    s.rect(1680, 0, 240, 1080, GREEN, "", .16, stroke=5)
    s.rect(240, 0, 80, 1080, AMBER, "", .12, "16 10", 4)
    s.rect(1600, 0, 80, 1080, AMBER, "", .12, "16 10", 4)
    s.pattern_rect(320, 0, 1280, 1080, "danger", RED)
    s.text(120, 132, "LEFT PANEL", GREEN, 18, True, "middle")
    s.text(960, 132, "CENTRAL GAMEPLAY-SAFE AREA — KEEP TRANSPARENT", RED, 20, True, "middle")
    s.text(1800, 132, "RIGHT PANEL", GREEN, 18, True, "middle")
    s.note(610, 155, [
        "AUTHORING CONTRACT",
        "Decorate primarily in the green left/right panels; either may be narrower.",
        "Amber 80 px bands allow alpha edges, shadows, vines, and torn-paper overlap.",
        "Keep x=320…1600 transparent for the full height; no top/bottom centre rails.",
        "Low alpha (≤32) is ignored; up to 0.5% stronger centre pixels are tolerated.",
        "Normal mode composites the complete PNG above the game using authored alpha.",
        "Split View does not show this decorative surround as the normal top surround.",
    ], 700)
    s.text(280, 930, "80 px EDGE OVERLAP", AMBER, 18, True, "middle")
    s.text(1640, 930, "80 px EDGE OVERLAP", AMBER, 18, True, "middle")
    s.text(960, 970, "NOT A FOUR-SIDED FRAME", RED, 24, True, "middle")
    s.text(960, 1005, "Small corner transitions are fine • substantial top/bottom rails are not", WHITE, 18, True, "middle")
    s.legend(1050)
    return s


def bottom_base(title, subtitle):
    s = Svg(1240, 1080, title, subtitle)
    s.rect(0, 0, 1240, 1080, MAGENTA, "", .025, "18 12", 5)
    s.text(620, 105, "Reference scenario shown: safeDrawing = 0 px, density = 2 px/dp", MAGENTA, 17, True, "middle")
    return s


def bottom_trackpad():
    s = bottom_base("BOTTOM — NORMAL TRACKPAD", "Exact canvas 1240×1080 px; source constants shown in dp/fractions; reference scenario is explicitly conditional")
    s.pattern_rect(24, 6, 1192, 940, "dynamic", CYAN, "")
    s.rect(24, 770, 405, 176, RED, "LMB touch + artwork", .15, stroke=4)
    s.rect(811, 770, 405, 176, RED, "RMB touch + artwork", .15, stroke=4)
    s.rect(24, 6, 1192, 764, GREEN, "Trackpad gesture area (excluding current L/R overlays)", .055, stroke=3)
    s.rect(76, 826, 300, 88, AMBER, "native label safe zone", .04, "12 8", 2)
    s.rect(864, 826, 300, 88, AMBER, "native label safe zone", .04, "12 8", 2)
    s.rect(24, 952, 264, 112, AMBER, "COMPANION min 132×56 dp", .11, stroke=4, rx=14)
    s.rect(976, 952, 264, 112, AMBER, "SETTINGS min 132×56 dp", .11, stroke=4, rx=14)
    s.line(0, 952, 1240, 952, CYAN, 4, "18 10")
    s.note(350, 120, [
        "SOURCE-BACKED PLACEMENT",
        "Touch outer padding: horizontal 12 dp; vertical 3 dp.",
        "L/R width = 34% of TouchSurface each.",
        "L/R height = clamp(22% of surface, 56 dp, 88 dp).",
        "Utility row padding: horizontal 12 dp; vertical 4 dp.",
        "Companion and Settings each use separate NORMAL/PRESSED assets shown below.",
        "No connection-status text is rendered on this screen.",
        "Immersive hides native labels, borders, marker, and button chrome.",
        "Trackpad is weighted; height changes with insets/window size.",
    ], 610)
    s.legend(1050)
    return s


def bottom_split():
    s = bottom_base("BOTTOM — SPLIT VIEW", "Live mirrored crop occupies dynamic top height; remaining native trackpad and utility row stay responsive")
    s.pattern_rect(0, 110, 1240, 314, "danger", RED, "LIVE MIRRORED CONTENT — example height 314 px")
    s.line(0, 424, 1240, 424, CYAN, 7, "24 12")
    s.text(620, 454, "dynamic panel / trackpad boundary", CYAN, 20, True, "middle")
    s.pattern_rect(24, 430, 1192, 516, "dynamic", CYAN, "Remaining TouchSurface — dynamic height")
    s.rect(24, 808, 405, 138, RED, "LMB: 34% wide; clamped height", .15)
    s.rect(811, 808, 405, 138, RED, "RMB: 34% wide; clamped height", .15)
    s.rect(24, 952, 264, 112, AMBER, "COMPANION", .11, rx=14)
    s.rect(976, 952, 264, 112, AMBER, "SETTINGS", .11, rx=14)
    s.note(330, 500, [
        "DYNAMIC HEIGHT FORMULA",
        "interfaceAspect = sourceAspect / (1 − splitRatio), orientation-adjusted.",
        "desired = safeWidth / interfaceAspect × 1.35.",
        "actual = min(desired, safeHeight − 176 dp).",
        "splitRatio is 0.05…0.95; default 0.75; snapped to source pixels.",
        "Example: 4:3 source, 0.75 split, zero inset → 314 px panel.",
    ], 650)
    s.text(620, 145, "NO ARTWORK MAY COVER THIS REGION", RED, 26, True, "middle")
    s.legend(1050)
    return s


def page_frame(title, subtitle, walkthrough=False):
    s = bottom_base(title, subtitle)
    s.rect(37, 32, 1166, 1015, CYAN, "Page overlay = 94% of safeDrawing bounds, centered", .05, "18 12", 5, 18)
    header_h = 112 if walkthrough else 144
    color = AMBER
    s.rect(37, 32, 1166, header_h, color, "Native toolbar/header", .10, stroke=4)
    s.line(37, 32 + header_h, 1203, 32 + header_h, color, 3)
    return s, header_h


def companion():
    s = bottom_base(
        "COMPANION — HOME / companion.background",
        "Fixed runtime-owned geometry: full immersive surface, title at top, Notes left, Walkthrough right",
    )
    density = REFERENCE_DENSITY_PX_PER_DP
    header_height = COMPANION_HEADER_HEIGHT_DP * density
    action_top = header_height + COMPANION_ACTION_TOP_OFFSET_DP * density
    action_height = COMPANION_ACTION_HEIGHT_DP * density
    horizontal_padding = COMPANION_HORIZONTAL_PADDING_DP * density
    action_gap = COMPANION_ACTION_GAP_DP * density
    action_width = (COMPANION_REFERENCE_WIDTH_PX - 2 * horizontal_padding - action_gap) // 2
    walkthrough_x = horizontal_padding + action_width + action_gap
    close_width = COMPANION_HEADER_CLOSE_WIDTH_DP * density
    close_height = COMPANION_UTILITY_TOUCH_TARGET_DP * density
    close_x = COMPANION_REFERENCE_WIDTH_PX - horizontal_padding - close_width
    close_y = (header_height - close_height) // 2
    safe_inset = horizontal_padding
    s.rect(0, 0, 1240, header_height, AMBER, "", .10, stroke=4)
    s.rect(
        horizontal_padding, close_y, close_x - horizontal_padding, close_height,
        GREEN, "TITLE / DECORATIVE HEADER ART", .035, "16 10", 3,
        element_id="companion-header-art-safe-area",
    )
    s.rect(
        close_x, close_y, close_width, close_height,
        AMBER, "CLOSE 48×56 dp", .12, "10 7", 3, rx=10,
        element_id="companion-header-close-hit-area",
    )
    s.text(620, 132, "HEADER 72 dp • CLOSE HIT TARGET IS RUNTIME-OWNED", AMBER, 17, True, "middle")
    s.rect(
        horizontal_padding, action_top, action_width, action_height,
        CYAN, "NOTES BUTTON — FIXED HIT AREA 286×96 dp", .10, stroke=5, rx=14,
        element_id="companion-notes-hit-area",
    )
    s.rect(
        horizontal_padding + safe_inset, action_top + safe_inset,
        action_width - 2 * safe_inset, action_height - 2 * safe_inset,
        GREEN, "RECOMMENDED ART SAFE", .035, "10 7", 2, rx=8,
        element_id="companion-notes-art-safe-area",
    )
    s.rect(
        walkthrough_x, action_top, action_width, action_height,
        CYAN, "WALKTHROUGH BUTTON — FIXED HIT AREA 286×96 dp", .10, stroke=5, rx=14,
        element_id="companion-walkthrough-hit-area",
    )
    s.rect(
        walkthrough_x + safe_inset, action_top + safe_inset,
        action_width - 2 * safe_inset, action_height - 2 * safe_inset,
        GREEN, "RECOMMENDED ART SAFE", .035, "10 7", 2, rx=8,
        element_id="companion-walkthrough-art-safe-area",
    )
    open_area_y = action_top + action_height + 32
    s.pattern_rect(
        horizontal_padding, open_area_y, COMPANION_REFERENCE_WIDTH_PX - 2 * horizontal_padding,
        1016 - open_area_y, "dynamic", GREEN,
        "OPEN AREA — reserved for future fixed Companion actions",
    )
    s.note(300, 560, [
        "IMMERSIVE AUTHORING RULE",
        "Paint the Companion title and surrounding decoration into this background.",
        "Dedicated Notes/Walkthrough normal/pressed assets supply action art and labels.",
        "Hit areas are fixed by AdventurePad; artists never define interaction coordinates.",
        "Dashed green areas are recommended 16 dp essential-art margins.",
        "Standard supplies visible header/buttons; runtime still owns semantics and press input.",
    ], 720)
    s.legend(1050)
    return s


def notes():
    s, hh = page_frame("NOTES / notes.background", "Standard: opaque native page. Immersive: artwork is the page; native editor remains keyboard-sensitive")
    s.rect(55, 48, 96, 96, AMBER, "BACK 48 dp", .08, stroke=3)
    s.rect(1087, 48, 96, 112, AMBER, "CLOSE 48×56 dp", .08, stroke=3)
    y = 32 + hh + 32
    s.rect(69, y, 1102, 42, AMBER, "Auto-save message — contrast critical", .06, stroke=2)
    s.pattern_rect(69, y + 60, 1102, 740, "dynamic", CYAN, "OutlinedTextField: weight(1), minLines 8, scrollable text")
    s.rect(69, 780, 1102, 250, MAGENTA, "IME/keyboard may shrink this region upward (adjustResize)", .07, "18 10", 4)
    s.note(300, 300, ["IMMERSIVE TEXT SAFE AREA", "Keep this region pale and low contrast; native content padding is 16 dp.", "Done is the keyboard IME action, not an in-page artwork button.", "Text/placeholder/cursor/outline remain native and dark in Immersive."], 640)
    s.legend(1050)
    return s


def walkthrough():
    s, hh = page_frame("WALKTHROUGH — READER / walkthrough.background", "Standard: opaque native page. Immersive: artwork is the page; native controls remain conditional", True)
    buttons = [(53, 96, "BACK"), (560, 120, "READER"), (680, 120, "SEARCH"), (800, 144, "CONTENTS"), (944, 90, "MORE"), (1075, 108, "CLOSE")]
    for x, w, label in buttons:
        s.rect(x, 40, w, 96, AMBER, label, .065, stroke=2)
    s.rect(37, 144, 1166, 110, CYAN, "Optional settings/search bar — dynamic height", .05, "16 10", 4)
    s.pattern_rect(37, 144 + hh, 1166, 903 - hh, "dynamic", CYAN, "Reader / Contents / Search results — scrolling live content")
    s.rect(69, 176 + hh, 1102, 820 - hh, RED, "Immersive reading area: keep pale/quiet; dark native text; 16 dp H / 12 dp V padding", .035, stroke=3)
    s.note(330, 430, ["TOOLBAR RULES", "Row padding 8 dp horizontal / 4 dp vertical; back/close 48×48 dp min.", "READER/SEARCH/CONTENTS are content-width, 40 dp min height.", "MORE is 40 dp min height; menus overlay dynamically.", "Search, contents and reader settings can add/reflow native UI."], 670)
    s.legend(1050)
    return s


def button(title, state, runtime, utility=False):
    w, h = (264, 112) if utility else (528, 224)
    s = Svg(w, h, "", "")
    s.parts.append(f'<rect x="12" y="10" width="{w-24}" height="{h-20}" rx="12" fill="#101820" fill-opacity=".92"/>')
    inset = 24 if utility else 48
    if utility:
        s.text(w / 2, 24, f"{title} — {state.upper()}", WHITE, 16, True, "middle")
        s.rect(18, 36, w - 36, 58, AMBER, "", .07, "9 6", 2, 8)
        s.text(w / 2, 55, "132×56 dp minimum @ 2× reference", MUTED, 10, False, "middle")
        s.text(w / 2, 74, "STANDARD NATIVE LABEL ZONE", AMBER, 10, True, "middle")
        s.text(w / 2, 91, "IMMERSIVE ART MAY SUPPLY LABEL", CYAN, 9, True, "middle")
        s.text(w / 2, 103, "suggested slice: 24 px", GREEN, 9, True, "middle")
    else:
        s.text(24, 34, f"{title} — {state.upper()}", WHITE, 22, True)
        s.text(w - 24, 34, "SOURCE 528×224", MUTED, 14, True, "end")
        s.rect(inset, 50, w - 2 * inset, 142, GREEN, "", .08, "12 8", 3, 10)
        s.text(w / 2, 70, "suggested nine-slice inset: 48 px", GREEN, 13, True, "middle")
        s.rect(int(w * .15), 86, int(w * .70), 72, AMBER, "", .08, "10 7", 3)
        s.text(w / 2, 116, "STANDARD NATIVE LABEL ZONE", AMBER, 16, True, "middle")
        s.text(w / 2, 140, "IMMERSIVE ART MAY SUPPLY LABEL", CYAN, 13, True, "middle")
        s.text(w / 2, 181, f"ASSET STATE: {state.upper()} — VISUAL CHANGE ONLY", CYAN, 12, True, "middle")
        s.text(w / 2, 210, "runtime: width 34%; height clamp(22%, 56…88 dp)", MUTED, 12, False, "middle")
    return s


def companion_action_button(title, state):
    rect = COMPANION_ACTIONS[
        "notesReferenceRectPx" if title == "NOTES" else "walkthroughReferenceRectPx"
    ]
    w, h = rect[2] - rect[0], rect[3] - rect[1]
    inset = COMPANION_HORIZONTAL_PADDING_DP * REFERENCE_DENSITY_PX_PER_DP
    s = Svg(w, h, "", "")
    s.parts.append(
        f'<rect x="8" y="6" width="{w-16}" height="{h-12}" rx="12" '
        'fill="#101820" fill-opacity=".92"/>'
    )
    s.text(18, 30, f"{title} — {state.upper()}", WHITE, 17, True)
    s.text(w - 18, 30, f"SOURCE {w}×{h}", MUTED, 12, True, "end")
    s.rect(inset, 44, w - 2 * inset, h - 88, GREEN, "", .08, "10 7", 2, 8)
    s.text(w / 2, 68, "32 px slice • native/immersive label zone", GREEN, 11, True, "middle")
    s.text(w / 2, 108, f"{state.upper()} • FIXED 286×96 DP HIT AREA", CYAN, 12, True, "middle")
    s.text(w / 2, 136, "IMMERSIVE ART SUPPLIES LABEL", AMBER, 10, True, "middle")
    return s


def panel_frame():
    s = Svg(1240, 360, "PANEL FRAME / panel.frame", "64 px nine-slice border around the live lower mirror in Immersive Split View")
    s.rect(64, 64, 1112, 232, RED, "CENTER CLEARED DURING IMPORT — live mirrored content remains visible", .05, "18 12", 4)
    s.rect(0, 0, 1240, 360, GREEN, "", .035, "", 5)
    s.line(64, 0, 64, 360, AMBER, 3, "10 8")
    s.line(1176, 0, 1176, 360, AMBER, 3, "10 8")
    s.line(0, 64, 1240, 64, AMBER, 3, "10 8")
    s.line(0, 296, 1240, 296, AMBER, 3, "10 8")
    s.text(620, 190, "IMPORTER CLEARS CENTER • ARTWORK IS NON-INTERACTIVE", RED, 22, True, "middle")
    s.text(620, 338, "GREEN OUTER BAND / AMBER GUIDES = 64 px SUGGESTED NINE-SLICE BORDER", GREEN, 17, True, "middle")
    return s


def generate():
    guides = {
        "top-gameplay-template": top(),
        "bottom-trackpad-template": bottom_trackpad(),
        "bottom-split-template": bottom_split(),
        "companion-template": companion(),
        "notes-template": notes(),
        "walkthrough-template": walkthrough(),
        "button-lmb-normal-template": button("LMB BUTTON ART", "normal", "Declared source canvas 528×224", False),
        "button-lmb-pressed-template": button("LMB BUTTON ART", "pressed", "Declared source canvas 528×224", False),
        "button-rmb-normal-template": button("RMB BUTTON ART", "normal", "Declared source canvas 528×224", False),
        "button-rmb-pressed-template": button("RMB BUTTON ART", "pressed", "Declared source canvas 528×224", False),
        "button-companion-normal-template": button("COMPANION", "normal", "264×112 source = 132×56 dp at 2× reference", True),
        "button-companion-pressed-template": button("COMPANION", "pressed", "264×112 source = 132×56 dp at 2× reference", True),
        "button-settings-normal-template": button("SETTINGS", "normal", "264×112 source = 132×56 dp at 2× reference", True),
        "button-settings-pressed-template": button("SETTINGS", "pressed", "264×112 source = 132×56 dp at 2× reference", True),
        "button-notes-normal-template": companion_action_button("NOTES", "normal"),
        "button-notes-pressed-template": companion_action_button("NOTES", "pressed"),
        "button-walkthrough-normal-template": companion_action_button("WALKTHROUGH", "normal"),
        "button-walkthrough-pressed-template": companion_action_button("WALKTHROUGH", "pressed"),
        "panel-frame-template": panel_frame(),
    }
    converter = shutil.which("rsvg-convert")
    if not converter:
        raise SystemExit("rsvg-convert is required to render creator PNG guides")
    OUT.mkdir(parents=True, exist_ok=True)
    SOURCE.mkdir(parents=True, exist_ok=True)
    for obsolete in (
        "button-lmb-template", "button-rmb-template",
        "button-companion-template", "button-settings-template",
    ):
        (OUT / f"{obsolete}.png").unlink(missing_ok=True)
        (SOURCE / f"{obsolete}.svg").unlink(missing_ok=True)
    for name, guide in guides.items():
        svg_path = SOURCE / f"{name}.svg"
        png_path = OUT / f"{name}.png"
        svg_path.write_text(guide.finish(), encoding="utf-8")
        subprocess.run([converter, str(svg_path), "-o", str(png_path)], check=True)
        print(png_path.relative_to(ROOT))


if __name__ == "__main__":
    generate()
