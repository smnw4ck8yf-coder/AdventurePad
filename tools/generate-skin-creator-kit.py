#!/usr/bin/env python3
"""Build the text-and-guide creator kit; intentionally creates no skin artwork."""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "skin-creator-kit"
OUTPUT = ROOT / "releases" / "AdventurePad-Skin-Creator-Kit-v1.zip"


def main() -> None:
    files = sorted(path for path in SOURCE.rglob("*") if path.is_file())
    if not files:
        raise SystemExit("creator kit source is empty")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(OUTPUT, "w", ZIP_DEFLATED) as archive:
        for path in files:
            archive.write(path, Path("AdventurePad-Skin-Creator-Kit-v1") / path.relative_to(SOURCE))
    print(OUTPUT)


if __name__ == "__main__":
    main()
