# Known Limitations

AdventurePad is active, pre-stable development software. These notes describe the current implementation and validation scope without implying that every item is a confirmed defect.

## Hardware support

AYN Thor is the only intended and tested device. Other dual-display Android devices are unverified and unsupported, even if they expose a compatible presentation display. Single-screen Android use is not supported.

## Custom ScummVM dependency

AdventurePad requires its matching modified ScummVM APK. Stock ScummVM does not provide the required bridge. Both APK versions and their signing identity must match for the signature-protected permissions to work.

## Preview and development status

The project is pre-stable, and breaking data or installation migrations may still occur before a stable release. Published preview binaries can lag behind the current development source; check each release's notes rather than assuming feature parity with this repository.

## Split View

Split View may need per-game setup, and compatibility can vary across engines and games. Broader validation is ongoing. AdventurePad falls back to Trackpad Mode when a valid crop or connection is unavailable.

## Controller focus and display lifecycle

L2+R2 behavior is not yet guaranteed across every possible Android focus and window state. Recent lifecycle and cold-start recovery paths also need broader physical-device regression coverage.

## Skin importing

- Importing an identical master PNG generates the same skin ID and is rejected as a duplicate.
- There is no replace or update workflow for an existing imported skin yet.
- Immersive eligibility depends on the skin supplying the required usable assets.
- External skins do not currently reskin the launcher.

## Launcher and library

The current sort selection may reset to Manual after the app process is killed or restarted. Manual card order and recorded launch timestamps do persist.

## Walkthrough and Companion

The Walkthrough reader stores a line-spacing preference internally, but the interface does not currently expose a control for it. Manual, Dialogue, Statistics, and time-played features are not currently available.

## Releases and builds

A reproducible clean build process for the custom ScummVM native dependencies is still being formalized. The shared release-signing strategy for the two APKs is also not final. The current public preview is historical and older than the development source.

See also: [Features](FEATURES.md) · [Installation](INSTALLATION.md) · [Building](BUILDING.md)
