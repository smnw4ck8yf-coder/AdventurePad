# Skin Authoring Reference

This is the concise technical reference for AdventurePad's one-PNG creator workflow. The machine-readable source of truth is [`skin-authoring/AUTHORING_TEMPLATE_SPEC.json`](../skin-authoring/AUTHORING_TEMPLATE_SPEC.json). If this page and that file ever differ, the JSON is authoritative.

The master canvas is exactly **4720×4040 px**. Coordinates use a top-left origin; right and bottom edges are exclusive. Each registered crop is exported as a PNG at the listed dimensions.

## Regions

| # | Slot | Origin (x, y) | Output | Scale | Alpha rule |
|---:|---|---:|---:|---|---|
| 1 | `top.surround` | 80, 180 | 1920×1080 | fill | RGBA; gameplay-safe centre |
| 2 | `bottom.trackpad.background` | 2080, 180 | 1240×1080 | cover | RGBA |
| 3 | `bottom.split.background` | 3400, 180 | 1240×1080 | cover | RGBA |
| 4 | `companion.background` | 80, 1420 | 1240×1080 | cover | RGBA |
| 5 | `notes.background` | 1400, 1420 | 1240×1080 | cover | RGBA |
| 6 | `walkthrough.background` | 2720, 1420 | 1240×1080 | cover | RGBA |
| 7 | `trackpad.surface` | 80, 2660 | 1240×720 | nine-slice | RGBA |
| 8 | `panel.frame` | 1400, 2660 | 1240×360 | nine-slice | RGBA; transparent centre |
| 9 | `preview` | 2720, 2660 | 1200×675 | contain | Fully opaque |
| 10 | `button.lmb.normal` | 80, 3560 | 528×224 | nine-slice | RGBA |
| 11 | `button.lmb.pressed` | 688, 3560 | 528×224 | nine-slice | RGBA |
| 12 | `button.rmb.normal` | 1296, 3560 | 528×224 | nine-slice | RGBA |
| 13 | `button.rmb.pressed` | 1904, 3560 | 528×224 | nine-slice | RGBA |
| 14 | `button.companion.normal` | 2512, 3560 | 264×112 | nine-slice | RGBA |
| 15 | `button.companion.pressed` | 2856, 3560 | 264×112 | nine-slice | RGBA |
| 16 | `button.settings.normal` | 3200, 3560 | 264×112 | nine-slice | RGBA |
| 17 | `button.settings.pressed` | 3544, 3560 | 264×112 | nine-slice | RGBA |
| 18 | `button.notes.normal` | 80, 3848 | 572×192 | nine-slice | RGBA |
| 19 | `button.notes.pressed` | 732, 3848 | 572×192 | nine-slice | RGBA |
| 20 | `button.walkthrough.normal` | 1384, 3848 | 572×192 | nine-slice | RGBA |
| 21 | `button.walkthrough.pressed` | 2036, 3848 | 572×192 | nine-slice | RGBA |

## Alpha and protected areas

Most regions allow RGBA transparency. Two regions have additional rules:

- **`top.surround`:** keep the full-height local rectangle `x=320…1600`, `y=0…1080` effectively transparent. Validation ignores alpha values at or below 32; no more than 0.5% of pixels in that safe centre may exceed alpha 32. Primary decoration belongs in `x=0…240` and `x=1680…1920`, with adjacent 80 px overlap bands for soft edges and organic detail.
- **`preview`:** every pixel must be opaque. This 1200×675 image is used in catalog/confirmation UI and is not rendered as application chrome.

The importer permits RGBA in other crops, but an Immersive skin still needs visible, usable control artwork.

## Background roles

The five 1240×1080 background crops are separate because they serve different runtime contexts:

- `bottom.trackpad.background` sits behind Trackpad Mode.
- `bottom.split.background` sits behind the responsive live lower mirror in Split View; it does not contain a fixed mirror hole.
- `companion.background` is the Companion landing surface.
- `notes.background` and `walkthrough.background` sit behind native reader content and should preserve readable areas for text.

## Nine-slice insets

Insets are source pixels in left/top/right/bottom order. Keep important detail away from stretch-sensitive areas.

| Asset family | Source size | Insets L/T/R/B |
|---|---:|---:|
| `trackpad.surface` | 1240×720 | 96/96/96/96 |
| `panel.frame` | 1240×360 | 64/64/64/64 |
| LMB/RMB normal and pressed | 528×224 | 48/48/48/48 |
| Companion/Settings normal and pressed | 264×112 | 24/24/24/24 |
| Notes/Walkthrough normal and pressed | 572×192 | 32/32/32/32 |

Nine-slice source sizes are authoring sizes, not final touch bounds. AdventurePad owns responsive layout, interaction, semantics, and accessibility.

Normal and pressed assets are paired states: normal is the default appearance and pressed is the touch-down/active appearance. Keep each pair visually related. Their source dimensions and insets describe rendering assets, not runtime hit targets.

## `panel.frame`

`panel.frame` frames the live lower mirrored panel in Immersive Split View. During import, AdventurePad clears its local centre rectangle at `x=64`, `y=64`, width `1112`, height `232`. At runtime, the mirrored content is fitted inside that opening. It is not a Companion overlay.

## Runtime notes

- Normal gameplay composites the complete `top.surround` PNG above the game using alpha; it is not always clipped away from the live viewport.
- Split View does not use `top.surround` as its normal decorative upper surround.
- `bottom.split.background` is exported in full; the runtime-sized mirror is composited above it. Do not create a fixed mirror hole.
- Standard style retains native labels and presentation. Immersive style relies more heavily on authored artwork while AdventurePad keeps control behavior and touch targets.
- Normal and pressed button artwork are separate assets.
- Companion, Notes, and Walkthrough content remains responsive and runtime-owned.
- External skins do not currently reskin the launcher.

See [Creating Custom Skins](CUSTOM_SKINS.md), the [artwork requirements](../skin-creator-kit/ASSET_REQUIREMENTS.md), and the [annotated template reference](../skin-authoring/AdventurePad-Skin-Template-v2-reference.png).
