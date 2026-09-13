# RPU and Android integration baseline

Assessment date: 2026-09-13.

Current completion roadmap: [Plan for full RPU support](rpu-completion-plan.md). This document records the initial assessment; Android builds, device smoke tests, the launcher and command HUD have since been completed.

## Objective and baseline

Run the user's Restoration Project Updated fork with CE, then verify the same installation on Android. The candidate engine baseline is FOR:CE commit `15913457d1a3a931f5a3dac0649459cfd4620198`. The RPU source baseline is `stenerstrom/Fallout2_Restoration_Project` commit `c14d687e4c401f64f7ef4ee255de95f07a91a6e5`.

The original `stenerstrom/fallout2-ce` fork is based on `alexbatalov/fallout2-ce`, commit `e97087b9582f37075db347a89898887320753f8b`, whose README explicitly excludes RP support. FOR:CE has since implemented the core hooks and other RPU requirements and [tracks RPU as supported](https://github.com/fallout2-ce/fallout2-ce/issues/196). Its existing Android port is the starting point for mobile work.

This assessment confirms source-level support and a successful local macOS arm64 build. It does not certify a complete playthrough or compatibility with every component distributed alongside RPU.

## Findings to verify in the game

| Area | Source evidence | Next action |
| --- | --- | --- |
| Core RPU hooks | RPU's alcohol, Dogmeat and weapon scripts use `HOOK_USEOBJ`, `HOOK_USEOBJON`, `HOOK_GAMEMODECHANGE` and `HOOK_COMBATDAMAGE`; FOR:CE implements these. | Exercise the corresponding interactions and save/load behavior. |
| Installation checks | RPU `scripts_src/global/gl_k_modini.ssl` reads `ddraw.ini` for `WorldMapSlots=21` and `BoostScriptDialogLimit=1`, and requests game termination when checks fail. | Preserve RPU's configuration during installation. Do not remove `ddraw.ini` after CE migrates settings. |
| Hero Appearance | RPU `scripts_src/epa/epai37.ssl` calls `set_hero_style` and `set_hero_race`. FOR:CE `src/sfall_opcodes.cc` registers these handlers, but they only pop arguments and log “not implemented”. RPU's package sets `EnableHeroAppearanceMod=1`. | Test the EPA appearance machine and included appearance component. A registered opcode is not evidence that the feature works. |
| Animation speed options | RPU `gl_k_walking_speed.ssl` and `gl_k_goris_derobing.ssl` use `fs_seek`, `fs_read_short` and `fs_write_short` after option checks. These operations are absent from FOR:CE's registered opcode set. | Confirm the installed option values and reproduce each enabled path. Implement a portable solution before calling these options supported. |
| Virtual file aliases | FOR:CE implements `fs_copy`, `fs_find` and `fs_delete` for asset aliases, with documented limitations. | Test Cassidy dialogue/head assets and distinguish alias lookup from mutable virtual files. |
| Slot Jinxer | RPU `scripts_src/epa/epac3.ssl`, `Node015`, calls unregistered `set_fake_perk` before setting `GVAR_TOASTER_SLOTMACHINES`. FOR:CE's interpreter treats an unregistered opcode as a fatal script error. | Reproduce the toaster reward with this exact RPU build. The upstream tracker describes a cosmetic issue; source order warrants checking whether the global-variable assignment is reached in this version. This is an inference, not a reproduced failure. |
| Compiler and package | RPU build scripts target sfall/modders pack 4.5 and download a Linux `sslc` binary; packaging fetches additional component repositories. | Pin the actual RPU build and all selected component versions. Do not assume a source checkout or an arbitrary release matches the pinned fork. |
| Android | Existing Gradle configuration builds arm64-v8a, armeabi-v7a, x86 and x86_64; it checks Java/native SDL binding consistency. | Start with an arm64 device and the existing Debug packaging path; verify imported files, controls and lifecycle. |

See [SFALL_COMPATIBILITY.md](../SFALL_COMPATIBILITY.md) for the larger feature matrix. It explicitly excludes complete memory-address and virtual-file compatibility. The Windows sfall DLL is not the mechanism for native CE compatibility.

## Verification sequence

1. Prepare an isolated Fallout 2 installation with the exact RPU build and a recorded mod load order. Retain RPU configuration files and required component data.
2. Launch the built Mac app with `--scan-unimplemented`, then start a new game. The scanner is diagnostic evidence, not a substitute for executing optional branches or a complete playthrough.
3. Test character creation, Temple/Arroyo progress, dialogue, combat, area transitions, rest and saving/reloading after a complete app restart.
4. Test RPU content explicitly: Sulik's restored quest/content, the expanded world map, EPA interactions, the toaster reward, Cassidy and appearance changes. Add each bundled optional component to the recorded test matrix.
5. Fix reproduced failures with script or portable engine changes and retain the save/log used to reproduce each issue.
6. Build Android Debug through `os/android/gradlew assembleDebug`. Package the APK with the `ce.dat` built from the same engine checkout, following `.github/workflows/ci-build.yml`. Record APK/engine and RPU versions.
7. On Android, verify data import, a new game, the same RPU scenarios, touch controls, pause/resume and save/load after closing the app.

Full acceptance requires the selected RPU content and optional components to work on both desktop and Android, with remaining failures explicitly tracked. A main-menu launch is only the first smoke test.

## Local build evidence

CMake 3.31.10, Ninja 1.13.0, Apple toolchain, Debug, `CMAKE_OSX_ARCHITECTURES=arm64`:

```sh
cmake -S . -B out/build/rpu-macos -G Ninja \
  -DCMAKE_BUILD_TYPE=Debug -DCMAKE_OSX_ARCHITECTURES=arm64
cmake --build out/build/rpu-macos --target fallout2-ce --parallel 6
```

The `fallout2-ce` target completed with exit code 0 and produced a Mach-O arm64 executable and bundled `ce.dat`. The workspace README gives the exact commands using the local tool environment. Engine source and RPU scripts remain unchanged at this baseline.

At the time of this initial assessment, game execution and Android builds had not yet been performed. They have since succeeded using the user's original game data. See the completion roadmap above and the workspace test record for current evidence.

## References

- [Original CE README](https://github.com/stenerstrom/fallout2-ce/blob/e97087b9582f37075db347a89898887320753f8b/README.md)
- [RPU source](https://github.com/stenerstrom/Fallout2_Restoration_Project/tree/c14d687e4c401f64f7ef4ee255de95f07a91a6e5)
- [FOR:CE baseline](https://github.com/fallout2-ce/fallout2-ce/tree/15913457d1a3a931f5a3dac0649459cfd4620198)
- [RPU support tracker](https://github.com/fallout2-ce/fallout2-ce/issues/196)
- [Installation/configuration discussion](https://github.com/fallout2-ce/fallout2-ce/issues/622)
- [Build and scanner instructions](../CONTRIBUTING.md)


Nästa testversion med spelväljare, Sonora och Nevada beskrivs i [Wasteland Collection](wasteland-collection.md).
