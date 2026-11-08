# Combined AdventurePad APK

AdventurePad and ScummVM-AdventurePad remain separate repositories and standalone
Android projects. The distribution build composes them only when the opt-in
`adventurepad.bundledEngine` Gradle property is set.

## Recorded pairing

`scummvm-engine.properties` is the source of truth for the exact engine Git
revision and repository URL. Updating that revision is a deliberate release step
after the engine change has been committed and tested in ScummVM-AdventurePad.

The adapter in `scummvm-engine/` compiles the pinned engine's Android Java and
resources as a library, then packages the generated arm64 native library and
engine assets. The engine classes keep the `org.scummvm.scummvm` Java namespace,
but the installed application ID is only `com.jamesmoran.adventurepad`.

## Local build

First perform a full native build in the separate ScummVM-AdventurePad checkout.
Gradle-only packaging is not enough after C++ changes. Then run:

```sh
tools/build-combined-apk.sh debug ../ScummVM-AdventurePad
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. A standalone
AdventurePad build remains `./gradlew assembleDebug`; a standalone engine build
remains `make ScummVM-debug.apk` in ScummVM-AdventurePad.

Release builds require the four `ADVENTUREPAD_RELEASE_*` signing values through
the environment or ignored `local.properties` and use:

```sh
tools/build-combined-apk.sh release ../ScummVM-AdventurePad
```

## Data and migration

An in-place combined APK can upgrade an existing AdventurePad installation only
when it has the same application ID and signing certificate. AdventurePad data,
skins, notes, and preferences therefore remain in place.

The separately installed ScummVM package has a different Linux UID and private
data directory. Matching signatures do not grant private-file access, and SAF
folder grants belong to the old package/UID. Migration therefore uses ScummVM's
existing user-visible backup format rather than private-file access:

1. In standalone ScummVM, open **Global Options → Misc → Export backup** and
   save the ZIP to a user-visible folder.
2. Install the combined AdventurePad APK as an in-place AdventurePad update. Do
   not uninstall standalone ScummVM.
3. From AdventurePad, open the bundled ScummVM library, then use **Global
   Options → Misc → Import backup** and select the exported ZIP.
4. Android asks for each game/save folder grant that belongs to the standalone
   app. Select the same folders to authorize the combined app.
5. Confirm the configured target appears, launch it, create a new save, restart,
   and load both the imported and new saves before removing the standalone app.

The backup contains `scummvm.ini`, saves from the global and per-game save
paths, and descriptors for registered SAF trees. It does not transfer Android
permissions: each missing tree is deliberately re-authorized with the system
folder picker during import. Keep the original backup ZIP and standalone app
until the imported configuration and real save have been verified.

## Release automation and licensing

`.github/workflows/combined-release.yml` checks out the recorded engine revision,
builds pinned arm64 dependencies and the native engine, rejects a dirty engine
checkout, builds the signed APK, and uploads an unpublished release-candidate
artifact. It also includes ScummVM's GPL licence, copyright file, exact engine
source archive, build provenance, and checksums. Publishing remains a separate,
manual step.

The dependency lock currently uses Oboe 1.10.0, FLAC 1.5.0, and libpng 1.6.59.
The local engine binary used during the initial Thor proof was built earlier with
libpng 1.6.43; it is a test binary, not a release candidate.
