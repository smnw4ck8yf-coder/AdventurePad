# AdventurePad Skin Creator Kit

The first-party material in this kit is reusable under the terms in [Creator-material licensing](../CREATOR_ASSETS_LICENSE.md).

AdventurePad's public skin workflow uses one master PNG. You do not create a manifest, hashes, asset folders, ZIP archive, package ID, or `.apskin` file.

For the complete beginner workflow, start with [Creating a Custom AdventurePad Skin](../docs/CUSTOM_SKINS.md), then use the [technical reference](../docs/SKIN_REFERENCE.md) when exact geometry is needed. A downloadable Creator Pack may also include the optional editable PSD and the completed example sheet; the PSD remains a convenience rather than a geometry authority or import format.

## Create and import a skin

1. Open `../skin-authoring/AdventurePad-Skin-Template-v2.png` in a raster editor, or use the SVG version in an editor that preserves the exact canvas and region positions.
2. Use `../skin-authoring/AdventurePad-Skin-Template-v2-reference.png` and the component guides in `templates/` to understand each region.
3. Create artwork inside all 21 marked regions. Follow `ASSET_REQUIREMENTS.md`, especially the upper-display transparency, preview opacity, and nine-slice rules.
4. Hide or remove guide and label artwork before export. Do not submit the annotated reference template.
5. Export one PNG at exactly **4720×4040 px**. sRGB is recommended for predictable colour, but the importer does not enforce a colour profile. DPI is not operationally significant.
6. Transfer the PNG to the Android device.
7. In AdventurePad, open the skin import flow and select the PNG.
8. AdventurePad validates the sheet, crops the 21 assets, generates its internal metadata/package, and installs the skin automatically.

The master sheet includes upper gameplay decoration, Trackpad and Split backgrounds, Companion/Notes/Walkthrough surfaces, the trackpad and Split panel frames, a catalog preview, and normal/pressed artwork for all six controls.

## Files in this kit

- `ASSET_REQUIREMENTS.md`: practical artwork, transparency, and nine-slice rules.
- `LAYOUT_SPEC.md`: responsive runtime layout context for artists.
- `LAYOUT_SPEC.json`: machine-readable version of that derived layout reference.
- `templates/`: generated component PNG guides.
- `templates/source-guides/`: generated SVG sources for those guides.

Exact master-sheet crop geometry lives only in `../skin-authoring/AUTHORING_TEMPLATE_SPEC.json`. The files in this directory explain or visualize that contract; they do not replace it.

Nothing in this kit skins the physical Thor shell, controls, bezels, hinge, or hardware outside the display pixels. External gameplay skins also do not reskin the AdventurePad launcher.

The internal `.apskin` archive is documented separately for maintainers in `../docs/skins/SKIN_FORMAT_V1.md`. It is not the community authoring workflow and is not directly selectable in the current user interface.

In a source checkout, maintainers can regenerate component guides with `python3 tools/generate-skin-templates.py`, regenerate the canonical template/reference outputs with `python3 tools/generate-skin-authoring-template.py`, and build the distributable pack with `python3 tools/generate-skin-creator-kit.py --psd PATH_TO_EDITABLE_PSD`.
