# AdventurePad

AdventurePad is a companion application for ScummVM designed specifically for dual-screen Android handhelds such as the AYN Thor.

It transforms classic point-and-click adventures into a Nintendo DS-style experience by displaying the game on the upper display while providing a dedicated touch interface, live game controls, and controller shortcuts on the lower display.

---

# Current Features

## Dual Display

- ✅ Live mirrored ScummVM rendering on the secondary display
- ✅ Dynamic Split View with adjustable horizontal split
- ✅ Full-width cropped game interface rendered on the lower display
- ✅ Independent upper game / lower interface rendering
- ✅ Per-game Split View profiles
- ✅ Automatic migration from legacy crop profiles
- ✅ Persistent Split View / Trackpad mode
- ✅ Automatic secondary display detection
- ✅ Automatic ScummVM connection
- ✅ Automatic mirror surface recreation after display reconnect
- ✅ Restore Both Screens recovery

---

## Split View Editor

- Smooth drag-to-adjust horizontal split
- Fine adjustment controls
- Live preview
- Save / Cancel workflow
- Normalized split storage
- Versioned profile persistence
- Per-game restoration using the ScummVM target ID

---

## Input

### Touch

- Large relative touch trackpad
- Single tap left click
- Two-finger right click
- Double-tap-and-hold drag
- Dedicated Left / Right mouse buttons

### Controller

- Left stick → mouse cursor
- A → Left click
- B → Right click
- Two-finger double tap shortcut for mode switching
- L2 + R2 shortcut for Split View / Trackpad mode switching
- Normal trigger behaviour preserved outside the shortcut

---

## Cursor System

- Continuous cursor ownership across both displays
- Cursor rendered only on its owning display
- Upper display owns rows above the split
- Lower display owns the split row and everything below
- Cursor coordinates transformed correctly into the lower cropped surface
- No duplicated cursor rendering

---

## Rendering

- Exact complementary upper/lower rendering regions
- Aspect-ratio preserved lower interface panel
- Dynamic panel height based on split position
- Full-width rendering without distortion
- Surface generation tracking
- Safe mirror surface recreation
- TextureView host implementation available for experimentation
- Existing SurfaceView implementation retained

---

## Reliability

- Robust mouse ownership system preventing duplicate DOWN / UP events
- Generation-safe mirror attachment lifecycle
- Safe mirror surface recreation
- Crop generation tracking
- Automatic recovery after display reconnects
- Regression tests covering repeated mirror recreation

---

# Tested

## Hardware

- ✅ AYN Thor dual-screen Android handheld

## Games

- Indiana Jones and the Fate of Atlantis
- Beneath a Steel Sky

---

# Project Status

## Milestone

# ✅ Split View Milestone Complete

AdventurePad now provides:

- Live dual-display rendering
- Adjustable Split View
- Per-game split persistence
- Independent touch trackpad
- Controller shortcuts
- Live lower interface rendering
- Reliable mirror recreation
- Hardware validation on the AYN Thor

The original proof-of-concept objective has been achieved.

---

# Known Issues

### L2 + R2 after game launch

Immediately after launching a game, the L2 + R2 Split View shortcut does not activate until the lower display has received focus.

The shortcut also stops working if focus returns to the upper display (for example by touching the upper touchscreen).

This is believed to be an Android input focus issue rather than a rendering issue.

Current workaround:

- Touch the lower display once.
- L2 + R2 then functions normally.

This issue is intentionally deferred for a future milestone.

---

# Planned Work

## User Experience

- Adventure-themed interface
- Improved UI polish
- Community themes
- Animated transitions

## Gameplay

- Per-game lower-screen layouts
- Context-sensitive action panels
- Notes system
- Hint system
- Two-finger scrolling
- Additional gesture shortcuts

## Technical

- Resolve Android display-focus dependency for controller shortcuts
- Evaluate TextureView vs SurfaceView performance
- Reduce rendering latency where possible
- Additional hardware compatibility testing
- Upstream investigation into ScummVM integration

---

# Building

...