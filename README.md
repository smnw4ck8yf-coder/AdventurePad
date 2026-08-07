# AdventurePad

AdventurePad is a companion application for ScummVM designed specifically for dual-screen Android handhelds such as the AYN Thor.

It transforms classic point-and-click adventures into a Nintendo DS-style experience by displaying the game on the upper screen while providing a dedicated touch trackpad and controls on the lower screen.

## Current Features

### Dual-display

- ✅ Live mirrored ScummVM rendering on the lower display
- Configurable full-frame editor with a smooth, vertical-only interface split line, fine adjustment,
  save, and cancel
- Versioned per-game normalized split persistence, including migration from legacy crop rectangles
- Persistent preferred Split View/Trackpad display mode
- Two-finger double-tap mode switching with single two-finger right-click preservation
- L2 + R2 trigger-axis chord mode switching with ordinary trigger forwarding preserved
- Exact complementary upper-game/lower-interface regions, with transformed cursor presentation
- Continuous cursor presentation across the upper/lower split
- ✅ Automatic detection of the secondary display
- ✅ Automatic connection to ScummVM
- ✅ Restore Both Screens recovery
- ✅ Automatic reconnection after surface recreation

### Input

- Large relative touch trackpad
- Single-tap left click
- Two-finger right click
- Double-tap-and-hold drag
- Dedicated left/right mouse buttons
- Controller support
  - Left stick → cursor
  - A → Left click
  - B → Right click

### Reliability

- Robust mouse ownership system preventing duplicate DOWN/UP events
- Safe mirror surface recreation
- Automatic recovery after display reconnects

## Tested

Hardware:

- ✅ AYN Thor dual-screen Android handheld

Games:

- Indiana Jones and the Fate of Atlantis
- Beneath a Steel Sky

## Project Status

### Current milestone

✅ **Proof of concept complete**

AdventurePad now demonstrates:

- live dual-display rendering
- independent lower-screen touch controls
- reliable communication with ScummVM
- hardware validation on the AYN Thor

### Planned work

Split View editing uses one normalized horizontal split. The upper display always renders the full-width
region above it and the lower live panel always renders the full-width region below it. The panel preserves
that region's aspect ratio, so its height follows the split and the trackpad occupies the remaining space.
ScummVM exposes its active target ID through the existing bridge so each game restores its own split.
Schema-v1 rectangle profiles migrate from their stored top boundary instead of being discarded.

In normal Split View Mode the live game interface is flush with the top and full display width, followed by
the flexible trackpad, LEFT/RIGHT buttons, and the bottom connection/settings controls. Cursor ownership is
exclusive at the split: rows above it render the cursor on the upper display, while the split row and rows
below it render the transformed cursor on the lower live surface.

- Adventure-themed UI
- Per-game layouts
- Bottom-screen action panels
- Notes and hint system
- Gesture improvements
- Two-finger scrolling
- Community themes

## Building

...
