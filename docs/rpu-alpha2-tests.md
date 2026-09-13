# RPU alpha.2 implementation and test record

Date: 2026-09-13. Candidate: Wasteland Collection 1.4.0-alpha.2, versionCode 10.
English UI; private content stays outside this repository. Device validation is pending.

## Target and evidence

RPU target: v2.4.34, BGforgeNet commit 6cd291f64a48782ed45fe116e5fb453ecbfc5488.
The EPA reward, critter walking and Goris source scripts were compared with the local
c14d687e fork and are identical. This is a targeted comparison, not a reproducible
build or a full content audit. The existing private English RPU package is retained.

## Implemented and verified

| Feature | Test and observed result | Remaining scope |
|---|---|---|
| Player fake perks/traits | Production C++ tests cover add/update/remove, ownership, bounded strings, packed little-endian records, legacy empty lists, all truncated prefixes, invalid counts and write failure. An isolated game script added Slot Jinxer and its quest global; the character sheet showed its icon/text. Save/restart/load retained both. | The actual EPA dialogue and slot-machine quest path must be played. Selectable and NPC fake-perk commands are not implemented. |
| Mutable virtual files | Production VFS and memory-reader tests cover big-endian integers/floats, seeks, bounds, original archive preservation, independent cursors, copy-on-write and reset. Original RPU scripts produced walking FPS equal to the frame count and Goris FPS 50. Script opcode round trips passed after reload. | Full Goris combat, interruptions, map transitions and speed-control combinations. fs_resize and persistent VFS entries are not implemented; these RPU scripts recreate overrides at game load. SFX engine overrides remain excluded. |
| Hero Appearance | Player virtual FIDs, selected archive plus fallback graphics, inventory FID integration and saved HAp_Race/HApStyle. Actual male long-hair art appeared; a missing style preserved the previous choice; script-visible FID remained ordinary. The English body/hairstyle picker works with the current choice selected. Full process restart retained the selected hairstyle. | Female variants, every armor/weapon/animation combination, and the full EPA path. set_dm/df_model remain unimplemented. Android HUD entry requires device testing. |
| List picker repair | Opening a short list at a nonzero selection previously crashed because it drew past the item array. The renderer now uses the clamped scroll offset and visible count. The appearance list was retested successfully. | General UI regression on Android. |
| Ammo rules | Separate process restarts with Default 0, Glovz 1 and YAAM 5. Engine logs selected the matching formula. Original Ammo INI Loader loaded 25 proto adjustments for each alternative. Rocket PID 14 multiplier/divisor was 1/1, 1/1 and 3/2 respectively. Switching back to Default restored 1/1. | Combat balance/results across ammo types and character perks. |
| Alternative explosions | Selecting the option mounted the bundled archive after the normal mod order; disabling it omitted that mount. | Actual explosion and damage animation in combat. |
| Save failure handling | New ce.sav declares format version 2 and requires sfallgv.sav. Removing that file from an isolated new slot produced a load error; restoring it allowed loading with perks/appearance intact. An older slot still loaded. Main/sidecar write failures now return failure instead of success. Global-state tests reject truncated/invalid records and replace state atomically. | End-to-end disk-full simulation and broader legacy save samples. |
| Android save export/restore | 48 Java assertions cover slot/proto/sidecar round trip, profile isolation, retained backup, three interrupted-activation boundaries, malformed/truncated ZIPs, traversal, duplicate paths, invalid journals and symlinks. Validation precedes replacement; session lock excludes a running game. | Android document-provider UI and real-device process interruption. |
| RPU option controls | English controls for walking, Goris speed, Hero Appearance, virtual files, merchant restocking, explosions and ammo. Ammo writes both script and engine selectors in one atomic INI update. Android Java compilation passes. | Touch layout and all option scenarios on the tablet. |

Fake-perk and VFS tests also passed AddressSanitizer/UndefinedBehaviorSanitizer runs
during development. Existing checks cover 97 engine settings, 36 INI/session assertions,
25 extraction assertions, 32 profile/update assertions, 28 command-binding assertions
and game-clock behavior. The final CI run builds all four Android ABIs, checks SDL
bindings and 16 KB ELF segment alignment, and produces matching ce.dat.

## Reproduce focused script tests

Use an isolated private game copy, never a player's live save folder. Tests intentionally
change character appearance, a fake perk and quest/global state. Compile only the chosen
SSL files from sfall_testing into that copy's data/scripts directory. Do not put these
test INT files into ce.dat or a bundled game profile.

The compiler used locally was sfall-team sslc 2025-06-18-01-40-04, via the pinned WASM
package. Invoke it with the SSL filename from the sfall_testing working directory:

~~~sh
node /path/to/compiler.mjs -q -p -l -O2 -d -s -n gl_test_fakeperks.ssl -o /private/test/data/scripts/gl_test_fakeperks.int
~~~

Repeat for gl_test_vfs.ssl, gl_test_hero.ssl and gl_test_rpu_ammo.ssl. Enable script
debug messages; use Misc/UseFileSystemOverride=1 and EnableHeroAppearanceMod=1.
Set mods/upu.ini main/critters_walk_faster=1 and goris_derobing_speed=50.
Start or load a disposable game. Search debug.log for FAKE_PERKS_TEST, HERO_TEST,
RPU_VFS_TEST and RPU_AMMO_TEST. The ammo test expects matching RPU/DamageFormula
and Misc/DamageFormula (0, 1 or 5). Stop the process when changing these INI options.

Local scratch evidence is under runtime/collection-smoke outside the source tree.
The scripts are regression probes, not a full quest or campaign test.

## Android acceptance still needed

Install as an update with the existing Complete app identity/signature, then:

1. Open an existing RPU save; export it to a ZIP outside the app.
2. Enable walking/Goris in Settings → Restoration Project options. Existing choices
   are preserved during upgrade, so older disabled values are not silently changed.
3. Open Quick commands → Change appearance outside combat; choose a style, save,
   close the process, and reload. Check inventory, movement, armor and weapons.
4. Export a disposable profile's saves, create another test save, restore the archive,
   then confirm the prior saves remain available through the backup export button.
5. Verify Sonora and Nevada still start/save/load and retain separate settings/saves.
6. Continue EPA and follower-command scenarios, then broader restored-content playthroughs.

Nevada's previously observed VbCCmndr.int/Animfrvr.int null-object opening errors are
not fixed in this milestone. Full Sonora/Dayglow, Nevada and RPU campaigns are not certified.
