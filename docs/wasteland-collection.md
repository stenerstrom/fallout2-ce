# Wasteland Collection — first Android test build

Version: **1.4.0-alpha.1**, Android version code **9**.

This milestone adds an English game library to the existing private complete APK. It does not complete the remaining Restoration Project compatibility work.

| Profile | Private content baseline | Data directory |
|---|---|---|
| Fallout 2 | Restoration Project Updated 2.4.34, previous CE workarounds and optional assets | Existing external files root |
| Sonora | English 1.16.4E including Dayglow | games/sonora below that root |
| Nevada | Extended 2.0.3.4, English resources | games/nevada below that root |

The private application ID remains io.github.stenerstrom.fallout2rpuce.complete. Install the APK as an update to retain existing RPU saves. The public CI build contains the launcher and engine, without game data.

## Implemented

- Illustrated game cards, English labels, selected-game details, preparation progress, Play and Settings.
- Settings, imports, bundled content and native SDL working directory follow the selected profile.
- A separate game process and shared session lock prevent changing profiles or settings while a game runs.
- RPU keeps its existing save location. Other profiles cannot load or overwrite its saves.
- The command HUD keeps common keyboard actions, skills and speed controls for all games. RPU companion commands are shown only for RPU; HUD positions are stored per profile.
- A shared content pool stores identical bundled files once. Each profile has a versioned manifest with file sizes and SHA-256 hashes.
- Content updates stage and verify all changed managed files before activation, record a recovery journal and roll forward after interruption. Existing configuration files are seeds and are preserved. Unknown modifications to managed files stop the update instead of being overwritten.

This is not a general rollback or save-export system. Configuration merging, obsolete-file removal, user-triggered rollback and save export remain future work. A successful installation record avoids extracting the game every launch; it is not a complete on-launch integrity scan.

## Private inputs and adaptations

**Sonora:** [Cambragol release 1.16.4E](https://github.com/cambragol/Fallout-Sonora-English/releases/tag/1.16.4), archive Fallout_Sonora_1.16.4E.zip, SHA-256 4a96b3326b62c6a908b80d479a5eb598d3f7bc97c14ced3621e92945615b8dfc. The digest matched publisher release metadata. The [translator's site](https://cambragol.github.io/Fallout-Sonora/download/) describes a Fission distribution. The CE profile supplies ce.dat, fallout2.cfg and ddraw.ini; it does not run the Fission executable. Its starting settings were cross-checked with the CE web distribution linked by the translator. Original music backup copies and platform executables are excluded.

**Nevada:** [English translation project](https://github.com/KeyboardGecko/Fallout-Nevada-Translation) and its linked [Extended installer](https://www.mediafire.com/file/nqfrkb5fllbpqx4/Fallout_Nevada_Mod_Us.exe/file). Download SHA-256: e732fbf6ddf87f7136170a60930b8b0e957de0a36f2d236df61f4a56b1024686 (recorded locally; no publisher checksum was available). Inno Setup resources were extracted without executing the Windows installer. The prepared game combines MAIN data, Sfall E resources and the English overlay, plus the user's original Fallout 2 master/critter archives and ce.dat. Relative archive/music paths replace parent-directory paths. CE reads fallout2.cfg instead of Nevada.cfg. StartYear 2140, StartMonth 9, StartDay 10 follow the [CE web port](https://fallout-nevada.ru/). The installer's AmmoFile=AmmoNevada.ini setting is reproduced. Windows utilities, the optional inventory filter, KeyMod and appearance executable are not activated.

**RPU:** all 92 files from the previously delivered complete English bundle are retained byte-for-byte. Its known Goris/faster-walking workarounds remain in place.

Neither private game files nor the complete APK are uploaded to the source repository.

## Packaging

Use prepared, lowercase game folders without user saves:

~~~sh
python3 os/android/tools/prepare_collection_bundle.py \
  --rpu /private/rpu --rpu-version 'RPU 2.4.34 + CE workarounds' \
  --sonora /private/sonora --sonora-version '1.16.4E + Dayglow' \
  --nevada /private/nevada --nevada-version 'Extended 2.0.3.4 English' \
  --previous-bundle /private/previous-bundle /private/new-collection
~~~

The output directory must be new and outside the repository and input folders. Manifests live in assets/bundled-games/<profile>/manifest.json; content is stored at assets/bundled-pool/<sha256>. BUNDLE_INFO.json records manifest hashes. This collection contains 226 logical files in 198 pool entries, totalling 3,697,390,993 pool bytes before APK compression.

The local Java-only build reuses the verified four-ABI native libraries from HUD commit ab1dc4d4695ffd3ef4e78ef39ac65986af9657da; native source is unchanged in this milestone. Use JDK 11 and these options (AGP 7.2 needs extra memory for the large archives):

~~~sh
./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx6144m -XX:MaxDirectMemorySize=2g -Dfile.encoding=UTF-8' \
  -PEXCLUDE_NATIVE_LIBS -PPREBUILT_NATIVE_LIBS=/private/native \
  -PPRIVATE_GAME_ASSETS=/private/new-collection/assets assembleDebug
~~~

## Validation and limits

Automated checks cover 97 settings, 36 settings/lock assertions, 25 legacy extraction assertions, 32 profile/update/recovery checks, 28 HUD command assertions, all 256 key mappings, and game-clock continuity/speed. The Android launcher compiles successfully.

Isolated Mac CE tests reached the English main menu, character selection and opening map for both Sonora and Nevada, then saved and reloaded successfully. Their scratch saves are excluded from the APK. Sonora's opening test had no script errors in its log. Nevada's opening scene emitted null-object script errors from VbCCmndr.int and Animfrvr.int; the game continued and saved/loaded, but progression past the opening needs a dedicated compatibility test. These results do not certify the complete campaigns.

The new Android library has not yet been installed or visually tested on the Honor tablet. The user installs the APK. The first device test should verify:

1. Install as an update, open Fallout 2, confirm an existing RPU save still loads.
2. Return through the game menu, prepare Sonora, start a new game, save and reload.
3. Repeat for Nevada, including the opening conversation and exit from Vault 8.
4. Give each game a different resolution, switch between them and confirm settings/save isolation. Check touch targets, HUD movement and speed controls.

## Following milestones

Continue the accepted [RPU completion plan](rpu-completion-plan.md): pin full RPU input provenance, add save export and content regression coverage, implement fake perks and save persistence, mutable VFS support for walking/Goris, Hero Appearance, remaining optional components and restored-quest playthroughs. Add Sonora/Dayglow and Nevada progression checklists and their useful companion commands as compatibility is verified.
