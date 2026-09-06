# AdventurePad Skin Creator Pack

The first-party template material, Creator Pack documentation/art assets, and AdventurePad Pirate example artwork in this pack are reusable under [CC BY 4.0](../CREATOR_ASSETS_LICENSE.md). See that file for attribution and exclusions.

Start with [Creating a Custom AdventurePad Skin](../docs/CUSTOM_SKINS.md). It takes you from the editable or image template to the one PNG that AdventurePad imports. Exact geometry and technical behavior are in the [Skin Authoring Reference](../docs/SKIN_REFERENCE.md).

## Choose a template

- `AdventurePad-Skin-Template.psd` is the optional editable PSD-compatible template. It contains a guide/reference layer and a completed AdventurePad Pirate example layer. It is not the import format.
- `skin-authoring/AdventurePad-Skin-Template-v2.png` is the canonical generated production image template.
- `skin-authoring/AdventurePad-Skin-Template-v2.svg` is its editable vector counterpart.
- `skin-authoring/AdventurePad-Skin-Template-v2-reference.png` and `.svg` explain the regions visually.
- `skin-authoring/AdventurePad-Skin-Example.png` is the completed, web-viewable Pirate example artwork sheet.

The final file selected in AdventurePad must be **one PNG at exactly 4720×4040 pixels**. Do not select the PSD and do not create 21 separate files.

## What is authoritative

`skin-authoring/AUTHORING_TEMPLATE_SPEC.json` is the sole machine-readable crop and geometry authority. The PSD, images, Markdown, and component guides are conveniences or derived explanations of that specification.

The optional files under `skin-creator-kit/` provide artwork requirements, responsive-layout context, and individual component guides. Normal creators do not need to create manifests, hashes, ZIP files, `.apskin` packages, asset directories, or skin IDs.
