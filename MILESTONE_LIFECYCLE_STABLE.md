# AdventurePad Lifecycle-Stable Milestone

Commit: f308a01  
Tag: v0.2-lifecycle-stable

## Hardware

- AYN Thor
- Android 13

## Verified functionality

- Dual-display launch
- ScummVM on the upper display
- AdventurePad on the lower display
- Relative trackpad movement
- Single-tap left click
- Two-finger right click
- Double-tap drag
- Dedicated left and right mouse buttons
- Dedicated-button drag
- Controller A = left mouse
- Controller B = right mouse
- START and D-pad forwarded to ScummVM
- Back closes AdventurePad without stopping ScummVM
- AdventurePad reconnects to a running ScummVM session
- Connection loss and restart recovery
- Lifecycle-safe mouse and controller cleanup
- No duplicate or stuck mouse-button events
- Cancelled or fabricated Compose releases cannot produce phantom taps
- Polished lower-screen interface
- Hidden diagnostics
- Restore Both Screens

## Games tested

- Indiana Jones and the Fate of Atlantis
- Beneath a Steel Sky

## Important resolved defect

A platform ACTION_CANCEL followed by a Compose-fabricated Release was previously accepted as a tap after launch or reopening.

Tap recognition now requires a matching genuine platform ACTION_UP from the same touch sequence and reset generation.

## Deferred features

- Trackpad sensitivity
- Haptics
- Two-finger vertical scrolling
- Per-game layouts
- Themes
- Notes and hints
- Cocoon/Daijisho frontend integration
