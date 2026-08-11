#!/usr/bin/env python3
"""Build and validate AdventurePad v1 skins from one master PNG.

The crop geometry and export contract come exclusively from
AUTHORING_TEMPLATE_SPEC.json. The implementation intentionally uses only the
Python standard library so the proof can be reproduced without Pillow.
"""

from __future__ import annotations

import argparse
import binascii
import hashlib
import json
from pathlib import Path
import re
import struct
import tempfile
import zipfile
import zlib


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PROOF_METADATA = {
    "id": "org.adventurepad.proof.rainbow",
    "name": "AdventurePad Rainbow Proof",
    "author": "AdventurePad Development",
    "packageVersion": "1.0.0",
}
PROOF_COLORS = {
    "top.surround": (220, 42, 42, 255),
    "bottom.trackpad.background": (28, 87, 214, 255),
    "bottom.split.background": (28, 164, 82, 255),
    "companion.background": (242, 126, 26, 255),
    "notes.background": (132, 54, 190, 255),
    "walkthrough.background": (245, 210, 44, 255),
    "trackpad.surface": (205, 210, 216, 255),
    "panel.frame": (74, 50, 34, 255),
    "button.lmb.normal": (232, 232, 232, 255),
    "button.rmb.normal": (214, 214, 214, 255),
    "button.companion.normal": (238, 224, 196, 255),
    "button.settings.normal": (205, 218, 226, 255),
    "button.lmb.pressed": (92, 92, 92, 255),
    "button.rmb.pressed": (70, 70, 70, 255),
    "button.companion.pressed": (112, 91, 60, 255),
    "button.settings.pressed": (70, 91, 104, 255),
    "button.notes.normal": (231, 188, 103, 255),
    "button.notes.pressed": (142, 92, 45, 255),
    "button.walkthrough.normal": (214, 164, 72, 255),
    "button.walkthrough.pressed": (112, 67, 34, 255),
}
ID_PATTERN = re.compile(r"[a-z0-9]+(?:[._-][a-z0-9]+)+$")
VERSION_PATTERN = re.compile(r"[0-9]+(?:\.[0-9]+){0,2}(?:[-+][A-Za-z0-9.-]+)?$")


class BuildError(ValueError):
    pass


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xFFFFFFFF)


def write_rgba_png(path: Path, width: int, height: int, pixels: bytes | bytearray) -> None:
    if len(pixels) != width * height * 4:
        raise BuildError("RGBA pixel buffer has the wrong size")
    rows = bytearray()
    stride = width * 4
    for y in range(height):
        rows.append(0)
        rows.extend(pixels[y * stride:(y + 1) * stride])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        PNG_SIGNATURE
        + _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + _chunk(b"IDAT", zlib.compress(bytes(rows), level=9))
        + _chunk(b"IEND", b"")
    )


def read_rgba_png(path: Path) -> tuple[int, int, bytearray]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise BuildError(f"{path} is not a PNG")
    offset = len(PNG_SIGNATURE)
    ihdr = None
    palette = None
    transparency = None
    compressed = bytearray()
    while offset < len(data):
        if offset + 12 > len(data):
            raise BuildError("Truncated PNG chunk")
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = data[offset + 8 + length:offset + 12 + length]
        if len(payload) != length or len(crc) != 4:
            raise BuildError("Truncated PNG payload")
        expected = struct.unpack(">I", crc)[0]
        if (binascii.crc32(kind + payload) & 0xFFFFFFFF) != expected:
            raise BuildError(f"PNG chunk {kind!r} has an invalid CRC")
        if kind == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", payload)
        elif kind == b"PLTE":
            palette = payload
        elif kind == b"tRNS":
            transparency = payload
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
        offset += 12 + length
    if ihdr is None:
        raise BuildError("PNG has no IHDR")
    width, height, depth, color_type, compression, filtering, interlace = ihdr
    if width < 1 or height < 1 or compression != 0 or filtering != 0:
        raise BuildError("Unsupported PNG header")
    if depth != 8 or color_type not in (0, 2, 3, 4, 6) or interlace != 0:
        raise BuildError("The reference builder supports non-interlaced 8-bit RGB/RGBA, grayscale, or indexed PNGs")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    stride = width * channels
    try:
        raw = zlib.decompress(bytes(compressed))
    except zlib.error as exc:
        raise BuildError("PNG pixel data is corrupt") from exc
    if len(raw) != height * (stride + 1):
        raise BuildError("PNG pixel data has an unexpected size")
    decoded = bytearray(height * stride)
    source = 0
    for y in range(height):
        filter_type = raw[source]
        source += 1
        row = raw[source:source + stride]
        source += stride
        previous = (y - 1) * stride
        current = y * stride
        for x, value in enumerate(row):
            left = decoded[current + x - channels] if x >= channels else 0
            up = decoded[previous + x] if y else 0
            upper_left = decoded[previous + x - channels] if y and x >= channels else 0
            if filter_type == 0:
                result = value
            elif filter_type == 1:
                result = value + left
            elif filter_type == 2:
                result = value + up
            elif filter_type == 3:
                result = value + ((left + up) >> 1)
            elif filter_type == 4:
                p = left + up - upper_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upper_left)
                predictor = left if pa <= pb and pa <= pc else (up if pb <= pc else upper_left)
                result = value + predictor
            else:
                raise BuildError(f"Unsupported PNG filter {filter_type}")
            decoded[current + x] = result & 0xFF
    rgba = bytearray(width * height * 4)
    for index in range(width * height):
        src = index * channels
        dst = index * 4
        if color_type == 6:
            rgba[dst:dst + 4] = decoded[src:src + 4]
        elif color_type == 2:
            rgba[dst:dst + 3] = decoded[src:src + 3]
            rgba[dst + 3] = 255
        elif color_type == 4:
            grey, alpha = decoded[src:src + 2]
            rgba[dst:dst + 4] = bytes((grey, grey, grey, alpha))
        elif color_type == 0:
            grey = decoded[src]
            alpha = 0 if transparency == bytes((0, grey)) else 255
            rgba[dst:dst + 4] = bytes((grey, grey, grey, alpha))
        else:
            palette_index = decoded[src]
            if palette is None or palette_index * 3 + 2 >= len(palette):
                raise BuildError("Indexed PNG references a missing palette entry")
            rgba[dst:dst + 3] = palette[palette_index * 3:palette_index * 3 + 3]
            rgba[dst + 3] = transparency[palette_index] if transparency and palette_index < len(transparency) else 255
    return width, height, rgba


def load_spec(path: Path) -> dict:
    try:
        spec = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise BuildError(f"Could not load authoring spec: {exc}") from exc
    if spec.get("schemaVersion") != 1:
        raise BuildError("Unsupported authoring spec schemaVersion")
    canvas = spec.get("masterCanvas")
    regions = spec.get("regions")
    if not isinstance(canvas, dict) or not isinstance(regions, list) or not regions:
        raise BuildError("Authoring spec is missing masterCanvas or regions")
    seen = set()
    indices = set()
    for region in regions:
        slot = region.get("slotId")
        index = region.get("index")
        if not isinstance(slot, str) or slot in seen:
            raise BuildError(f"Every authoring slot must occur exactly once: {slot!r}")
        if not isinstance(index, int) or index in indices:
            raise BuildError(f"Every authoring region index must occur exactly once: {index!r}")
        seen.add(slot)
        indices.add(index)
        origin, size, output = region.get("origin"), region.get("size"), region.get("output")
        if not all(isinstance(value, dict) for value in (origin, size, output)):
            raise BuildError(f"Region {slot} has incomplete geometry")
        x, y = origin.get("x"), origin.get("y")
        width, height = size.get("width"), size.get("height")
        out_width, out_height = output.get("width"), output.get("height")
        values = (x, y, width, height, out_width, out_height)
        if not all(isinstance(value, int) for value in values) or min(width, height, out_width, out_height) < 1:
            raise BuildError(f"Region {slot} has invalid geometry")
        if x < 0 or y < 0 or x + width > canvas.get("width", 0) or y + height > canvas.get("height", 0):
            raise BuildError(f"Region {slot} escapes the master canvas")
        if output.get("format") != "png":
            raise BuildError(f"Region {slot} is not a PNG output")
    if "preview" not in seen or "panel.frame" not in seen:
        raise BuildError("Authoring spec must include preview and panel.frame")
    return spec


def crop_rgba(pixels: bytearray, master_width: int, x: int, y: int, width: int, height: int) -> bytearray:
    output = bytearray(width * height * 4)
    source_stride = master_width * 4
    target_stride = width * 4
    for row in range(height):
        start = (y + row) * source_stride + x * 4
        output[row * target_stride:(row + 1) * target_stride] = pixels[start:start + target_stride]
    return output


def scale_nearest(pixels: bytearray, width: int, height: int, out_width: int, out_height: int) -> bytearray:
    if (width, height) == (out_width, out_height):
        return pixels
    output = bytearray(out_width * out_height * 4)
    for y in range(out_height):
        source_y = y * height // out_height
        for x in range(out_width):
            source = (source_y * width + x * width // out_width) * 4
            target = (y * out_width + x) * 4
            output[target:target + 4] = pixels[source:source + 4]
    return output


def clear_transparent_center(region: dict, pixels: bytearray, width: int, height: int) -> None:
    center = region.get("transparentCenter")
    if not center:
        return
    x, y = center["x"], center["y"]
    right, bottom = x + center["width"], y + center["height"]
    if x < 0 or y < 0 or right > width or bottom > height:
        raise BuildError(f"Transparent center for {region['slotId']} escapes its output")
    alpha = center.get("requiredAlpha")
    for row in range(y, bottom):
        for column in range(x, right):
            pixels[(row * width + column) * 4 + 3] = alpha


def asset_path(slot: str) -> str:
    return "preview.png" if slot == "preview" else "assets/" + slot.replace(".", "/") + ".png"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build_manifest(spec: dict, metadata: dict, hashes: dict[str, str]) -> dict:
    if not ID_PATTERN.fullmatch(metadata["id"]) or metadata["id"].startswith("builtin."):
        raise BuildError("Skin id is invalid or uses the builtin namespace")
    if not VERSION_PATTERN.fullmatch(metadata["packageVersion"]):
        raise BuildError("packageVersion is invalid")
    assets = {}
    for region in sorted(spec["regions"], key=lambda item: item["index"]):
        slot = region["slotId"]
        entry = {
            "path": asset_path(slot),
            "scale": region["scaleMode"],
            "canvas": {"width": region["output"]["width"], "height": region["output"]["height"]},
            "sha256": hashes[slot],
        }
        if "nineSliceInsets" in region:
            entry["sliceInsets"] = region["nineSliceInsets"]
        assets[slot] = entry
    return {
        "formatVersion": 1,
        **metadata,
        "minimumAdventurePadVersionCode": 1,
        "features": ["lower-controls", "upper-surround", "companion-surfaces", "button-states"],
        "assets": assets,
        "colors": {
            "background": "#111417", "surface": "#1A1F24", "surfaceRaised": "#22282E",
            "surfacePressed": "#303841", "outline": "#46515B", "outlineStrong": "#64717D",
            "primary": "#D8B86A", "onPrimary": "#211B0D", "textPrimary": "#F2F3F5",
            "textSecondary": "#ADB5BD",
        },
        "metrics": {"spacingScale": 1.0, "cornerScale": 1.0, "borderScale": 1.0},
        "extensions": {"authoringTemplateVersion": spec.get("templateVersion")},
    }


def build_skin(master_path: Path, spec_path: Path, metadata: dict, output_path: Path, extracted: Path | None = None) -> dict:
    spec = load_spec(spec_path)
    width, height, master = read_rgba_png(master_path)
    expected = (spec["masterCanvas"]["width"], spec["masterCanvas"]["height"])
    if (width, height) != expected:
        raise BuildError(f"Master PNG is {width}x{height}; expected {expected[0]}x{expected[1]}")
    with tempfile.TemporaryDirectory(prefix="adventurepad-proof-") as temporary:
        package_root = Path(temporary)
        hashes = {}
        for region in sorted(spec["regions"], key=lambda item: item["index"]):
            origin, size, output = region["origin"], region["size"], region["output"]
            pixels = crop_rgba(master, width, origin["x"], origin["y"], size["width"], size["height"])
            pixels = scale_nearest(pixels, size["width"], size["height"], output["width"], output["height"])
            clear_transparent_center(region, pixels, output["width"], output["height"])
            destination = package_root / asset_path(region["slotId"])
            write_rgba_png(destination, output["width"], output["height"], pixels)
            hashes[region["slotId"]] = sha256(destination)
        manifest = build_manifest(spec, metadata, hashes)
        (package_root / "skin.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(package_root.rglob("*")):
                if path.is_file():
                    entry = zipfile.ZipInfo(path.relative_to(package_root).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    entry.external_attr = 0o100644 << 16
                    archive.writestr(entry, path.read_bytes(), compresslevel=9)
        if extracted:
            extracted.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(output_path) as archive:
                archive.extractall(extracted)
    return validate_package(output_path, spec_path)


def validate_package(package_path: Path, spec_path: Path) -> dict:
    spec = load_spec(spec_path)
    expected_regions = {region["slotId"]: region for region in spec["regions"]}
    with zipfile.ZipFile(package_path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or "skin.json" not in names or "preview.png" not in names:
            raise BuildError("Package structure is invalid")
        if any(name.startswith("/") or ".." in Path(name).parts or "\\" in name for name in names):
            raise BuildError("Package contains an unsafe path")
        manifest = json.loads(archive.read("skin.json"))
        if manifest.get("formatVersion") != 1 or set(manifest.get("assets", {})) != set(expected_regions):
            raise BuildError("skin.json does not contain every authoritative region exactly once")
        for slot, region in expected_regions.items():
            asset = manifest["assets"][slot]
            path = asset["path"]
            payload = archive.read(path)
            if hashlib.sha256(payload).hexdigest() != asset["sha256"]:
                raise BuildError(f"Checksum mismatch for {path}")
            with tempfile.NamedTemporaryFile(suffix=".png") as temporary:
                temporary.write(payload)
                temporary.flush()
                width, height, pixels = read_rgba_png(Path(temporary.name))
            output = region["output"]
            if (width, height) != (output["width"], output["height"]):
                raise BuildError(f"{slot} has incorrect dimensions")
            if region.get("alphaMode") == "opaque" and any(pixels[index] != 255 for index in range(3, len(pixels), 4)):
                raise BuildError(f"{slot} must be fully opaque")
            center = region.get("transparentCenter")
            if center:
                for y in range(center["y"], center["y"] + center["height"]):
                    for x in range(center["x"], center["x"] + center["width"]):
                        if pixels[(y * width + x) * 4 + 3] != center["requiredAlpha"]:
                            raise BuildError(f"{slot} transparent center is invalid")
        return {
            "package": str(package_path),
            "regions": len(expected_regions),
            "files": len(names),
            "id": manifest["id"],
            "name": manifest["name"],
            "status": "valid",
        }


def fill_rect(pixels: bytearray, canvas_width: int, x: int, y: int, width: int, height: int, color: tuple[int, int, int, int]) -> None:
    row = bytes(color) * width
    stride = canvas_width * 4
    for current_y in range(y, y + height):
        start = current_y * stride + x * 4
        pixels[start:start + len(row)] = row


def generate_rainbow_source(spec_path: Path, output_path: Path) -> None:
    spec = load_spec(spec_path)
    width, height = spec["masterCanvas"]["width"], spec["masterCanvas"]["height"]
    pixels = bytearray(width * height * 4)
    for region in spec["regions"]:
        slot, origin, size = region["slotId"], region["origin"], region["size"]
        if slot == "preview":
            stripe_colors = [PROOF_COLORS[name] for name in (
                "top.surround", "bottom.trackpad.background", "bottom.split.background",
                "companion.background", "notes.background", "walkthrough.background",
            )]
            stripe_width = size["width"] // len(stripe_colors)
            for index, color in enumerate(stripe_colors):
                left = origin["x"] + index * stripe_width
                current_width = size["width"] - index * stripe_width if index == len(stripe_colors) - 1 else stripe_width
                fill_rect(pixels, width, left, origin["y"], current_width, size["height"], color)
        else:
            color = PROOF_COLORS.get(slot, (255, 0, 255, 255))
            fill_rect(pixels, width, origin["x"], origin["y"], size["width"], size["height"], color)
        center = region.get("transparentCenter")
        if center:
            fill_rect(
                pixels, width, origin["x"] + center["x"], origin["y"] + center["y"],
                center["width"], center["height"], (0, 0, 0, center["requiredAlpha"]),
            )
    write_rgba_png(output_path, width, height, pixels)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    generate = subparsers.add_parser("generate-rainbow", help="generate the clean proof master PNG")
    generate.add_argument("--spec", required=True, type=Path)
    generate.add_argument("--output", required=True, type=Path)
    build = subparsers.add_parser("build", help="slice and package a master PNG")
    build.add_argument("--master", required=True, type=Path)
    build.add_argument("--spec", required=True, type=Path)
    build.add_argument("--id", required=True)
    build.add_argument("--name", required=True)
    build.add_argument("--author", required=True)
    build.add_argument("--package-version", default="1.0.0")
    build.add_argument("--output", required=True, type=Path)
    build.add_argument("--extracted", type=Path)
    validate = subparsers.add_parser("validate", help="validate a generated .apskin")
    validate.add_argument("--package", required=True, type=Path)
    validate.add_argument("--spec", required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.command == "generate-rainbow":
            generate_rainbow_source(args.spec, args.output)
            print(args.output)
        elif args.command == "build":
            result = build_skin(
                args.master, args.spec,
                {"id": args.id, "name": args.name, "author": args.author, "packageVersion": args.package_version},
                args.output, args.extracted,
            )
            print(json.dumps(result, indent=2))
        else:
            print(json.dumps(validate_package(args.package, args.spec), indent=2))
    except (BuildError, OSError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
