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
6. Launch **Fallout 2 RPU CE (Debug)**, tap **Välj spelfiler**, and select the complete test-installation folder. The app imports its own copy; large installations can take several minutes.
7. Open **Inställningar** to choose display, interface, gameplay, sound and mod settings. Save your changes, return to the launcher, and tap **Spela**.
8. Start a new game. Check Arroyo, map transitions, dialogue, combat and save/load before testing later RPU content.

The engine's Android configuration currently requires Android 7.0/API 24 or newer. The package contains arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries. The previous build passed an initial new-game and save/load test on the connected HONOR tablet; later RPU content still needs testing.

## Settings before starting the game

The launcher exposes all 97 registered engine settings, grouped by purpose. **Bild** includes resolution presets, custom width/height, and integer scaling. Resolution changes take effect on the next game start. Higher resolution shows more of the map; lower resolution or higher scaling makes text and controls larger. At least 640 × 480 logical pixels must remain after scaling.

**Moddar och alla konfigurationsfiler** lists imported INI/CFG files in the game root, `mods`, and `data/config`. Each file can be edited by section or as full text, including commented and additional options. Not every legacy or optional sfall setting is implemented in FOR:CE. The current RPU 2.4.34 test installation requires `goris_derobing_speed=0` and `critters_walk_faster=0` in `mods/upu.ini` to avoid unimplemented `fs_seek` calls.

Exit through the game's menu before changing settings. The launcher and native engine use separate processes and a shared file lock; controls are disabled while the game is open. Configuration writes preserve unrelated keys and comments and use atomic replacement. A previous version is kept in the app's internal files directory. Existing saves are retained when the APK is installed as an update with the same signing key. Some gameplay and sound preferences can also be restored by loading an old save.

The launcher UI is currently Swedish. Controls tied to other platforms are labeled accordingly. CI checks the generated settings list against `src/settings.h` and `src/settings.cc` and runs Java tests for INI preservation, resolution validation and locking across processes:

```sh
python3 os/android/tools/generate_settings_schema.py --check
bash os/android/tools/test-settings.sh
```

## First target device

The connected tablet identifies itself as **HONOR YLE-W09** (the user's MagicPad 4), running Android 16/API 36, with `arm64-v8a` native code, 4096-byte memory pages and a 3000 × 1920 landscape display. The previous build was tested with official RPU 2.4.34: new game, save and load worked, and the user confirmed gameplay.

The build includes 16 KB ELF segment alignment and compressed native libraries using Android's documented compatibility path for older Android Gradle Plugin versions ([Android guidance](https://developer.android.com/guide/practices/page-sizes)). CI checks the resulting APK. The physical tablet test used 4 KB pages and therefore does not establish runtime compatibility with 16 KB devices.

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


## Private APK with bundled game files

A local build can include the prepared game installation and unpack it automatically on first launch. The complete variant is named **Fallout 2 RPU CE – komplett**, with application ID `io.github.stenerstrom.fallout2rpuce.complete`. It uses separate app data and can be tested alongside the regular Debug variant.

The asset preparation script excludes saves, backup folders, hidden files, logs and desktop executables. It requires an output directory outside the source repository and produces a manifest containing each file's size and SHA-256. The app verifies files while extracting and skips verified files after an interruption. A file with different existing contents causes an error and is not overwritten. While extraction is incomplete, the launcher cannot start the game. Later APK updates retain an already prepared installation, including its settings and saves; they do not silently replace its game content.

Example (run from the repository root, with Java 11 and Android SDK 32 configured locally):

```sh
python3 os/android/tools/prepare_private_bundle.py \
  /path/to/prepared/Fallout2-RPU /path/outside/repo/private-bundle \
  --native-apk /path/to/verified/fallout2-rpu-ce-debug.apk
cd os/android
./gradlew --no-daemon assembleDebug \
  -PEXCLUDE_NATIVE_LIBS \
  -PPREBUILT_NATIVE_LIBS=/path/outside/repo/private-bundle/native \
  -PPRIVATE_GAME_ASSETS=/path/outside/repo/private-bundle/assets
```

The reused native libraries must match the intended engine revision; rebuild them when changing native code. Configure and retain a local signing key through the ignored `os/android/debug-keystore.properties` file. The standard GitHub Actions build includes only the engine and checks that bundled game assets are absent. Keep the complete APK and original game data local.

The prepared RPU 2.4.34 installation contains approximately 1.45 GB of game files. The complete APK and its unpacked game data together need roughly 3 GB on the device.


## In-game command HUD

Version 1.3.0-rpu.4 adds a small **≡** button over the game surface. Tap it for commands or drag it to a different position. The panel closes before a command is sent, and its taps are consumed by Android rather than passed through to the map.

- **Speed:** 0.5×, 1×, 1.5×, 2×, 3× and 4×. The native game clock changes continuously, without resetting existing timer deadlines. Rendering, touch timing, keyboard repeat and mouse-button repeat use real time. Speed starts at 1× with each new game process. Actual acceleration is still limited by the device's frame rate.
- **Party Orders:** loot, heal, regroup, scatter, holster, player pickup/loot, switch pickup mode, ammo type and burst control. Bindings are read from `mods/party_orders.ini` when opening the panel, including modifier combinations and disabled keys. These buttons invoke the mod's keyboard commands; the action still depends on the current game context and companion abilities.
- **Common actions:** inventory, character, Pip-Boy, map, save/load dialogs, hand/weapon mode, end turn and Escape.
- **Skills:** sneak, lockpick, steal, traps, first aid, doctor, science and repair.

Commands use SDL's Android key-down/key-up path so that RPU's `HOOK_KEYPRESS` and `key_pressed` handling see them. Modifiers are pressed before the primary key and released afterward. Pending commands are cancelled and held keys released when the activity pauses or is destroyed.

Validation includes native clock tests for speed ratios, continuity, fractional time and 64-bit uptime, 28 keyboard-binding assertions, comparison of all 256 DIK mappings with the fetched SDL source, Java compilation and native builds. The complete APK's game assets are verified against their embedded SHA-256 manifest. Physical play-testing of the new HUD and companion actions remains to be done by the user.
