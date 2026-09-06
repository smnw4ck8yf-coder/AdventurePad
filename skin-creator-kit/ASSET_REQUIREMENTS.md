# AdventurePad skin artwork requirements

These rules apply to the single **4720×4040 PNG** imported by AdventurePad. Exact crop coordinates are defined by `../skin-authoring/AUTHORING_TEMPLATE_SPEC.json`; use the production and annotated reference templates in that directory.

## General rules

- Export PNG at exactly 4720×4040 px. DPI is ignored. sRGB is recommended but not importer-enforced.
- The sheet contains 21 authored regions. Keep every region at its registered position and size.
- Most artwork supports RGBA transparency. Transparent crops can technically be generated, but a skin intended for Immersive use should provide visible, usable LMB, RMB, Companion, and Settings controls.
- `preview` must be fully opaque. It is 1200×675 and appears only in catalog/confirmation UI.
- Standard Interface Style supplies native labels and chrome. Immersive uses the authored control appearance while AdventurePad retains input, semantics, accessibility, and layout.
- External gameplay skins do not reskin the launcher.

## Upper gameplay surround

`top.surround` is a 1920×1080 RGBA image composited above the game in Normal gameplay.

- Prefer full-height decoration in the left `x=0…240` and right `x=1680…1920` panels.
- The `x=240…320` and `x=1600…1680` bands allow alpha edges, shadows, vines, and similar edge overlap.
- Keep the full-height centre `x=320…1600` effectively transparent.
- Alpha values at or below 32 are ignored by validation. At most 0.5% of safe-centre pixels may exceed alpha 32.
- Artwork may overlap the extreme left/right game edges in Normal mode.
- Avoid meaningful horizontal top/bottom rails through the centre because they conflict with or are obscured by the game viewport.
- Runtime does not always clip the authored PNG away from the live viewport.
- Split View does not show this decorative surround as the normal upper surround.

## Lower surfaces and pages

- Trackpad, Split, Companion, Notes, and Walkthrough backgrounds are 1240×1080 RGBA assets.
- `bottom.split.background` remains below the runtime-sized live mirror. Do not paint a fixed transparent hole for mirrored content.
- Companion, Notes, and Walkthrough use responsive native content. Preserve quiet, readable areas beneath native text and controls.
- At the 2 px/dp reference size, Companion's Notes and Walkthrough hit regions are `[32,240,604,432]` and `[636,240,1208,432]`. AdventurePad owns those interaction coordinates.

## Nine-slice artwork

Nine-slice source dimensions are authoring sizes, not fixed physical control or touch sizes. Corners remain visually stable, edges stretch along one axis, and centre regions stretch/fill where applicable. Runtime layout determines final artwork and touch bounds.

Keep icons, labels, borders, and other important details away from stretch-sensitive centres and edges.

| Asset family | Source size | Insets L/T/R/B |
|---|---:|---:|
| `trackpad.surface` | 1240×720 | 96/96/96/96 px |
| `panel.frame` | 1240×360 | 64/64/64/64 px |
| LMB/RMB normal and pressed | 528×224 | 48/48/48/48 px |
| Companion/Settings normal and pressed | 264×112 | 24/24/24/24 px |
| Notes/Walkthrough normal and pressed | 572×192 | 32/32/32/32 px |

`panel.frame` belongs to Immersive Split View. It frames the live lower mirrored panel and changes the usable inner opening. During import, AdventurePad clears its centre rectangle—`x=64`, `y=64`, `width=1112`, `height=232`—so artwork cannot cover the live mirror. It is not a Companion overlay.

Normal and pressed artwork are separate. Pressed artwork changes appearance only; AdventurePad continues to own the action and touch target.
