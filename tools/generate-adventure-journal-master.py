#!/usr/bin/env python3
"""Create the Adventure Journal proof as one registered master PNG.

The output is creator-equivalent input: the Android builder, not this script,
performs all runtime slicing, manifest generation, validation, and installation.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import random

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps


def cover(image: Image.Image, size: tuple[int, int], box: tuple[int, int, int, int] | None = None) -> Image.Image:
    source = image.crop(box) if box else image
    return ImageOps.fit(source, size, method=Image.Resampling.LANCZOS, centering=(0.5, 0.5)).convert("RGBA")


def tint(image: Image.Image, black: str, white: str, strength: float = 0.72) -> Image.Image:
    colored = ImageOps.colorize(ImageOps.grayscale(image), black=black, white=white).convert("RGBA")
    return Image.blend(image.convert("RGBA"), colored, strength)


def quiet_parchment(source: Image.Image, size: tuple[int, int], variant: int) -> Image.Image:
    # Crop inside the generated journal's outer border, retaining a little wood/brass edge.
    page = cover(source, size, (105, 28, 1430, 1000))
    page = ImageEnhance.Color(page).enhance(0.72)
    page = ImageEnhance.Contrast(page).enhance(0.84)
    wash = Image.new("RGBA", size, (255, 235, 190, 30 if variant == 0 else 20))
    page = Image.alpha_composite(page, wash)
    draw = ImageDraw.Draw(page, "RGBA")
    w, h = size
    draw.rounded_rectangle((22, 22, w - 23, h - 23), radius=24, outline=(91, 55, 25, 150), width=5)
    draw.rounded_rectangle((31, 31, w - 32, h - 32), radius=20, outline=(188, 137, 57, 150), width=3)
    if variant:
        # A restrained binding gutter outside the primary 16 dp text inset.
        draw.line((50, 178, 50, h - 48), fill=(94, 58, 29, 35), width=3)
        draw.line((55, 178, 55, h - 48), fill=(255, 244, 211, 40), width=2)
    return page


def material_panel(source: Image.Image, size: tuple[int, int], kind: str) -> Image.Image:
    if kind == "wood":
        panel = cover(source, size, (0, 0, 1536, 270))
        panel = tint(panel, "#160d08", "#70401f", 0.62)
    elif kind == "leather":
        panel = cover(source, size, (0, 120, 260, 930))
        panel = tint(panel, "#1b0908", "#713326", 0.54)
    else:
        panel = cover(source, size)
    draw = ImageDraw.Draw(panel, "RGBA")
    w, h = size
    draw.rectangle((8, 8, w - 9, h - 9), outline=(55, 25, 11, 220), width=8)
    draw.rectangle((18, 18, w - 19, h - 19), outline=(184, 126, 42, 190), width=3)
    return panel


def button(source: Image.Image, size: tuple[int, int], pressed: bool) -> Image.Image:
    panel = material_panel(source, size, "leather")
    if pressed:
        panel = ImageEnhance.Brightness(panel).enhance(0.66)
        panel = ImageEnhance.Contrast(panel).enhance(1.12)
    draw = ImageDraw.Draw(panel, "RGBA")
    w, h = size
    inset = max(12, min(w, h) // 9)
    draw.rounded_rectangle(
        (inset, inset, w - inset - 1, h - inset - 1),
        radius=max(10, inset // 2),
        outline=(210, 151, 56, 225 if not pressed else 155),
        width=max(3, inset // 8),
    )
    return panel


def add_paper_grain(image: Image.Image, seed: int) -> Image.Image:
    randomizer = random.Random(seed)
    noise = Image.new("L", image.size)
    noise.putdata([randomizer.randrange(92, 164) for _ in range(image.width * image.height)])
    noise = noise.filter(ImageFilter.GaussianBlur(1.4))
    grain = ImageOps.colorize(noise, black="#70471f", white="#fff0c5").convert("RGBA")
    grain.putalpha(14)
    return Image.alpha_composite(image, grain)


def make_assets(source: Image.Image, spec: dict) -> dict[str, Image.Image]:
    sizes = {
        region["slotId"]: (region["size"]["width"], region["size"]["height"])
        for region in spec["regions"]
    }
    notes = add_paper_grain(quiet_parchment(source, sizes["notes.background"], 0), 75)
    walkthrough = add_paper_grain(quiet_parchment(source, sizes["walkthrough.background"], 1), 76)
    assets = {
        "top.surround": material_panel(source, sizes["top.surround"], "wood"),
        "bottom.trackpad.background": material_panel(source, sizes["bottom.trackpad.background"], "wood"),
        "bottom.split.background": material_panel(source, sizes["bottom.split.background"], "wood"),
        "companion.background": material_panel(source, sizes["companion.background"], "leather"),
        "notes.background": notes,
        "walkthrough.background": walkthrough,
        "trackpad.surface": material_panel(source, sizes["trackpad.surface"], "leather"),
        "panel.frame": material_panel(source, sizes["panel.frame"], "wood"),
        "preview": cover(source, sizes["preview"]),
    }
    for control in ("lmb", "rmb", "companion", "settings"):
        for state in ("normal", "pressed"):
            slot = f"button.{control}.{state}"
            assets[slot] = button(source, sizes[slot], state == "pressed")
    return assets


def generate(source_path: Path, spec_path: Path, output_path: Path) -> None:
    source = Image.open(source_path).convert("RGBA")
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    canvas_size = (spec["masterCanvas"]["width"], spec["masterCanvas"]["height"])
    master = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    assets = make_assets(source, spec)
    for region in spec["regions"]:
        slot = region["slotId"]
        asset = assets[slot]
        expected = (region["size"]["width"], region["size"]["height"])
        if asset.size != expected:
            raise ValueError(f"{slot} is {asset.size}, expected {expected}")
        master.alpha_composite(asset, (region["origin"]["x"], region["origin"]["y"]))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    master.save(output_path, format="PNG", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--spec", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    generate(args.source, args.spec, args.output)
    print(args.output)


if __name__ == "__main__":
    main()
