# AdventurePad Skin Package Format v1

An `.apskin` file is a ZIP archive containing declarative artwork and a `skin.json` manifest. It cannot contain application behavior. AdventurePad keeps gestures, layout, accessibility, labels, state, input, and game rendering native.

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
assets/                   optional rendering artwork
  launcher/
  top/
  bottom/
  trackpad/
  panels/
  buttons/
  icons/
fonts/                    reserved for a future compatible version
```

Paths are case-sensitive POSIX relative paths. Absolute paths, `..`, empty components, backslashes, duplicates, case collisions, nested archives, executables, scripts, encrypted entries, and files outside the package root are rejected.

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
| `launcher.background` | No | 1920×1080 | Yes | Cover; launcher context only |
| `launcher.header` | No | 1920×256 | Yes | Decorative; native header remains |
| `launcher.brand` | No | 1024×256 | Yes | Contain |
| `top.surround` | No | 1920×1080 | Yes | Fill display, then clip out the native live-game rectangle |
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
| `panel.frame` | No | 1240×360 | Yes | Nine-slice border around Companion-family pages; center patch is never drawn |

The reference canvases match the current AYN Thor displays, not fixed runtime dimensions. Artwork must tolerate cropping and different aspect ratios. There is no guaranteed visible safe area in `top.surround`: a 16:9 game can hide it completely. AdventurePad/ScummVM clips the artwork outside the native viewport, and missing provider data leaves the existing black surround.

Pressed artwork changes visuals only. Missing pressed art falls back to the matching normal image; missing normal art falls back to legacy v1 artwork where applicable, then native rendering. Legacy `bottom.background`, `trackpad.button.left`, and `trackpad.button.right` packages remain renderable but are not authoritative creator slots.

Hover, focused, and disabled artwork states are reserved for a future format-compatible extension and are not required in v1.

Fonts, custom cursors, animations, sounds, remote assets, and executable behavior are not implemented in v1.
