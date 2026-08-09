# AdventurePad canonical skin authoring template v1

This directory is the Milestone 7.2 artist-facing source. Edit one document: `AdventurePad-Skin-Template-v1.svg`. It contains one exact-size `ARTWORK` child group for every surface/state in the requested authoring contract. The PNG is a flattened inspection reference, not an editable or sliceable source.

This is not a Skin Builder. Nothing here imports a design, slices PNGs, hashes assets, edits `skin.json`, or builds an `.apskin`.

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

## Authoring rules

### Upper display

`top.surround` is only the 1920×1080 upper display, never the physical device, bezel, controls, or hinge. Paint the complete artboard. Do not cut a transparent game-window hole. Runtime clips the skin out of the actual ScummVM viewport, so game pixels always win. The 4:3 and 16:10 rectangles are examples in `GUIDES`; a full-screen 16:9 game can hide the skin completely.

### Lower displays and pages

Every 1240×1080 lower artboard represents screen pixels only. The zero-inset, 2 px/dp rectangles are reference geometry, not a density or inset guarantee. Native content, labels, touch targets, scrolling, keyboard resizing, and `WindowInsets.safeDrawing` remain runtime-owned.

Split View has no fixed transparent cutout. Export all of `bottom.split.background`; runtime geometry sizes and layers the mirror above it. Companion, Notes, and Walkthrough retain their existing centered 94% native page layouts. Their page bounds, content zones, scrolling areas, and optional frame placement are guides only.

### Buttons and panel frame

Normal and pressed button artwork are separate groups. Keep artwork label-free: AdventurePad draws Left/Right, COMPANION, and SETTINGS natively. Suggested nine-slice borders are registration metadata and visible guides; avoid essential detail in stretchable centers.

`panel.frame` requires an RGBA image whose inner rectangle `(64,64)` through `(1176,296)` is fully transparent. Runtime draws only the eight border patches and intentionally omits the center patch.

### Preview

`preview` is the only required v1 package image. It is a 1200×675 opaque catalog/confirmation image and is never rendered as application UI.

## Scope relative to runtime v1

The authoritative sources agree on the dimensions and behavior of all 17 included regions. `trackpad.surface` remains a separate runtime asset and is therefore included.

The runtime format also recognizes optional `launcher.background`, `launcher.header`, and `launcher.brand` slots. They are intentionally absent because Milestone 7.2 explicitly enumerates gameplay, lower-screen, control, panel, and preview regions and says not to redesign AdventurePad. Legacy compatibility slots (`bottom.background`, `trackpad.button.left`, `trackpad.button.right`) are likewise not authoritative creator slots.

## Future Skin Builder consumption

`AUTHORING_TEMPLATE_SPEC.json` supplies slot ID, exact master origin, source/output size, alpha mode, scaling, and nine-slice metadata without a hard-coded coordinate mapping. A future builder should verify the SVG group IDs, ignore non-artwork layers, clip each group to its registered rectangle, enforce `preview` opacity and the `panel.frame` transparent center, and then perform packaging work. Those operations are deliberately not implemented here.
