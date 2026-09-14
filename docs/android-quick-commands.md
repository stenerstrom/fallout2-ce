# Android Quick commands

The floating button opens a compact panel with Quick, Party (RPU only), Game and
Skills tabs. Its position, last tab and up to six favorites are stored separately
for each game. Hold a command to add/remove a favorite. Speed presets remain
visible, highlight the active value and keep the panel open when changed.

Commands close the panel and wait for SDL window focus before emitting real
Android key presses. Modifiers go down first and up last. Pause/dispose cancels
pending commands and releases any held keys. A sent-command toast acknowledges
only that a key was sent, not that a companion successfully executed the order.

Party commands follow mods/party_orders.ini. Missing keys use the documented
defaults; explicit blank or 0 disables a command. Malformed/unreadable bindings
explain the problem when tapped and never send a guessed replacement. Toggle
auto-loot/burst are actions; the HUD does not pretend to know those script states.

Hero Appearance is available during character creation (MODEL / STYLE) and
through EPA. Its Android HUD entry and unused JNI request path were removed.
The setting that enables Hero Appearance remains under Restoration Project options.

Verification:
- os/android/tools/test-commands.sh checks bindings, catalog, profile filtering,
  favorites, invalid/disabled config and the existing native regression suite.
- testDebugUnitTest exercises real Android views using Robolectric: favorites,
  tabs, speed, small-screen layout, focus retry and pause/key-release ordering.
- Native-rendered screenshots are written under app/build/reports/hud-preview.
- Real touch interaction on Honor MagicPad and companion actions still require
  device acceptance. Automated UI tests use a fake input bridge, not a running game.
