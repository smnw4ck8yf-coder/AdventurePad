# Building AdventurePad

> [Project overview](../README.md) · [Installation](INSTALLATION.md) · [Features](FEATURES.md) · [Known Limitations](KNOWN_LIMITATIONS.md)

Release signing and debug-signer migration are covered in [Release Signing Preparation](RELEASE_SIGNING.md).

AdventurePad consists of two source projects that produce a matching APK pair:

```text
upstream ScummVM
        ↓
AdventurePad-maintained ScummVM changes
        ↓ protected Android bridge
AdventurePad app
```

The AdventurePad app builds independently, but the installed product requires its matching custom ScummVM build. Official ScummVM does not contain the bridge used by AdventurePad.

## Current toolchain contract

| Requirement | AdventurePad | Custom ScummVM |
|---|---:|---:|
| Gradle wrapper | 9.5.0 | 9.6.1 in generated `android_project` |
| Android Gradle Plugin | 9.3.1 | 9.2.1 |
| Compile/target SDK | 37 | 37 (target defaults to compile SDK) |
| Minimum SDK | 33 / Android 13 | 16; the paired AdventurePad product still requires Android 13+ |
| Java source/bytecode | Java 11 | Android/Java plus native C++ |
| Android NDK | Not used | `23.2.8568313` |
| Current tested ABI | Android-managed app | `arm64-v8a` native ScummVM library |

Use a JDK supported by the checked-in Gradle wrappers; Gradle 9 requires JDK 17 or newer. The current local environment uses JDK 25. Install Android SDK platform 37 and accept its required SDK licenses. Local SDK paths belong in ignored `local.properties` files or environment variables, never in version control.

## Build the AdventurePad app

From the AdventurePad repository:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug and release variants both use `com.jamesmoran.adventurepad`; no application-ID suffix is configured. No release-signing configuration currently exists, so `assembleDebug` is the verified installable developer path. The build copies the canonical skin-authoring JSON into generated app assets during `preBuild`.

ScummVM does not need to be built first to compile or test this project, because the bridge is implemented through Android intents, Messenger messages, permissions, and package/component names rather than a compile-time module dependency.

Connected-device tests, when a suitable device is available, use:

```sh
./gradlew connectedDebugAndroidTest
```

## Build the custom ScummVM APK

The custom source checkout currently lives beside AdventurePad as `ScummVM-AdventurePad`. It has complete upstream history and is based on upstream ScummVM commit `4edac15aae5e1fe475eb5d4767b4c5ece3636164`, followed by 12 committed AdventurePad changes. The prepared publication candidate is the clean `adventurepad` branch at `fe5bea93fe4180489d83c4586f4aad1d086a388c`. It is the intended ScummVM source counterpart for AdventurePad `v0.2.0-preview`; this integration label does not replace or invent an upstream ScummVM semantic version.

### Reproducibility limitation

A clean ScummVM Android build is **not yet reproducible from the repository alone**. The current arm64 build uses external, untracked dependency outputs:

- Oboe 1.10.0 headers and `liboboe.a`;
- libpng 1.6.43 headers and static library;
- a separately prepared static FLAC installation whose exact source version/build recipe is not recorded.

The repository does not yet provide a bootstrap script or documented commands for producing those dependency prefixes. Publish that setup before presenting the ScummVM fork as independently reproducible.

### Audited local build shape

Once equivalent arm64 dependency prefixes exist, the current configuration can be reproduced in this form, replacing each placeholder with a local path:

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/23.2.8568313"
export ADVENTUREPAD_SCUMMVM_DEPS=/path/to/prepared/arm64-dependencies

CXXFLAGS="-I$ADVENTUREPAD_SCUMMVM_DEPS/src/oboe-1.10.0/include" \
LDFLAGS="-L$ADVENTUREPAD_SCUMMVM_DEPS/oboe-build-arm64" \
./configure \
  --host=android-arm64-v8a \
  --with-flac-prefix="$ADVENTUREPAD_SCUMMVM_DEPS/flac-prefix-arm64" \
  --with-png-prefix="$ADVENTUREPAD_SCUMMVM_DEPS/libpng-prefix-arm64"

make -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)" ScummVM-debug.apk
```

The make target builds the native `libscummvm.so`, generates `android_project`, runs its Gradle debug build, and copies the resulting APK to:

```text
ScummVM-debug.apk
```

The intermediate Gradle output is:

```text
android_project/build/outputs/apk/debug/ScummVM-debug.apk
```

`android_project`, native object files, generated assets, `config.mk`, `config.h`, logs, libraries, and APKs are build products/local configuration and are ignored by Git. Do not publish them as source changes.

Running only this command:

```sh
cd android_project
./gradlew assembleDebug
```

repackages the already generated project and existing native library. It compiles current Android Java sources through `src.properties`, but it does **not** rebuild changed ScummVM C++ code. Use the configure/make path for a true source build.

### Variants and package IDs

- Debug APK: `org.scummvm.scummvm.debug`
- Release variant: `org.scummvm.scummvm`

AdventurePad currently binds specifically to the debug package. Therefore, build and install the ScummVM **debug** variant for the current architecture. The release variant would collide with official ScummVM and would not satisfy AdventurePad's hard-coded component target.

## Signing the pair

Neither project defines a shared release-signing configuration. Android's Gradle plugin signs debug builds with the developer machine's default debug identity. When both projects are built by the same user on the same machine, they receive the same certificate and their signature-protected cross-app permissions work.

Different machines normally have different debug certificates. Building one half elsewhere can produce a pair that cannot use the protected bridge, and it cannot update an installation signed by James's current debug certificate.

A future public release process must sign both APKs with one durable controlled release identity. The Android debug certificate is not an acceptable release-signing identity. Do not commit keystores, passwords, signing properties, or machine-specific paths. See [Release Signing Preparation](RELEASE_SIGNING.md).

Install a locally built matching pair with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r ../ScummVM-AdventurePad/ScummVM-debug.apk
```

Build and install both from the same signing environment. Then perform physical-device smoke tests for launcher discovery, Add Game, Play, input, save actions, Trackpad/Split View, Companion, Notes/Walkthrough, and process/display recovery.

## James's local shortcut

The private `updateadventurepad` shell function builds `AdventurePad` with `assembleDebug`, installs it with `adb install -r`, runs `assembleDebug` inside the existing ScummVM `android_project`, installs that APK with `adb install -r`, and force-stops both packages. It does not uninstall either app and does not launch AdventurePad afterward.

Because it only invokes Gradle inside the generated ScummVM project, it does not rebuild native C++ changes or recreate external dependencies. It is a local incremental packaging/install shortcut, not a public or clean-build procedure.
