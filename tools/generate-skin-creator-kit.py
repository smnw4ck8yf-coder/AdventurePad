#!/usr/bin/env python3
"""Build the public Skin Creator Pack as a local release artifact."""
from argparse import ArgumentParser
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parents[1]
KIT_SOURCE = ROOT / "skin-creator-kit"
AUTHORING_SOURCE = ROOT / "skin-authoring"
DOCS_SOURCE = ROOT / "docs"
CREATOR_LICENSE = ROOT / "CREATOR_ASSETS_LICENSE.md"
DEFAULT_OUTPUT = ROOT / "releases" / "AdventurePad-Skin-Creator-Pack.zip"
ARCHIVE_ROOT = Path("AdventurePad-Skin-Creator-Pack")

AUTHORING_FILES = (
    "AUTHORING_TEMPLATE_SPEC.json",
    "AUTHORING_TEMPLATE_SPEC.md",
    "AdventurePad-Skin-Template-v2.png",
    "AdventurePad-Skin-Template-v2.svg",
    "AdventurePad-Skin-Template-v2-reference.png",
    "AdventurePad-Skin-Template-v2-reference.svg",
    "AdventurePad-Skin-Example.png",
)
DOC_FILES = (
    "CUSTOM_SKINS.md",
    "SKIN_REFERENCE.md",
    "images/immersive-pirate-skin.jpg",
)
KIT_FILES = (
    "README.md",
    "ASSET_REQUIREMENTS.md",
    "LAYOUT_SPEC.md",
    "LAYOUT_SPEC.json",
)


def arguments():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument(
        "--psd",
        type=Path,
        required=True,
        help="read-only path to the user-supplied editable PSD",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"local ZIP destination (default: {DEFAULT_OUTPUT})",
    )
    return parser.parse_args()


def require_files(paths):
    missing = [path for path in paths if not path.is_file()]
    if missing:
        raise SystemExit("missing creator-pack input: " + ", ".join(str(path) for path in missing))


def add(archive, source, destination):
    archive.write(source, ARCHIVE_ROOT / destination)


def main() -> None:
    args = arguments()
    component_files = sorted(
        path
        for directory in (KIT_SOURCE / "templates", KIT_SOURCE / "templates" / "source-guides")
        for path in directory.glob("*")
        if path.is_file() and path.name != ".DS_Store"
    )
    authoring_files = [AUTHORING_SOURCE / name for name in AUTHORING_FILES]
    doc_files = [DOCS_SOURCE / name for name in DOC_FILES]
    kit_files = [KIT_SOURCE / name for name in KIT_FILES]
    pack_readme = KIT_SOURCE / "CREATOR_PACK_README.md"
    require_files(
        [
            args.psd,
            pack_readme,
            CREATOR_LICENSE,
            *authoring_files,
            *doc_files,
            *kit_files,
            *component_files,
        ]
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.output, "w", ZIP_DEFLATED) as archive:
        readme = pack_readme.read_text(encoding="utf-8").replace(
            "](../docs/", "](docs/"
        )
        archive.writestr(str(ARCHIVE_ROOT / "README.md"), readme)
        add(archive, CREATOR_LICENSE, "CREATOR_ASSETS_LICENSE.md")
        add(archive, args.psd, "AdventurePad-Skin-Template.psd")
        for path in doc_files:
            add(archive, path, Path("docs") / path.relative_to(DOCS_SOURCE))
        for path in authoring_files:
            add(archive, path, Path("skin-authoring") / path.name)
        for path in kit_files:
            add(archive, path, Path("skin-creator-kit") / path.name)
        for path in component_files:
            add(archive, path, Path("skin-creator-kit") / path.relative_to(KIT_SOURCE))
    print(args.output)


if __name__ == "__main__":
    main()
