# AdventurePad creator layout contract

`LAYOUT_SPEC.json` is a machine-readable responsive-layout reference. Exact master-sheet crop geometry is authoritative only in `../skin-authoring/AUTHORING_TEMPLATE_SPEC.json`. The PNGs in `templates/` are generated creator guides. They represent display pixels only—never the Thor shell, controls, bezel, hinge, or other hardware.

## Measurement model

The display reference canvases are exactly 1920×1080 for the upper display and 1240×1080 for the lower display. Compose values remain dp until Android applies the runtime density, and the lower layout is inside `WindowInsets.safeDrawing`. Density and inset values are not fixed in source, so a single universal pixel rectangle would be false precision.

The lower PNGs therefore include a visibly labeled reference scenario of 2 px/dp and zero safe-drawing inset. It is a useful normalization for editing, not a runtime guarantee. Exact formulas and dp values are printed in the guides and JSON.

## Top gameplay contract

`top.surround` is authored as two optional full-height decorative side panels, not a conventional four-sided frame. The primary side zones are x=0…240 and x=1680…1920, but artwork may be narrower. The adjoining 80 px bands at x=240…320 and x=1600…1680 permit alpha edges, shadows, vines, torn paper, and similar edge overlap. The full-height centre x=320…1600 is gameplay-safe and must remain transparent; do not build substantial top or bottom rails across it.

The single-master-PNG importer actively validates this area. Alpha values through 32 are ignored and up to 0.5% of the central pixels may exceed that threshold. This deliberately tolerates antialiasing and sparse organic detail while rejecting clear opaque intrusion.

In Normal gameplay, `top.surround` fills the upper display and the complete RGBA bitmap is composited above the game. Runtime does not always clip the artwork away from the live viewport, so decoration may overlap the extreme left/right game edges. The authoring-safe centre is what keeps normal gameplay visually unobstructed.

Split View does not show this decorative artwork as the normal upper surround. Creators should never encode a fixed decorative inner frame.

## Lower gameplay contract

The normal layout selects `bottom.trackpad.background` beneath a weighted `TouchSurface` and utility row. The surface has 12 dp horizontal and 3 dp vertical outer padding. Its LMB and RMB regions are each 34% of surface width and have height `clamp(22% of surface height, 56 dp, 88 dp)`. The touch-target and artwork bounds are the same. Standard style draws native Left/Right labels, borders, feedback overlays, and the lower-screen position marker. Immersive hides those native visuals while preserving the same hit targets and state-aware artwork.

The utility row has 12 dp horizontal and 4 dp vertical padding. Companion and Settings have minimum bounds of 132×56 dp and select their state-aware artwork. Standard draws their native labels and chrome; Immersive leaves the same controls invisible above the supplied art. The Trackpad screen has no connection-status indicator.

Split View selects `bottom.split.background`, then puts a live mirrored crop above it and the same weighted trackpad/utility layout. Its height is:

`min(safeWidth / adjustedInterfaceAspect × 1.35, max(0, safeHeight − 176 dp))`

The split ratio ranges from 0.05 through 0.95, defaults to 0.75, and snaps to a source pixel. The live mirror is composited above the background using that runtime height, so no background asset can obscure it and no fixed transparent cutout is assumed.

## Companion, Notes, and Walkthrough

Standard Interface Style places an opaque native page over these backgrounds. With exact artwork installed, Immersive makes Companion, Notes, and Walkthrough full lower-display surfaces. Native input, scrolling, accessibility, and keyboard behavior remain runtime-owned. Creators should keep the marked Notes editor and Walkthrough reading areas pale and low-contrast for dark native text.

In Standard they are centered native overlays occupying 94% of safe-drawing width and height. In Immersive each exact asset fills the surface; a missing screen asset independently falls back to opaque native rendering.

- Companion has a fixed 72 dp header, followed by a 48 dp vertical offset and a fixed 96 dp action row. The complete original-height buttons are shifted down by 48 dp; neither edge remains at its former position. The row has 16 dp horizontal padding and a 16 dp gap; each action is 286×96 dp in the 620×540 dp reference layout. At 2 px/dp, Notes is exactly `[32,240,604,432]` and Walkthrough is exactly `[636,240,1208,432]`. Immersive draws no native title, action labels, backgrounds, outlines, or ripple. Artists paint the title and surrounding decoration into `companion.background`, then paint action visuals into the dedicated 572×192 normal/pressed Notes and Walkthrough assets. The larger area below remains open for future fixed actions. Artists never define coordinates. Older skins without the four action assets retain their existing background/native fallback; older dedicated assets remain loadable and are scaled into the current runtime bounds.
- Notes uses the same header, then 16 dp content padding and an 8 dp gap around an auto-save message and weighted editor. The editor has at least eight lines and resizes for the keyboard via `adjustResize`; Done is the IME action.
- Walkthrough uses an 8 dp horizontal/4 dp vertical toolbar. Back and close are at least 48×48 dp. Reader, Search, Contents, and More are conditional intrinsic-width controls with 40 dp minimum height. Reader settings, search, contents, menus, and scrolling text make the remaining area dynamic.

Decorative styling must preserve Notes/Walkthrough native text, editor behavior, reader controls, focus semantics, reader palettes, and search-highlight contrast.

## Button, state, and nine-slice contract

LMB and RMB declare 528×224 source canvases and use nine-slice rendering with 48 px suggested insets. Companion and Settings use 264×112 source canvases with 24 px suggested insets. Each has `.normal` and `.pressed` slots. Existing pointer/Compose press state selects only the image; behavior, minimum hit targets, native labels, ripple/overlay, and accessibility are unchanged. Missing pressed art falls back to matching normal art.

Companion and Settings remain native Compose controls with minimum 132×56 dp touch targets. Hover, focused, and disabled artwork is not required in v1; native semantics/focus/disabled behavior remains independent.

Standard style remains authoritative for visible native text. Immersive-oriented artwork should include the visual labels identified by the guides because native labels and chrome are hidden there. Compose remains authoritative for semantics, accessibility, localization metadata, and interaction.

Source PNG dimensions are authoring dimensions, not fixed runtime device-space or touch sizes. Nine-slice rendering keeps corners stable, stretches edges along one axis, and stretches/fills the centre where applicable. Final visual and touch bounds come from the responsive runtime layout. Keep important icons, text, and detail outside stretch-sensitive areas.

- `trackpad.surface`: 96 px insets.
- `panel.frame`: 64 px insets.
- LMB/RMB: 48 px insets.
- Companion/Settings: 24 px insets.
- Notes/Walkthrough: 32 px insets.

## `panel.frame`

`panel.frame` is a 1240×360 nine-slice frame used around the live lower mirrored panel in Immersive Split View. Its 64 px insets define an inner opening. AdventurePad clears the centre during import, and the runtime fits live mirrored content inside that opening so artwork cannot cover it. The frame is visual and non-interactive; it is not a Companion overlay.

No existing screen structure or input behavior is changed by this contract.
