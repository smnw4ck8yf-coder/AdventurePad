# Installing AdventurePad

> [Project overview](../README.md) · [Features](FEATURES.md) · [Known Limitations](KNOWN_LIMITATIONS.md) · [Building](BUILDING.md)

AdventurePad `0.2.0-preview` is distributed as one APK containing the AdventurePad interface and its embedded ScummVM engine. Stock or separately installed ScummVM is not required to run the embedded engine.

## Supported environment

AdventurePad is designed for the AYN Thor dual-screen Android handheld and requires Android 13 or newer. Other dual-display Android devices have not been verified. Ordinary single-screen compatibility should not be assumed.

## Before installing

- Download `AdventurePad-combined-arm64.apk` and `SHA256SUMS` from the same release and verify the checksum.
- Allow APK installation from the browser or file manager if Android asks.
- Supply your own legally obtained game data. No commercial games are included.
- Back up important game saves before changing an existing installation.

## Existing AdventurePad installation

Do not uninstall AdventurePad merely to update. Open the downloaded APK and install it over the existing app. A correctly signed release keeps the package identity and should preserve AdventurePad's app data.

The combined APK's embedded engine runs under AdventurePad's package and Android UID. A separately installed ScummVM app has separate private data and folder permissions. Keep the standalone app and any backup until you have confirmed that the combined build can see your games and load your saves. See [Combined AdventurePad APK](COMBINED_APK.md#data-and-migration) for the backup/import procedure.

## New installation

1. Install `AdventurePad-combined-arm64.apk`.
2. Launch **AdventurePad**.
3. Choose **Add Game** and grant access to the folder containing legally obtained game data.
4. Complete the embedded ScummVM detection flow and return to AdventurePad.
5. Select the launcher card to play.

## Important preview testing disclosure

This preview has been installed successfully as an in-place upgrade on an AYN Thor. The embedded engine, existing games and saves, immersive mode, Split View, and the AdventurePad process/UID integration were exercised; Cocoon integration appeared to work in that upgraded environment.

The following paths have **not** been tested:

- a clean installation on a device with no previous AdventurePad data;
- first-run setup and game-folder authorization from a completely fresh state;
- fresh dual-screen onboarding on a second or reset AYN Thor.

No working Thor was reset or uninstalled for this release preparation. Treat first-run behavior as preview-quality and report any onboarding or storage-permission issue with the device model and Android version.

## Troubleshooting

- **Android refuses an update:** do not uninstall immediately. Confirm the APK came from the official release and report the error; a signing-certificate mismatch must be investigated first.
- **A game is absent:** use Add Game and complete the embedded ScummVM detection flow for the game-data directory.
- **A folder cannot be read:** repeat Add Game and grant the requested Android folder access.
- **The lower screen is unavailable:** restart AdventurePad and confirm the device exposes its second screen as an Android presentation display.
- **Split View falls back to Trackpad Mode:** configure a valid per-game split and ensure the game exposes a compatible full-width horizontal interface region.
