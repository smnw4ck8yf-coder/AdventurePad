# AdventurePad canonical skin authoring template v2

This directory documents the artist-facing registration for AdventurePad's single-PNG Skin Builder. It contains two distinct authoring assets:

- `AdventurePad-Skin-Template-v2-reference.svg` and `AdventurePad-Skin-Template-v2-reference.png` are the labelled reference/learning template. They explain the regions and authoring constraints; never submit the guide/reference file as a skin.
- `AdventurePad-Skin-Template-v2.svg` and `AdventurePad-Skin-Template-v2.png` are the canonical production canvas. Visible boundaries and labels sit entirely in non-runtime gutters, leaving every registered region ready to paint.

Start finished artwork from the Creator Canvas, paint every required region, and export one **4720×4040 sRGB PNG**. The creator submits only that final PNG; AdventurePad validates its dimensions, slices every registered region, generates the internal assets and metadata, and installs the selectable skin.

Creators do not build `.apskin` archives, manifests, folders, or individual assets. Those are internal AdventurePad implementation details. Guide graphics and labels are reference-only and must be hidden or painted over in the submitted master PNG.

## Layer contract

- `GUIDE_BACKGROUNDS`: non-exporting artboard/reference backdrops below the artwork, so edits remain visible while guides are enabled.
- `ARTWORK`: the only artist-editable/exportable layer. Each child group ID is the exact runtime slot ID. Put artwork inside that group and keep native labels out.
- `GUIDES`: non-exporting visual geometry: live/protected content, artwork-safe and dynamic areas, safe-drawing guidance, native controls, and nine-slice boundaries.
- `LABELS`: non-exporting dimensions, region names, native-label zones, and explanatory notes.
- `REGISTRATION`: hidden exact-bound rectangles with `data-*` attributes. `AUTHORING_TEMPLATE_SPEC.json` is the complete slicing-registration contract.

Hide or ignore `GUIDE_BACKGROUNDS`, `GUIDES`, `LABELS`, and `REGISTRATION` when extracting art. Automated tooling must read the `ARTWORK` group identified by `svgArtworkGroupId`, clipped to the registered region rectangle. Guide graphics and text must never enter runtime assets.

## Canvas and region layout

The master canvas is **4720×4040 px**. Coordinates use a top-left origin and exclusive right/bottom edges. Regions have intentional gutters and do not overlap.

| Slot ID | Origin x,y | Output | Scale | Alpha behavior | Nine-slice L/T/R/B |
|---|---:|---:|---|---|---:|
| `top.surround` | 80,180 | 1920×1080 | `fill` | `rgba-runtime-clipped` | — |
| `bottom.trackpad.background` | 2080,180 | 1240×1080 | `cover` | `rgba` | — |
| `bottom.split.background` | 3400,180 | 1240×1080 | `cover` | `rgba` | — |
| `companion.background` | 80,1420 | 1240×1080 | `cover` | `rgba` | — |
| `notes.background` | 1400,1420 | 1240×1080 | `cover` | `rgba` | — |
| `walkthrough.background` | 2720,1420 | 1240×1080 | `cover` | `rgba` | — |
| `trackpad.surface` | 80,2660 | 1240×720 | `nineSlice` | `rgba` | 96/96/96/96 |
| `panel.frame` | 1400,2660 | 1240×360 | `nineSlice` | `rgba-transparent-center` | 64/64/64/64 |
| `preview` | 2720,2660 | 1200×675 | `contain` | `opaque` | — |
| `button.lmb.normal` | 80,3560 | 528×224 | `nineSlice` | `rgba` | 48/48/48/48 |
| `button.lmb.pressed` | 688,3560 | 528×224 | `nineSlice` | `rgba` | 48/48/48/48 |
| `button.rmb.normal` | 1296,3560 | 528×224 | `nineSlice` | `rgba` | 48/48/48/48 |
| `button.rmb.pressed` | 1904,3560 | 528×224 | `nineSlice` | `rgba` | 48/48/48/48 |
| `button.companion.normal` | 2512,3560 | 264×112 | `nineSlice` | `rgba` | 24/24/24/24 |
| `button.companion.pressed` | 2856,3560 | 264×112 | `nineSlice` | `rgba` | 24/24/24/24 |
| `button.settings.normal` | 3200,3560 | 264×112 | `nineSlice` | `rgba` | 24/24/24/24 |
| `button.settings.pressed` | 3544,3560 | 264×112 | `nineSlice` | `rgba` | 24/24/24/24 |
| `button.notes.normal` | 80,3848 | 572×192 | `nineSlice` | `rgba` | 32/32/32/32 |
| `button.notes.pressed` | 732,3848 | 572×192 | `nineSlice` | `rgba` | 32/32/32/32 |
| `button.walkthrough.normal` | 1384,3848 | 572×192 | `nineSlice` | `rgba` | 32/32/32/32 |
| `button.walkthrough.pressed` | 2036,3848 | 572×192 | `nineSlice` | `rgba` | 32/32/32/32 |

## Authoring rules

### Upper display

`top.surround` is only the 1920×1080 upper display, never the physical device, bezel, controls, or hinge. Paint the complete artboard. Do not cut a transparent game-window hole. Runtime clips the skin out of the actual ScummVM viewport, so game pixels always win. The 4:3 and 16:10 rectangles are examples in `GUIDES`; a full-screen 16:9 game can hide the skin completely.

### Lower displays and pages

Every 1240×1080 lower artboard represents screen pixels only. The zero-inset, 2 px/dp rectangles are reference geometry, not a density or inset guarantee. Native content, labels, touch targets, scrolling, keyboard resizing, and `WindowInsets.safeDrawing` remain runtime-owned.

Split View has no fixed transparent cutout. Export all of `bottom.split.background`; runtime geometry sizes and layers the mirror above it. In Standard style Companion, Notes, and Walkthrough retain centered 94% opaque native pages. With exact artwork in Immersive style they become full lower-display game surfaces. Companion uses a fixed 72 dp header, a 48 dp top offset, and a fixed 96 dp action row: Notes on the left and Walkthrough on the right, with 16 dp outer padding and a 16 dp gap. Both edges of the original-height buttons move down by 48 dp. In the 2 px/dp reference this produces exact 572×192 px hit regions at `[32,240,604,432]` and `[636,240,1208,432]`. Paint the Companion title and surrounding decoration into `companion.background`; paint the two action appearances and labels into their dedicated normal/pressed assets. AdventurePad owns the fixed hit targets and never asks artists for coordinates. Keep Notes and Walkthrough content areas pale, quiet, and readable beneath native dark text, and preserve their marked header areas.

### Buttons and panel frame

Normal and pressed button artwork are separate groups. Standard style draws native labels. Immersive style hides native labels and chrome, so immersive-oriented artwork should visually identify its fixed hit regions. Notes and Walkthrough use their exact 572×192 px reference hit-area geometry. Suggested nine-slice borders are registration metadata and visible guides; avoid essential detail in stretchable centers.

`panel.frame` requires an RGBA image whose inner rectangle `(64,64)` through `(1176,296)` is fully transparent. Runtime draws only the eight border patches and intentionally omits the center patch.

### Preview

`preview` is the only required package image. It is a 1200×675 opaque catalog/confirmation image and is never rendered as application UI.

## Scope relative to the runtime skin contract

The authoritative sources agree on the dimensions and behavior of all 21 included regions. `trackpad.surface` remains a separate runtime asset and is therefore included.

The runtime format also recognizes optional `launcher.background`, `launcher.header`, and `launcher.brand` slots. They are intentionally absent from this focused gameplay/lower-screen template. Legacy compatibility slots (`bottom.background`, `trackpad.button.left`, `trackpad.button.right`) are likewise not authoritative creator slots.

## Skin Builder consumption

`AUTHORING_TEMPLATE_SPEC.json` supplies slot ID, exact master origin, source/output size, alpha mode, scaling, and nine-slice metadata. The current builder reads this registration, validates the submitted master PNG, clips each registered rectangle, enforces `preview` opacity and the `panel.frame` transparent center, then produces and installs the internal package. The checked-in Adventure Journal proof follows this exact path.
