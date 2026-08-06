# AdventurePad

AdventurePad is a companion application for ScummVM designed specifically for dual-screen Android handhelds such as the AYN Thor.

It transforms classic point-and-click adventures into a Nintendo DS-style experience by displaying the game on the upper screen while providing a dedicated touch trackpad and controls on the lower screen.

## Current Features

### Dual-display

- ✅ Live mirrored ScummVM rendering on the lower display
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

- Adventure-themed UI
- Per-game layouts
- Bottom-screen action panels
- Notes and hint system
- Gesture improvements
- Two-finger scrolling
- Community themes

## Building

...