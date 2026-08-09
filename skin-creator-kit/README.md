# AdventurePad Skin Creator Kit v1 — measured display templates

This kit contains guides, not finished artwork. Start with `LAYOUT_SPEC.md`, use `LAYOUT_SPEC.json` when exact formulas are needed, and open the measured PNG guides in `templates/`. The templates cover the actual AdventurePad display states: top gameplay, lower trackpad, lower Split View, Companion, Notes, Walkthrough, LMB/RMB, and the Companion/Settings utility controls.

Nothing in this kit skins the physical Thor shell, D-pad, sticks, ABXY buttons, bezels, hinge, or hardware outside the display pixels.

Required package files:

1. `skin.json` based on `skin.json.template`.
2. `preview.png` at 1200×675.

Everything except `skin.json` and `preview.png` is optional. The runtime now selects separate Trackpad and Split View backgrounds, Companion/Notes/Walkthrough backgrounds, border-only `panel.frame`, and normal/pressed artwork for LMB, RMB, Companion, and Settings. Native labels, input, semantics, and accessibility remain authoritative.

Run `python3 tools/generate-skin-templates.py` to regenerate the PNG guides, then `python3 tools/generate-skin-creator-kit.py` to produce the distributable creator-kit ZIP. Neither command creates an importable skin or final artwork.
