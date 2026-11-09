# AdventurePad 0.2.0-preview verification record

Verification date: 9 November 2026

Status: **not yet ready to publish**. The combined APK content and runtime integration are established, but a new release candidate must be built after the signing-workflow and application-label fixes, then pass the automated release gate.

## Static APK audit

The locally re-signed candidate derived from GitHub Actions run `42588859386` was inspected without installing or changing any device state.

| Check | Result | Evidence |
|---|---|---|
| APK signature structure | Pass | Android `apksigner verify` succeeds using APK Signature Scheme v2 with one signer |
| Original AdventurePad signer | Pass | Certificate SHA-256 `3a075d9ada4282fc8fa619aae2fa44fe3968a5dbafdfa5acac34b9ce3c3aaa13` |
| Package | Pass | `com.jamesmoran.adventurepad` |
| Version | Pass | `0.2.0-preview`, version code `2` |
| Release mode | Pass | No debuggable application flag |
| Embedded engine | Pass | `lib/arm64-v8a/libscummvm.so` is present, together with ScummVM engine data such as `gui-icons.dat`, `translations.dat`, and `scummmodern.zip` |
| Default branding | Pass | Default application and launcher label is `AdventurePad` |
| Localized branding | **Fail** | The embedded engine's localized `app_name` resources cause Android to report `ScummVM` as the application label in multiple locales |
| Test signing identity in original CI artifact | **Fail** | The unmodified run artifact used certificate SHA-256 `108eac2944ec4be309dec6c3854a3377435442f7054b6ea6e37f1dbfab5b84ee`, DN `CN=AdventurePad Test Build, OU=Testing, O=AdventurePad, L=London, C=GB` |

Audited file SHA-256 values:

- locally re-signed candidate: `26936002947aba1887df8c478df7670a7e9f2807c00bb253ada56c82b0e87929`;
- original run artifact: `c91e1e913fc74f2221012fd38ab22457175a6d660d0a6460915abc8ffa387e1b`.

These hashes identify the audited files only. Neither file should be published as the final release candidate because neither contains the application-label fix.

## Runtime evidence already collected

The locally re-signed combined APK was installed successfully as an in-place update on the existing AYN Thor installation. While a game was running:

- the active process and game activity belonged to `com.jamesmoran.adventurepad`, UID `10191`;
- `org.scummvm.scummvm.ScummVMActivity` ran inside the AdventurePad process;
- the standalone ScummVM package, UID `10189`, had no running process;
- AdventurePad's trackpad, ScummVM activity, and relative-input service shared the AdventurePad process;
- the embedded ScummVM class loads `libscummvm.so` from the AdventurePad APK.

Existing games and saves, immersive mode, and Split View worked in the upgraded installation. Cocoon integration appeared to work.

## Explicitly untested

- clean installation with no previous AdventurePad state;
- first-run setup and storage authorization from a completely fresh state;
- fresh dual-screen onboarding on a second or reset AYN Thor.

The working Thor was not reset, uninstalled, or otherwise changed for this audit.

## Final release-candidate gate

After review, configuration of the four GitHub Actions signing secrets, and an approved build, run:

```sh
tools/verify-release-apk.sh AdventurePad-combined-arm64.apk
```

Publication is blocked unless that command passes on the exact APK to be attached to the release. Also confirm its SHA-256 matches the generated `SHA256SUMS` entry. This repository change does not publish a release or trigger a build.
