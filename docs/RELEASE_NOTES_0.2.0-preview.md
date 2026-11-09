# AdventurePad 0.2.0-preview — draft release notes

> Draft only. Review before creating or publishing a GitHub release.

AdventurePad and its custom ScummVM engine are now packaged in a single APK for the AYN Thor. Install one AdventurePad application to browse games, launch the embedded engine, and use AdventurePad's lower-screen controls, immersive layouts, Split View, Companion, Notes, Walkthroughs, themes, and skins.

## Highlights

- One combined AdventurePad + embedded ScummVM APK.
- Preserves the AdventurePad package identity for in-place upgrades.
- Embedded engine runs inside `com.jamesmoran.adventurepad`; a separate ScummVM process is not used.
- Existing launcher library and saves worked in the tested upgrade environment.
- Immersive mode and Split View worked on the tested AYN Thor.
- Cocoon integration appeared to work in the tested upgrade environment.

## Installation

Download `AdventurePad-combined-arm64.apk` and verify it against `SHA256SUMS`. On an existing AdventurePad installation, install the APK as an update; do not uninstall first. Keep any separately installed ScummVM app and backups until games and saves have been verified in the combined build.

New users can install the single APK, launch AdventurePad, choose **Add Game**, and grant access to legally obtained game data. Detailed steps and migration guidance are in `docs/INSTALLATION.md`.

## Preview testing disclosure

The combined APK was successfully tested as an in-place upgrade on an AYN Thor. Clean installation, completely fresh first-run setup, and fresh dual-screen onboarding on a second or reset AYN Thor have **not** been tested. The working test device was not reset or uninstalled. First-time users should treat onboarding and storage authorization as preview-quality and report any issue.

## Verification record

The release candidate must pass `tools/verify-release-apk.sh` before upload. The check confirms:

- package `com.jamesmoran.adventurepad`;
- version `0.2.0-preview` (`versionCode` 2);
- original AdventurePad certificate SHA-256 `3A:07:5D:9A:DA:42:82:FC:8F:A6:19:AA:E2:FA:44:FE:39:68:A5:DB:AF:DF:A5:AC:AC:34:B9:CE:3C:3A:AA:13`;
- non-debuggable release packaging;
- AdventurePad application branding in every packaged locale;
- embedded arm64 `libscummvm.so`.

No commercial game data is included. AdventurePad and ScummVM licensing/source materials are included with the release artifact.
