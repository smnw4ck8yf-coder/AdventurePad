# AdventurePad Skin Creator Kit v1 — measured display templates

This kit contains guides, not finished artwork. Start with `LAYOUT_SPEC.md`, use `LAYOUT_SPEC.json` when exact formulas are needed, and open the measured PNG guides in `templates/`. The templates cover the actual AdventurePad display states: top gameplay, lower trackpad, lower Split View, Companion, Notes, Walkthrough, LMB/RMB, Companion/Settings utilities, and the dedicated Notes/Walkthrough action states.

For single-PNG creation, `../skin-authoring/AdventurePad-Skin-Template-v2.png` is the canonical production canvas and `AdventurePad-Skin-Template-v2-reference.png` is its labelled learning reference. Submit neither the untouched canvas nor the reference guide: submit only the completed 4720×4040 sRGB PNG to AdventurePad. The app's package files, manifests, hashes, folders, and sliced assets remain internal implementation details.

Nothing in this kit skins the physical Thor shell, D-pad, sticks, ABXY buttons, bezels, hinge, or hardware outside the display pixels.

Required package files:

1. `skin.json` based on `skin.json.template`.
2. `preview.png` at 1200×675.

Everything except `skin.json` and `preview.png` is optional. The runtime selects separate Trackpad and Split View backgrounds, Companion/Notes/Walkthrough backgrounds, border-only `panel.frame`, and normal/pressed artwork for LMB, RMB, Companion, Settings, Notes, and Walkthrough. Standard style supplies visible native control treatment. Immersive style uses artwork for appearance while AdventurePad retains fixed input, semantics, and accessibility. Skins predating the Notes/Walkthrough assets retain their existing Companion background/native fallback.

Run `python3 tools/generate-skin-templates.py` to regenerate the PNG guides, then `python3 tools/generate-skin-creator-kit.py` to produce the distributable creator-kit ZIP. Neither command creates an importable skin or final artwork.
