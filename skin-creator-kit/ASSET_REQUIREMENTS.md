# Creator requirements

- Read `LAYOUT_SPEC.md` and use the state-specific PNG in `templates/`; the former generic upper/lower guides are superseded.
- Work in sRGB PNG. Use transparency wherever native UI or live content must show through.
- In Standard style AdventurePad draws control text and chrome. In Immersive, artwork must visually identify the fixed Trackpad and Companion hit regions; AdventurePad still owns semantics, accessibility, and interaction geometry.
- Top gameplay is 1920×1080. Every actual runtime viewport is protected, and 16:9 may leave no visible surround at all.
- Each lower screen-state guide is 1240×1080. Safe-drawing insets and dp-to-pixel density are runtime inputs, so use the printed formulas instead of assuming the normalized example rectangles are universal.
- `trackpad.surface` uses 96 px suggested nine-slice edges. Do not put essential detail in the stretchable center.
- LMB/RMB state guides are 528×224 with 48 px suggested nine-slice edges. Companion/Settings state guides are 264×112 with 24 px suggested edges. Notes/Walkthrough state guides use their exact 572×192 reference hit-area geometry with 32 px suggested edges. Immersive-oriented button art may include its visual label because native labels are hidden there; if pressed is missing, normal remains visible.
- `bottom.split.background` is always below the runtime-sized mirror view. Do not paint an assumed fixed live-content hole into the source asset.
- Companion, Notes, and Walkthrough backgrounds theme the lower display. Standard Interface Style keeps the centered opaque native page. Immersive uses each exact asset as a full page and independently falls back to native rendering when absent. Paint the Companion title and surrounding decoration into `companion.background`; paint Notes and Walkthrough into their dedicated normal/pressed assets. Do not define interaction coordinates. In the 2 px/dp reference, the 96 dp-high action hit regions are exactly `[32,240,604,432]` and `[636,240,1208,432]`.
- `panel.frame` must use nine-slice with a transparent center. Runtime deliberately omits its center patch and draws only the border around Companion-family pages.
- `preview.png`: 1200×675, opaque, contain-safe. It is not used as application UI.
- Every rendering asset is optional and falls back independently. Every referenced asset needs its exact SHA-256 in `skin.json`.
- Use a reverse-domain lowercase ID such as `org.artist.skin-name`. Do not use the reserved `builtin.*` namespace.
- Package only `skin.json`, `preview.png`, PNG/WebP artwork, and documented future-compatible font files. Do not include source project files, archives, scripts, executables, or licenses that forbid redistribution.
