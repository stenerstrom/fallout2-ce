# Hero Appearance in character creation

Candidate: Wasteland Collection 1.4.0-alpha.3, versionCode 11.

The alpha.2 port supported player appearance archives, in-game selection and save
persistence, but omitted the character-creation panel. Enabling the setting could
not show that missing UI.

## Changes

- A rotating player preview fits inside the original 640×480 editor. Skills,
  tag buttons and the tag counter move right to make room.
- MODEL and STYLE open the existing English selectors. R and H are their keyboard
  shortcuts. Left/right arrows cycle through available packs; tapping the preview
  rotates it. Hit regions are larger than the arrow artwork.
- The preview follows sex changes. Cancel restores the entry appearance; Create,
  Erase and premade character selection reset it. The in-game character sheet
  keeps its existing layout.
- GCD templates append the same two big-endian model/style integers as sfall.
  Legacy templates default to the original appearance. Invalid, incomplete or
  unavailable appearance data fall back to available defaults.
- The Load template dialog accepts Enter. Its keyboard list selection uses the
  same bounds as the Save dialog.
- Appearance changes before a map is loaded skip map redraw.

## Checks completed before Android CI

- macOS native build passes.
- Existing HUD (28 assertions), game clock, fake-perk, virtual-file and global
  variable tests pass.
- Actual RPU creation UI displays default male, alternate male body, male long
  hair, default female and blonde female sprites. Skills and tag count remain
  readable in the original 640×480 layout.
- Switching from an unavailable male body to female falls back to female default.
  Cancel and reopening Modify restore the original male appearance.
- Saving a long-haired male template writes a 440-byte GCD whose last eight bytes
  are the big-endian pair (0, 1). No test scripts are active for these checks.

## Completion and device protocol

Use an isolated RPU data folder, with no injected scripts that set appearance.
Load the saved GCD after Erase, start the game, save to a new slot, fully restart
and reload. Confirm the same hairstyle. Also load a legacy template and try a new
Create screen with Hero Appearance disabled.

Build all four Android ABIs from this source revision. The private APK may reuse
the alpha.2 game content only if its ce.dat hash matches the new CI artifact.
Record final native hashes, APK signature and private test results with the APK.

On Honor MagicPad, install as an update and test MODEL/STYLE title taps, both
arrow rows, the preview tap, sex changes, and selecting tag skills after their
buttons move. Verify appearance after starting, saving and reloading. Automated
Mac clicks also failed to activate existing game buttons in this test environment;
keyboard checks do not certify touch input on the tablet.

Full EPA quest routes and every armor/weapon/animation combination remain outside
this focused fix. See the existing alpha.2 limitations for campaign compatibility.
