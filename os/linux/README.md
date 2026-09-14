# Wasteland Collection for Omarchy

An English game library for Fallout 2 with Restoration Project Updated, Sonora
including Dayglow, and Nevada. This Linux x86_64 build includes the alpha.7 engine
corrections and the Hero Appearance character-creation panel. Object scripts left without
an owner in Nevada maps and older saves are skipped before initialization.

## Install

Copy the private .run installer to your Omarchy computer and run:

    bash Wasteland-Collection-1.4.0-alpha.7-linux.2-Omarchy-x86_64.run

The installer checks the embedded files and adds Wasteland Collection to the app
menu. Press Super + Space and search for Wasteland Collection. Missing Arch
packages are listed and installed only if you agree at the installer prompt.
The game itself runs natively; Wine and Steam are not required.

The default installation is ~/.local/share/wasteland-collection.
Use --destination /your/linux/path to choose another location, or --no-launch
to install without opening the library. --extract-only verifies/extracts a
package without installing a menu entry or checking desktop dependencies.

The installer needs about 4 GB plus space to stage an update. Preparing all three
games adds about 4.3 GB. Allow at least 9 GB for a fresh installation and all games,
plus room for your saves. Keep the downloaded installer elsewhere if space is tight.

## Play and settings

Choose a game and select Prepare & play. First launch verifies and copies that
game's files. The resulting profile is independent of the other two games.

Settings includes the engine's 97 fields, grouped by category, and named RPU
options. Choose a section, make changes and Save changes before switching to
another section. Display scaling must leave at least 640 x 480 logical pixels.
New profiles start at 1024 x 768 in a window. Linux uses SDL's available video
backend; no Omarchy/Hyprland configuration is modified.

Game folder opens the selected profile. Back up saves creates a ZIP under
backups/. This build does not offer an automatic ZIP restore; retain the archive
and copy saves into the selected profile's data/SAVEGAME (capitalization may vary) while the game is closed.
Normal keyboard commands and the engine's help remain available. The Android
touch HUD is not part of this desktop launcher.

## Updates and data

profiles/rpu, profiles/sonora and profiles/nevada contain separate settings and
saves. Updates replace the selected engine release. Preparing a content update
checks changed managed files, stages all replacements, preserves existing INI/CFG
files and resumes after interruption. Unexpected user edits to managed game assets
stop preparation. Old engine releases remain available; saves are outside them.

A successful preparation marker avoids re-reading gigabytes at every launch.
It is not a full integrity scan on every start. The supplied launcher, installer
and content checks are local; private game data is never uploaded to source CI.

## Test-build limits

RPU, Sonora and Nevada retain the compatibility limits documented in the Android
alpha.2-alpha.4 test reports. Full campaigns, all EPA paths, optional party control,
and every outfit/weapon/animation combination are not certified. A real Omarchy
machine still needs a display, audio, keyboard and save/load acceptance test.

## References

- Omarchy app menu: https://learn.omacom.io/2/the-omarchy-manual
- Native GTK on Arch: https://pygobject.gnome.org/getting_started.html
- Arch SDL2 compatibility package: https://archlinux.org/packages/extra/x86_64/sdl2-compat/
- Desktop entries: https://specifications.freedesktop.org/desktop-entry/latest/
