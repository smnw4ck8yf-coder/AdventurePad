# AdventurePad

AdventurePad is a companion application for ScummVM designed specifically for dual-screen Android handhelds such as the AYN Thor.

It places the game on the upper display while providing a dedicated touch trackpad and controller interface on the lower display, creating a Nintendo DS-style point-and-click experience.

## Current Features

- Automatic dual-screen launch
- Large relative touch trackpad
- Single-tap left click
- Two-finger right click
- Double-tap-and-hold drag
- Dedicated Left and Right mouse buttons
- Controller support
    - Left stick → cursor
    - A → Left Click
    - B → Right Click
- Restore Both Screens button
- Automatic reconnection if AdventurePad is reopened
- Robust mouse ownership system preventing duplicate DOWN/UP events

## Tested

Successfully tested with:

- Indiana Jones and the Fate of Atlantis
- Beneath a Steel Sky

## Project Status

Current milestone:

✅ Reliable mouse input system complete.

Next planned work includes:

- Improved UI polish
- Adventure game themed interface
- Per-game layouts
- Additional quality-of-life features

## Building

```bash
./gradlew installDebug
```

If Java cannot be found:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

Then run:

```bash
./gradlew installDebug
```

## License

Work in progress.