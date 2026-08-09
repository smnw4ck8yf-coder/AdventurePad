# AdventurePad creator layout contract

`LAYOUT_SPEC.json` is the machine-readable authority. The PNGs in `templates/` are creator guides generated from it and the measured source constants. They represent display pixels only—never the Thor shell, controls, bezel, hinge, or other hardware.

## Measurement model

The display reference canvases are exactly 1920×1080 for the upper display and 1240×1080 for the lower display. Compose values remain dp until Android applies the runtime density, and the lower layout is inside `WindowInsets.safeDrawing`. Density and inset values are not fixed in source, so a single universal pixel rectangle would be false precision.

The lower PNGs therefore include a visibly labeled reference scenario of 2 px/dp and zero safe-drawing inset. It is a useful normalization for editing, not a runtime guarantee. Exact formulas and dp values are printed in the guides and JSON.

## Top gameplay contract

`top.surround` fills the upper display, then `SkinSurroundView` clips out the actual viewport reported by ScummVM. It cannot draw over game pixels. When the ScummVM GUI/overlay is visible, the full display is protected. A 16:9 viewport occupies all 1920×1080 pixels, so no artwork pixel is guaranteed visible. At this reference size, centered 16:10 and 4:3 examples are 1728×1080 at x=96 and 1440×1080 at x=240.

Split View can expand the selected upper source crop to a different presentation aspect. The reported protected rectangle changes with it; creators must never encode a fixed decorative inner frame.

## Lower gameplay contract

The normal layout selects `bottom.trackpad.background` beneath a weighted `TouchSurface` and native utility row. The surface has 12 dp horizontal and 3 dp vertical outer padding. Its LMB and RMB regions are each 34% of surface width and have height `clamp(22% of surface height, 56 dp, 88 dp)`. The touch-target and artwork bounds are the same. Native Compose draws the Left/Right labels.

The utility row has 12 dp horizontal and 4 dp vertical padding. Companion and Settings have minimum bounds of 132×56 dp and select their state-aware artwork beneath native labels; the connection indicator remains intrinsic native text centered by weighted spacers.

Split View selects `bottom.split.background`, then puts a live mirrored crop above it and the same weighted trackpad/utility layout. Its height is:

`min(safeWidth / adjustedInterfaceAspect × 1.35, max(0, safeHeight − 176 dp))`

The split ratio ranges from 0.05 through 0.95, defaults to 0.75, and snaps to a source pixel. The live mirror is composited above the background using that runtime height, so no background asset can obscure it and no fixed transparent cutout is assumed.

## Companion, Notes, and Walkthrough

These pages remain centered native overlays occupying 94% of the safe-drawing width and height. Their background assets theme the lower display beneath/around the unchanged page: `companion.background`, `notes.background`, and `walkthrough.background`. Each missing screen asset independently retains native rendering.

- Companion uses `PageHeader`: 16 dp horizontal and 8 dp vertical padding, optional 48×48 dp-min back control, and a 48 dp-wide/56 dp-min close control. Home content scrolls with 16 dp padding and 8 dp card gaps.
- Notes uses the same header, then 16 dp content padding and an 8 dp gap around an auto-save message and weighted editor. The editor has at least eight lines and resizes for the keyboard via `adjustResize`; Done is the IME action.
- Walkthrough uses an 8 dp horizontal/4 dp vertical toolbar. Back and close are at least 48×48 dp. Reader, Search, Contents, and More are conditional intrinsic-width controls with 40 dp minimum height. Reader settings, search, contents, menus, and scrolling text make the remaining area dynamic.

Decorative styling must preserve native text, outlines, focus/press feedback, reader palettes, and search-highlight contrast.

## Button and state contract

LMB and RMB declare 528×224 source canvases and use nine-slice rendering with 48 px suggested insets. Companion and Settings use 264×112 source canvases with 24 px suggested insets. Each has `.normal` and `.pressed` slots. Existing pointer/Compose press state selects only the image; behavior, minimum hit targets, native labels, ripple/overlay, and accessibility are unchanged. Missing pressed art falls back to matching normal art.

Companion and Settings remain native Compose controls with minimum 132×56 dp touch targets. Hover, focused, and disabled artwork is not required in v1; native semantics/focus/disabled behavior remains independent.

All artwork must be label-free. Compose remains authoritative for visible text, localization, semantics, and accessibility.

## `panel.frame`

`panel.frame` must be nine-slice. It is drawn around Companion-family pages as eight border patches; the center patch is deliberately omitted so it cannot cover native content. The frame is visual and non-interactive. It is no longer used over live Split View content.

No existing screen structure or input behavior is changed by this contract.
