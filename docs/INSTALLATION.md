# Installing AdventurePad

> [Project overview](../README.md) · [Features](FEATURES.md) · [Known Limitations](KNOWN_LIMITATIONS.md) · [Building](BUILDING.md)

AdventurePad currently operates as a matching pair of Android apps:

| App | Current package ID | Purpose |
|---|---|---|
| AdventurePad | `com.jamesmoran.adventurepad` | Launcher, lower-screen controls, Split View configuration, Companion, Notes, Walkthroughs, themes, and skins |
| AdventurePad ScummVM | `org.scummvm.scummvm.debug` | The modified ScummVM engine, game rendering, library/configuration data, save integration, and the protected bridge used by AdventurePad |

Install both APKs from the same AdventurePad release. Stock ScummVM does not implement the AdventurePad bridge and cannot replace the matching custom APK.

## Supported environment

AdventurePad is designed for and developed/tested on the AYN Thor dual-screen Android handheld. The AdventurePad app requires Android 13 or newer and expects Android to expose a non-default presentation display for the second screen.

Other dual-display Android devices may satisfy the implementation's display checks, but they have not been verified and are not currently supported. Ordinary single-screen Android compatibility should not be assumed.

## Before installing

- Obtain both APKs from the same AdventurePad release. Do not mix versions from different releases.
- Allow APK installation from the browser or file manager you use to open the downloads, if Android asks.
- Supply your own legally obtained game data. AdventurePad and ScummVM do not include commercial games.

The custom ScummVM package can coexist with stock ScummVM because their package IDs differ. Despite its historical `.debug` package suffix, the release-pair `adventurepadRelease` build is non-debuggable. You do not need to remove stock ScummVM, but AdventurePad will ignore it and use only `org.scummvm.scummvm.debug`.

## Install

Installation order is not significant, provided both apps are installed before use:

1. Install the matching AdventurePad APK.
2. Install the matching AdventurePad ScummVM APK.
3. Launch AdventurePad from the Android launcher.

For development installs with Android platform tools:

```sh
adb install -r AdventurePad-vX.Y.Z.apk
adb install -r ScummVM-AdventurePad-vX.Y.Z.apk
```

The apps use signature-protected Android permissions for their private bridge. These permissions do not produce a user prompt, but both APKs must be signed by the same publisher identity. A mismatched pair may install yet fail to communicate, or Android may reject an attempted update because its signer differs.

## Add the first game

1. Open AdventurePad.
2. Choose **Add Game** in the AdventurePad launcher.
3. AdventurePad opens the matching ScummVM file/detection flow.
4. Grant access to the folder containing your legally obtained game data when Android asks.
5. Select the game-data directory, complete ScummVM's detection/options flow, and return to AdventurePad.
6. Select the new launcher card to play.

Folder access is granted to the custom ScummVM package. A separate stock ScummVM installation has its own configuration and Android storage grants.

## Updating

Update both apps with the matching pair from the new release. A normal update preserves app data when package IDs and signing identities remain compatible.

The public `v0.1.0-preview` used the former debug signing identity. Beginning with `v0.2.0-preview`, releases use the durable controlled AdventurePad signing identity. Moving from the first preview may therefore require uninstalling and reinstalling both apps. Follow the release notes for the exact pair being installed.

Do not uninstall merely to update unless the release notes require it. Uninstalling can remove:

- AdventurePad settings, per-game Notes and Walkthrough data, imported skins, and custom launcher artwork;
- the custom ScummVM configuration, default app-private saves, and persisted folder permissions.

Game files stored in a separately chosen user folder are normally outside the app's private data, but their folder grant may need to be selected again. Back up important saves and use ScummVM's backup/export facility before any uninstall/reinstall migration.

## Troubleshooting

- **AdventurePad cannot connect to ScummVM:** confirm that `org.scummvm.scummvm.debug` from the same release is installed. Stock ScummVM is not sufficient.
- **Android refuses an update:** the installed and replacement APKs may have different signing certificates. Back up data before considering uninstall/reinstall.
- **A game is absent:** use Add Game and complete ScummVM's detection flow for the directory, not an individual game file.
- **A folder cannot be read:** repeat the Add Game flow and grant the requested Android folder access.
- **The lower screen is unavailable:** confirm the device exposes its second screen as an Android presentation display and restart both apps. Non-Thor hardware remains unverified.
- **Split View falls back to Trackpad Mode:** configure a valid per-game split and ensure the game/engine exposes a compatible full-width horizontal interface region.

## Current preview status

The existing [v0.1.0 preview](https://github.com/smnw4ck8yf-coder/AdventurePad/releases/tag/v0.1.0-preview) is an historical early preview. Its two-APK installation model remains correct, but it predates the current launcher, theme, and skin-authoring work. Use only the two APKs attached to that same release if testing it.
