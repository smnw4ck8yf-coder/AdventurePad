#!/usr/bin/env python3
"""Generate Milestone 7 creator-facing guides; never generates skin artwork."""

from pathlib import Path
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

    def rect(self, x, y, w, h, color, label="", fill_opacity=.12, dash="", stroke=4, rx=0):
        dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{color}" fill-opacity="{fill_opacity}" stroke="{color}" stroke-width="{stroke}"{dash_attr}/>' )
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
    s = Svg(1920, 1080, "TOP GAMEPLAY / top.surround", "Exact display canvas 1920×1080 px — every actual runtime game viewport is protected")
    s.pattern_rect(0, 0, 1920, 1080, "danger", RED)
    s.rect(96, 0, 1728, 1080, AMBER, "", .05, "24 14", 6)
    s.rect(240, 0, 1440, 1080, CYAN, "", .06, "24 14", 6)
    s.rect(0, 0, 96, 1080, GREEN, "", .13)
    s.rect(1824, 0, 96, 1080, GREEN, "", .13)
    s.note(640, 150, [
        "RUNTIME CONTRACT",
        "Bitmap fills the display, then SkinSurroundView clipOutRect(viewport).",
        "No pixel is guaranteed artwork-visible: full-screen 16:9 hides all art.",
        "4:3 leaves 240 px per side; 16:10 leaves 96 px per side on this canvas.",
        "ScummVM GUI/overlay reports full display protected (no surround).",
        "Split View may expand the upper crop and changes the protected rectangle.",
    ], 900)
    s.text(270, 930, "CYAN 4:3 protected edge", CYAN, 19, True)
    s.text(110, 965, "AMBER 16:10 protected edge", AMBER, 19, True)
    s.text(960, 930, "RED HATCH = 16:9 full-screen protected viewport", RED, 22, True, "middle")
    s.text(120, 1010, "Potential surround only; never place required information here", GREEN, 22, True)
    s.legend(1050)
    return s


def bottom_base(title, subtitle):
    s = Svg(1240, 1080, title, subtitle)
    s.rect(0, 0, 1240, 1080, MAGENTA, "", .025, "18 12", 5)
    s.text(620, 105, "Reference scenario shown: safeDrawing = 0 px, density = 2 px/dp", MAGENTA, 17, True, "middle")
    return s


def bottom_trackpad():
    s = bottom_base("BOTTOM — NORMAL TRACKPAD", "Exact canvas 1240×1080 px; source constants shown in dp/fractions; reference scenario is explicitly conditional")
    s.pattern_rect(24, 6, 1192, 940, "dynamic", CYAN, "TouchSurface 1192×940 px in reference scenario")
    s.rect(24, 770, 405, 176, RED, "LMB touch + artwork", .15, stroke=4)
    s.rect(811, 770, 405, 176, RED, "RMB touch + artwork", .15, stroke=4)
    s.rect(24, 6, 1192, 764, GREEN, "Trackpad gesture area (excluding current L/R overlays)", .055, stroke=3)
    s.rect(76, 826, 300, 88, AMBER, "native label safe zone", .04, "12 8", 2)
    s.rect(864, 826, 300, 88, AMBER, "native label safe zone", .04, "12 8", 2)
    s.rect(24, 952, 264, 112, AMBER, "COMPANION min 132×56 dp", .11, stroke=4, rx=14)
    s.rect(976, 952, 264, 112, AMBER, "SETTINGS min 132×56 dp", .11, stroke=4, rx=14)
    s.rect(500, 972, 240, 72, AMBER, "● Online / Offline", .06, "10 8", 3)
    s.line(0, 952, 1240, 952, CYAN, 4, "18 10")
    s.note(350, 120, [
        "SOURCE-BACKED PLACEMENT",
        "Touch outer padding: horizontal 12 dp; vertical 3 dp.",
        "L/R width = 34% of TouchSurface each.",
        "L/R height = clamp(22% of surface, 56 dp, 88 dp).",
        "Utility row padding: horizontal 12 dp; vertical 4 dp.",
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
    s, hh = page_frame("COMPANION — HOME / companion.background", "Background themes full lower display beneath unchanged centered 94% native page")
    s.rect(55, 48, 96, 112, AMBER, "", .08, "10 8", 2)
    s.text(103, 113, "back*", AMBER, 16, True, "middle")
    s.rect(1087, 48, 96, 112, AMBER, "", .08, "10 8", 2)
    s.text(1135, 113, "close", AMBER, 16, True, "middle")
    s.pattern_rect(69, 32 + hh + 34, 1102, 820, "dynamic", CYAN, "Scrollable content: 16 dp padding, 8 dp gaps")
    for y, label in [(250, "NOTES"), (380, "WALKTHROUGH"), (510, "MANUAL"), (640, "RECENT DIALOGUE"), (770, "STATISTICS")]:
        s.rect(85, y, 1070, 96, AMBER, label + " — native card/text", .035, stroke=2, rx=12)
    s.note(330, 870, ["DECORATION RULE", "Background colors may theme the page; do not reduce native text contrast.", "Home cards and availability labels are dynamic native content."], 600)
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
        s.text(w / 2, 74, "NATIVE LABEL SAFE ZONE", AMBER, 10, True, "middle")
        s.text(w / 2, 91, "NO BAKED-IN LABEL", RED, 10, True, "middle")
        s.text(w / 2, 103, "suggested slice: 24 px", GREEN, 9, True, "middle")
    else:
        s.text(24, 34, f"{title} — {state.upper()}", WHITE, 22, True)
        s.text(w - 24, 34, "SOURCE 528×224", MUTED, 14, True, "end")
        s.rect(inset, 50, w - 2 * inset, 142, GREEN, "", .08, "12 8", 3, 10)
        s.text(w / 2, 70, "suggested nine-slice inset: 48 px", GREEN, 13, True, "middle")
        s.rect(int(w * .15), 86, int(w * .70), 72, AMBER, "", .08, "10 7", 3)
        s.text(w / 2, 116, "NATIVE LABEL SAFE ZONE", AMBER, 16, True, "middle")
        s.text(w / 2, 140, "NO BAKED-IN LABELS", AMBER, 13, True, "middle")
        s.text(w / 2, 181, f"ASSET STATE: {state.upper()} — VISUAL CHANGE ONLY", CYAN, 12, True, "middle")
        s.text(w / 2, 210, "runtime: width 34%; height clamp(22%, 56…88 dp)", MUTED, 12, False, "middle")
    return s


def panel_frame():
    s = Svg(1240, 360, "PANEL FRAME / panel.frame", "Nine-slice source 1240×360 — runtime draws border patches only around Companion-family pages")
    s.rect(64, 64, 1112, 232, RED, "CENTER PATCH OMITTED BY RUNTIME — native content remains visible", .05, "18 12", 4)
    s.rect(0, 0, 1240, 360, GREEN, "", .035, "", 5)
    s.line(64, 0, 64, 360, AMBER, 3, "10 8")
    s.line(1176, 0, 1176, 360, AMBER, 3, "10 8")
    s.line(0, 64, 1240, 64, AMBER, 3, "10 8")
    s.line(0, 296, 1240, 296, AMBER, 3, "10 8")
    s.text(620, 190, "KEEP CENTER TRANSPARENT • ARTWORK IS NON-INTERACTIVE", RED, 22, True, "middle")
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
