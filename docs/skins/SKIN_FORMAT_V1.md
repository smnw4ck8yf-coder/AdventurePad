# AdventurePad Skin Package Format v1 — internal/advanced

> This is an internal package-format reference for AdventurePad maintainers. Community artists should use the [single-PNG creator workflow](../../skin-creator-kit/README.md): export one 4720×4040 PNG and let AdventurePad build and install the package. The current user interface does not offer direct `.apskin` selection.

Internally, an `.apskin` file is a ZIP archive containing declarative artwork and a `skin.json` manifest. It cannot contain application behavior. AdventurePad keeps gestures, layout, accessibility, labels, state, input, and game rendering native.

## Context selection

Skin selection is deliberately not global:

- The AdventurePad launcher resolves `builtin.adventure` in v1.
- Gameplay resolves the skin assigned to the exact ScummVM target ID.
- An unassigned or unavailable gameplay skin resolves `builtin.default`; it never inherits the launcher skin.
- Advanced ScummVM resolves its existing/default presentation in v1 and does not inherit the launcher skin.

Assignments store stable skin IDs, not installation paths. Removing a community skin clears assignments to it. Upper-surround lookup is read-only and signature-protected between the AdventurePad and ScummVM apps.

## Archive structure

```text
skin.json                 required
preview.png               required; catalog/confirmation only
assets/                   optional PNG/WebP rendering artwork
  top/
  bottom/
  trackpad/
  panels/
  buttons/
  icons/
```

Paths are case-sensitive POSIX relative paths. Absolute paths, `..`, empty components, backslashes, duplicates, case collisions, nested archives, executables, scripts, encrypted entries, fonts, and files outside the package root are rejected. Apart from root `skin.json` and `preview.png`, accepted package files must be PNG/WebP images under `assets/`.

Limits are 64 MiB packaged, 128 MiB expanded, 256 entries, 32 MiB per entry, and 8192×8192 per decoded image. Every manifest-referenced asset has a SHA-256 digest. Import extracts to private staging, validates the complete package, and promotes it atomically.

## Manifest

```json
{
  "formatVersion": 1,
  "id": "org.example.my-skin",
  "name": "My Adventure Skin",
  "author": "Artist Name",
  "packageVersion": "1.0.0",
  "minimumAdventurePadVersionCode": 1,
  "features": ["lower-controls", "upper-surround"],
  "assets": {
    "bottom.trackpad.background": {
      "path": "assets/bottom/trackpad-background.png",
      "scale": "cover",
      "canvas": {"width": 1240, "height": 1080},
      "sha256": "64-lowercase-hex-characters"
    },
    "trackpad.surface": {
      "path": "assets/trackpad/surface.png",
      "scale": "nineSlice",
      "sliceInsets": {"left": 96, "top": 96, "right": 96, "bottom": 96},
      "sha256": "64-lowercase-hex-characters"
    }
  },
  "colors": {
    "background": "#111417",
    "surface": "#1A1F24",
    "surfaceRaised": "#22282E",
    "surfacePressed": "#303841",
    "outline": "#46515B",
    "outlineStrong": "#64717D",
    "primary": "#D8B86A",
    "onPrimary": "#211B0D",
    "textPrimary": "#F2F3F5",
    "textSecondary": "#ADB5BD",
    "trackpadBackground": "#1A1F24",
    "mirrorBackdrop": "#000000",
    "controlTint": "#22282E",
    "controlOutline": "#46515B"
  },
  "metrics": {"spacingScale": 1.0, "cornerScale": 1.0, "borderScale": 1.0},
  "extensions": {}
}
```

`formatVersion` is a major format version. Unsupported versions are rejected. Unknown optional features, slots, color tokens, metric tokens, and extension data are retained/ignored safely. Missing supported slots and colors fall back independently to `builtin.default`.

Colors are `#RRGGBB` or `#AARRGGBB`. Metrics are bounded to `0.5..2.0` and cannot change minimum touch targets. `canvas` is descriptive authoring metadata; runtime sizing remains responsive.

Scaling modes are `cover`, `contain`, `fill`, `nineSlice`, and `none`. `nineSlice` requires non-negative source-pixel `sliceInsets` that leave a non-empty center.

## v1 asset slots

| Slot | Required | Reference canvas | Alpha | Runtime contract |
|---|---|---:|---|---|
| `preview` | Yes | 1200×675 | No | Contain; never rendered as UI |
| `launcher.background` | No | 1920×1080 | Yes | Recognized internal slot; external gameplay skins cannot currently select it for the fixed launcher |
| `launcher.header` | No | 1920×256 | Yes | Recognized internal slot; no current external-skin launcher consumer |
| `launcher.brand` | No | 1024×256 | Yes | Recognized internal slot; no current external-skin launcher consumer |
| `top.surround` | No | 1920×1080 | Yes | Normal gameplay composites the complete RGBA bitmap above the game; Split View does not show it as the normal decorative surround |
| `bottom.trackpad.background` | No | 1240×1080 | Yes | Cover beneath normal lower gameplay UI |
| `bottom.split.background` | No | 1240×1080 | Yes | Cover beneath Split View; runtime mirror always draws above it |
| `companion.background` | No | 1240×1080 | Yes | Cover full lower display beneath current Companion page |
| `notes.background` | No | 1240×1080 | Yes | Notes-specific cover; missing asset retains native rendering |
| `walkthrough.background` | No | 1240×1080 | Yes | Walkthrough-specific cover; missing asset retains native rendering |
| `trackpad.surface` | No | 1240×720 | Yes | Nine-slice; 96 px suggested edges |
| `button.lmb.normal`, `button.lmb.pressed` | No | 528×224 | Yes | Nine-slice; native LMB touch region and label remain |
| `button.rmb.normal`, `button.rmb.pressed` | No | 528×224 | Yes | Nine-slice; native RMB touch region and label remain |
| `button.companion.normal`, `button.companion.pressed` | No | 264×112 | Yes | Nine-slice; native 132×56 dp-min button owns input/label |
| `button.settings.normal`, `button.settings.pressed` | No | 264×112 | Yes | Nine-slice; native 132×56 dp-min button owns input/label |
| `button.notes.normal`, `button.notes.pressed` | No | 572×192 | Yes | Nine-slice; native Companion Notes action owns input/semantics |
| `button.walkthrough.normal`, `button.walkthrough.pressed` | No | 572×192 | Yes | Nine-slice; native Companion Walkthrough action owns input/semantics |
| `panel.frame` | No | 1240×360 | Yes | 64 px nine-slice frame around the live lower mirror in Immersive Split View; importer clears the center |

The reference canvases match the current AYN Thor displays, not fixed runtime control/touch dimensions. In Normal gameplay the complete `top.surround` bitmap is composited above the game using alpha. The public master-PNG importer therefore validates a transparent full-height centre at `x=320…1600`: alpha values through 32 are ignored and no more than 0.5% of centre pixels may exceed that threshold. Side decoration may overlap extreme game edges. Avoid meaningful top/bottom rails through the centre. Split View does not show the decorative bitmap as the normal upper surround.

Nine-slice authoring sizes are not physical touch sizes. Corners remain stable, edges stretch along one axis, and centres stretch/fill where applicable. Insets are 96 px for `trackpad.surface`, 64 px for `panel.frame`, 48 px for LMB/RMB, 24 px for Companion/Settings, and 32 px for Notes/Walkthrough. Runtime layout remains authoritative for visual and touch bounds.

Pressed artwork changes visuals only. Missing pressed art falls back to the matching normal image; missing normal art falls back to legacy v1 artwork where applicable, then native rendering. Legacy `bottom.background`, `trackpad.button.left`, and `trackpad.button.right` packages remain renderable but are not authoritative creator slots.

Hover, focused, and disabled artwork states are reserved for a future format-compatible extension and are not required in v1.

Fonts, custom cursors, animations, sounds, remote assets, and executable behavior are not implemented or accepted in v1 packages.
