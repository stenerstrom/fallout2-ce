# Fallout 2 RPU CE: Android test build

This development build uses FOR:CE as the engine for testing Restoration Project Updated (RPU). The app is named **Fallout 2 RPU CE (Debug)** and uses application ID `io.github.stenerstrom.fallout2rpuce.debug`, so it installs alongside the original CE app with separate game data and saves.

## Package contents

- `fallout2-rpu-ce-debug.apk`: Android Debug application.
- `ce.dat`: engine assets built from the same source commit as the APK.
- `EXAMPLE_fallout2.cfg`: example engine settings.
- `BUILD_INFO.json`: engine revision and the RPU source revision being targeted.
- `SHA256SUMS`: checksums for the package files.

The package does not include RPU or the original Fallout 2 game data. The RPU revision in `BUILD_INFO.json` is a source reference, not a claim that a matching RPU package has been built or tested.

## Install and import

1. Prepare a complete Fallout 2 installation with your original game data and RPU installed. RPU's source-code repository alone is not a playable installation.
2. Use a separate copy for testing. Keep the installation's original `ddraw.ini`, `mods/`, mod load order, `data/` and other resources. In particular, RPU checks `WorldMapSlots=21` and `BoostScriptDialogLimit=1` in `ddraw.ini`.
3. Copy this package's `ce.dat` into the root of that test installation. Review `EXAMPLE_fallout2.cfg` alongside the installation's configuration; the example is not an automatic replacement for the installed configuration.
4. Copy the complete test installation to an accessible folder on your Android device, such as Downloads. Preserve all files and directory structure. Resolve file-name case inconsistencies against the game's configuration.
5. Install `fallout2-rpu-ce-debug.apk` through Android's package installer.
6. Launch **Fallout 2 RPU CE (Debug)** and select the complete test-installation folder when asked. The app imports its own copy; large installations can take several minutes.
7. Start a new game. Check Arroyo, map transitions, dialogue, combat and save/load before testing later RPU content.

The engine's Android configuration currently requires Android 7.0/API 24 or newer. The package contains arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries. Device compatibility and RPU gameplay still need verification.

## First target device

The first planned device is the user's **HONOR MagicPad 4**. HONOR lists the model with Snapdragon 8 Gen 5 and MagicOS 10 / Android 16 ([manufacturer specification](https://www.honor.com/uk/tablets/honor-magicpad-4/buy/)). The installed OS version and memory page size on the actual device still need to be recorded.

The build includes arm64 native code, 16 KB ELF segment alignment and compressed native libraries using Android's documented compatibility path for older Android Gradle Plugin versions ([Android guidance](https://developer.android.com/guide/practices/page-sizes)). CI checks the resulting APK rather than relying on build settings alone. These checks do not replace running the game on the tablet.

With the tablet connected for testing, record:

```sh
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb shell getconf PAGE_SIZE
```

## Controls

- Move one finger to move the mouse cursor.
- Tap one finger for a left click.
- Tap two fingers for a right click.
- Move two fingers to scroll.

## Reporting a test result

Record the engine commit from `BUILD_INFO.json`, actual installed RPU version, optional components and load order, device model, Android version and the steps that reproduce the problem. Include a save and relevant debug output when available.

Pay particular attention to EPA appearance changes, animation speed options, the Slot Jinxer reward and Cassidy dialogue. Core RPU support is documented upstream, but some optional sfall functions remain incomplete.

## Build updates

GitHub Actions caches a development signing key for this project branch so consecutive builds can usually be installed as updates. If that cache is lost, Android may reject an update signed with a new key. Preserve your saves before uninstalling any test app. Release signing is a separate future step.
