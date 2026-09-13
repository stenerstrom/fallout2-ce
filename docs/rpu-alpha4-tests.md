# Alpha.4 audit corrections

This audit follows the alpha.3 character creation fix. It is not a claim that all
Restoration Project or other campaign behavior has been ported or playtested.

## Confirmed defects

- Config writers reported success even when buffered output, immediate writes or
  close failed. This also undermined the CE save-sidecar error check introduced
  earlier. All writers now propagate these failures. The comment-preserving
  settings writer leaves the original file and existing backup intact if writing
  or closing the temporary file fails. This is not a redesign of the engine's
  legacy whole-slot save transaction.
- Resuming a game-content installation charged already verified staged files to
  the free-space requirement again. It now counts only files that still need
  writing. Corrupt staged files still require space and cannot be activated.
- The engine always used script-created virtual files, ignoring the exposed
  UseFileSystemOverride setting. Engine lookup now follows the configured value.
  Script filesystem access and already-open readers remain valid. Hero Appearance
  has its own asset lookup and is not disabled by this switch. RPU scripts may
  themselves re-enable the setting and request a restart when a feature needs it.

## Regression evidence

Each defect was reproduced by a failing assertion against the previous production
implementation, then passed after the corresponding fix.

- Config tests link the actual config and dictionary implementations and inject
  buffered-write, immediate-write and close errors through file dependencies.
  They verify reported failure, original/backup preservation and a successful
  readback. No live save or settings files are modified by these tests.
- Installer tests provide exactly the staging safety margin as available space:
  a valid staged payload completes without reading the source again; a corrupt
  staged payload is rejected without activation.
- Virtual filesystem tests switch overrides off and on, check archive fallback
  and confirm that script data remains available.

Run os/android/tools/test-settings.sh and os/android/tools/test-commands.sh.
Android CI runs these tests and builds four ABIs. The private APK must also pass
the existing signature, source/native identity, complete content hash, application
version and 16 KB native-alignment checks before delivery.

## Tablet acceptance

Update the existing Wasteland Collection app, then check the character creation
MODEL/STYLE controls, start/load a game, and save into a new empty slot. Check
launch and save/load separately for RPU, Sonora and Nevada. Do not deliberately
fill the tablet storage or damage saves: those failure cases use isolated tests.

The alpha.3 touch checks, full EPA quest paths, all appearance/weapon combinations,
optional party control and full campaign playthroughs remain unverified.
